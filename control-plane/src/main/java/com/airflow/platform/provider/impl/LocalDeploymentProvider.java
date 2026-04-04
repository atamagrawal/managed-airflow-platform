package com.airflow.platform.provider.impl;

import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.provider.DeploymentProvider;
import com.airflow.platform.service.DockerComposeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Local implementation of DeploymentProvider
 * Manages Airflow deployments using Docker Compose on localhost
 */
@Component
@ConditionalOnProperty(name = "deployment.provider", havingValue = "local")
@RequiredArgsConstructor
@Slf4j
public class LocalDeploymentProvider implements DeploymentProvider {

    private static final String BUILD_INPUTS_STAMP = ".managed-airflow-build-inputs.sha256";

    /** Multi-line stamp; first line must match for {@link #readBuildInputsStamp(Path)}. */
    private static final String BUILD_INPUTS_STAMP_HEADER = "# map-build-inputs-v2";

    private static volatile List<String> composeBaseCommand;

    private final DockerComposeGenerator composeGenerator;

    @Value("${local.base-directory:${user.home}/airflow-deployments}")
    private String baseDirectory;

    @Value("${local.docker-compose-timeout:300}")
    private int timeout;

    @Value("${local.airflow-handoff.restart-apiserver-on-settings-change:true}")
    private boolean restartApiserverOnLocalSettingsChange;

    @Override
    public void deploy(AirflowDeployment deployment) {
        log.info("Deploying Airflow locally: {}", deployment.getDeploymentId());
        try {
            provisionComposeArtifactsOnly(deployment);
            startComposeStack(deployment);
            log.info("Airflow deployed successfully: {}", deployment.getDeploymentId());
        } catch (Exception e) {
            log.error("Failed to deploy Airflow locally: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Local deployment failed: " + e.getMessage(), e);
        }
    }

    /**
     * Writes {@code docker-compose.yml} and {@code config/airflow_local_settings.py} without starting containers.
     */
    public void provisionComposeArtifactsOnly(AirflowDeployment deployment) {
        try {
            String deploymentDir = getDeploymentDirectory(deployment);
            Path deploymentPath = Paths.get(deploymentDir);
            if (!Files.exists(deploymentPath)) {
                Files.createDirectories(deploymentPath);
            }
            String dockerCompose = composeGenerator.generateDockerCompose(deployment);
            Path composePath = deploymentPath.resolve("docker-compose.yml");
            Files.writeString(composePath, dockerCompose);
            persistAirflowLocalSettingsPy(deploymentPath);
            log.info("Docker Compose file generated (not started): {}", composePath);
        } catch (Exception e) {
            log.error("Failed to provision compose artifacts: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Local compose provisioning failed: " + e.getMessage(), e);
        }
    }

    /**
     * {@code docker compose build} then {@code up -d}. Always regenerates compose first so {@code build:} vs
     * {@code image:} matches the current {@code Dockerfile} on disk (e.g. after syncing a project Dockerfile).
     */
    public void startComposeStack(AirflowDeployment deployment) {
        try {
            String deploymentDir = getDeploymentDirectory(deployment);
            Path deploymentPath = Paths.get(deploymentDir);
            if (!Files.exists(deploymentPath)) {
                Files.createDirectories(deploymentPath);
            }
            provisionComposeArtifactsOnly(deployment);
            executeDockerCompose(deploymentDir, "build");
            executeDockerCompose(deploymentDir, "up", "-d");
            persistBuildInputsStampSafe(deployment, deploymentPath);
            log.info("Local compose stack started: {}", deployment.getDeploymentId());
        } catch (Exception e) {
            log.error("Failed to start local compose stack: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Local stack start failed: " + e.getMessage(), e);
        }
    }

    /**
     * Stops containers without removing volumes (unlike {@link #uninstall}).
     */
    public void stopComposeStack(AirflowDeployment deployment) {
        try {
            String deploymentDir = getDeploymentDirectory(deployment);
            if (!Files.exists(Paths.get(deploymentDir, "docker-compose.yml"))) {
                log.debug("No compose file for {}; nothing to stop", deployment.getDeploymentId());
                return;
            }
            executeDockerCompose(deploymentDir, "down");
            log.info("Local compose stack stopped: {}", deployment.getDeploymentId());
        } catch (Exception e) {
            log.error("Failed to stop local compose stack: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Local stack stop failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void upgrade(AirflowDeployment deployment) {
        log.info("Upgrading Airflow deployment: {}", deployment.getDeploymentId());

        try {
            String deploymentDir = getDeploymentDirectory(deployment);
            Path deploymentPath = Paths.get(deploymentDir);

            // Regenerate docker-compose.yml with updated configuration
            String dockerCompose = composeGenerator.generateDockerCompose(deployment);
            Path composePath = deploymentPath.resolve("docker-compose.yml");
            Files.writeString(composePath, dockerCompose);
            persistAirflowLocalSettingsPy(deploymentPath);

            // Stop and remove existing containers
            executeDockerCompose(deploymentDir, "down");

            // Rebuild once, then start containers.
            executeDockerCompose(deploymentDir, "build");
            executeDockerCompose(deploymentDir, "up", "-d");

            persistBuildInputsStampSafe(deployment, deploymentPath);
            log.info("Airflow upgraded successfully: {}", deployment.getDeploymentId());
        } catch (Exception e) {
            log.error("Failed to upgrade Airflow: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Local upgrade failed: " + e.getMessage(), e);
        }
    }

    /**
     * Keeps compose in sync and rebuilds images only when Dockerfile / requirements / packages or core
     * deployment settings change. DAGs and plugins are bind-mounted, so typical project deploys skip
     * {@link #upgrade}'s {@code down} + full rebuild, and skip {@code docker compose up -d} when neither
     * compose.yml nor build inputs changed (avoids re-running one-shot services like {@code airflow-init}).
     */
    @Override
    public void syncAfterProjectDeploy(AirflowDeployment deployment) {
        log.info("Syncing local stack after project deploy: {}", deployment.getDeploymentId());

        try {
            String deploymentDir = getDeploymentDirectory(deployment);
            Path deploymentPath = Paths.get(deploymentDir);
            Path composePath = deploymentPath.resolve("docker-compose.yml");
            if (!Files.exists(composePath)) {
                log.warn("No docker-compose.yml at {}; deploy Airflow before deploying projects", deploymentDir);
                return;
            }

            String newCompose = composeGenerator.generateDockerCompose(deployment);
            String oldCompose = Files.readString(composePath, StandardCharsets.UTF_8);
            boolean composeChanged = !oldCompose.equals(newCompose);
            if (composeChanged) {
                Files.writeString(composePath, newCompose, StandardCharsets.UTF_8);
            }
            boolean handoffSettingsChanged = persistAirflowLocalSettingsPy(deploymentPath);

            Path stampPath = deploymentPath.resolve(BUILD_INPUTS_STAMP);
            BuildInputsSnapshot current = BuildInputsSnapshot.capture(deployment, deploymentPath);
            String combinedFingerprint = current.combinedFingerprint(deploymentPath).toLowerCase();
            Optional<BuildInputsSnapshot> previousSnap = readBuildInputsStamp(stampPath);
            String legacyCombined = readLegacyCombinedStamp(stampPath, previousSnap);

            boolean needsBuild = previousSnap
                    .map(prev -> !prev.equals(current))
                    .orElseGet(() -> !combinedFingerprint.equals(legacyCombined));

            if (needsBuild) {
                if (previousSnap.isPresent()) {
                    logBuildInputChanges(previousSnap.get(), current, deployment.getDeploymentId());
                } else if (legacyCombined.isEmpty()) {
                    log.info("Compose build for {}: no build-input stamp (upgrade from older control-plane, stamp deleted, "
                            + "or deploy path skipped persist); establishing stamp with compose build",
                            deployment.getDeploymentId());
                    log.info("Compose build inputs for {}: {}", deployment.getDeploymentId(), current.describe());
                } else if (!combinedFingerprint.equals(legacyCombined)) {
                    log.info("Compose build for {}: build inputs changed (legacy single-hash stamp; no per-field diff). "
                            + "previousCombinedDigest={} currentCombinedDigest={}",
                            deployment.getDeploymentId(), legacyCombined, combinedFingerprint);
                }
                executeDockerCompose(deploymentDir, "build");
                writeBuildInputsStamp(stampPath, current);
            } else {
                log.info("Build inputs unchanged for {}; bind-mounted files are visible without rebuild",
                        deployment.getDeploymentId());
                if (previousSnap.isEmpty() && !legacyCombined.isEmpty()
                        && combinedFingerprint.equals(legacyCombined)) {
                    writeBuildInputsStamp(stampPath, current);
                }
            }

            if (needsBuild || composeChanged) {
                log.info("Applying docker compose up (Airflow services only) for {} (composeChanged={}, needsBuild={}); "
                        + "postgres/redis are unchanged by project requirements.txt",
                        deployment.getDeploymentId(), composeChanged, needsBuild);
                composeUpAirflowRuntimeServicesOnly(deploymentDir, deployment);
            } else {
                log.info("Skipping docker compose up for {}; compose file and build inputs unchanged "
                        + "(bind-mounted DAGs/plugins are already visible to running containers)",
                        deployment.getDeploymentId());
            }
            if (handoffSettingsChanged) {
                restartAirflowApiserverIfRunning(deploymentDir, deployment.getDeploymentId());
            }
        } catch (Exception e) {
            log.error("Failed to sync local stack after project deploy: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Local project sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * FAB {@code /auth/token} lives in a mounted sub-app without the main API CORS stack; this file patches CORS at
     * startup. Regenerated whenever compose is written so control-plane upgrades pick up template fixes.
     *
     * @return {@code true} if the file was created or its contents changed (running apiserver should be restarted)
     */
    private boolean persistAirflowLocalSettingsPy(Path deploymentPath) {
        try {
            Path configDir = deploymentPath.resolve("config");
            Files.createDirectories(configDir);
            Path f = configDir.resolve("airflow_local_settings.py");
            String content = composeGenerator.generateAirflowLocalSettingsPy();
            if (Files.exists(f)) {
                String existing = Files.readString(f, StandardCharsets.UTF_8);
                if (existing.equals(content)) {
                    return false;
                }
            }
            Files.writeString(f, content, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            log.warn("Could not write {}/config/airflow_local_settings.py (Open Airflow handoff may fail until fixed): {}",
                    deploymentPath, e.getMessage());
            return false;
        }
    }

    /**
     * Writes {@code config/airflow_local_settings.py} if this deployment has a compose directory. Use on control-plane
     * startup so stacks created before the handoff patch (or before a template upgrade) still get the file without a
     * manual project sync.
     */
    public void ensureAirflowHandoffConfig(AirflowDeployment deployment) {
        try {
            Path deploymentPath = Paths.get(getDeploymentDirectory(deployment));
            if (!Files.exists(deploymentPath.resolve("docker-compose.yml"))) {
                return;
            }
            if (persistAirflowLocalSettingsPy(deploymentPath)) {
                restartAirflowApiserverIfRunning(getDeploymentDirectory(deployment), deployment.getDeploymentId());
            }
        } catch (Exception e) {
            log.warn("Could not ensure airflow_local_settings.py for {}: {}", deployment.getDeploymentId(), e.getMessage());
        }
    }

    /**
     * Loads updated {@code airflow_local_settings.py} into a long-lived apiserver process. Only runs when the service
     * is already up; skipped when {@code local.airflow-handoff.restart-apiserver-on-settings-change} is false.
     */
    private void restartAirflowApiserverIfRunning(String deploymentDir, String deploymentId) {
        if (!restartApiserverOnLocalSettingsChange) {
            log.debug("Skipping airflow-apiserver restart after local settings change (local.airflow-handoff.restart-apiserver-on-settings-change=false)");
            return;
        }
        try {
            if (!isApiserverComposeServiceRunning(deploymentDir)) {
                return;
            }
            log.info("Restarting airflow-apiserver for deployment {} so config/airflow_local_settings.py is loaded",
                    deploymentId);
            executeDockerCompose(deploymentDir, "restart", "airflow-apiserver");
        } catch (Exception e) {
            log.warn("Could not restart airflow-apiserver for {} after updating airflow_local_settings.py: {}",
                    deploymentId, e.getMessage());
        }
    }

    private boolean isApiserverComposeServiceRunning(String deploymentDir) throws IOException, InterruptedException {
        String output = executeDockerCompose(deploymentDir, "ps", "--services", "--filter", "status=running");
        return output != null && output.toLowerCase().contains("apiserver");
    }

    /**
     * Records what the stack was last built from so {@link #syncAfterProjectDeploy} can skip {@code compose build}
     * when project sync does not change those inputs.
     */
    private void persistBuildInputsStampSafe(AirflowDeployment deployment, Path deploymentPath) {
        try {
            BuildInputsSnapshot snap = BuildInputsSnapshot.capture(deployment, deploymentPath);
            writeBuildInputsStamp(deploymentPath.resolve(BUILD_INPUTS_STAMP), snap);
        } catch (Exception e) {
            log.warn("Could not persist build-input stamp for {} (next project deploy may run an extra compose build): {}",
                    deployment.getDeploymentId(), e.getMessage());
        }
    }

    private static Optional<BuildInputsSnapshot> readBuildInputsStamp(Path stampPath) throws IOException {
        if (!Files.exists(stampPath)) {
            return Optional.empty();
        }
        List<String> lines = Files.readAllLines(stampPath, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).trim().equals(BUILD_INPUTS_STAMP_HEADER)) {
            return Optional.empty();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return Optional.of(new BuildInputsSnapshot(
                map.getOrDefault("airflowVersion", ""),
                map.getOrDefault("executor", ""),
                map.getOrDefault("Dockerfile", "absent"),
                map.getOrDefault("requirements.txt", "absent"),
                map.getOrDefault("packages.txt", "absent")
        ));
    }

    /**
     * If file is legacy format (single hex line), returns that digest; otherwise empty string.
     */
    private static String readLegacyCombinedStamp(Path stampPath, Optional<BuildInputsSnapshot> parsedV2)
            throws IOException {
        if (parsedV2.isPresent() || !Files.exists(stampPath)) {
            return "";
        }
        String raw = Files.readString(stampPath, StandardCharsets.UTF_8).trim();
        if (raw.contains("\n") || raw.contains("\r")) {
            return "";
        }
        if (raw.length() == 64 && raw.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            return raw.toLowerCase();
        }
        return "";
    }

    private static void writeBuildInputsStamp(Path stampPath, BuildInputsSnapshot snap) throws IOException {
        String body = BUILD_INPUTS_STAMP_HEADER + "\n"
                + "airflowVersion=" + snap.airflowVersion() + "\n"
                + "executor=" + snap.executor() + "\n"
                + "Dockerfile=" + snap.dockerfileDigest() + "\n"
                + "requirements.txt=" + snap.requirementsDigest() + "\n"
                + "packages.txt=" + snap.packagesDigest() + "\n";
        Files.writeString(stampPath, body, StandardCharsets.UTF_8);
    }

    private static void logBuildInputChanges(BuildInputsSnapshot previous, BuildInputsSnapshot current, String deploymentId) {
        List<String> changes = new ArrayList<>();
        if (!previous.airflowVersion().equals(current.airflowVersion())) {
            changes.add("airflowVersion: '%s' -> '%s'".formatted(previous.airflowVersion(), current.airflowVersion()));
        }
        if (!previous.executor().equals(current.executor())) {
            changes.add("executor: '%s' -> '%s'".formatted(previous.executor(), current.executor()));
        }
        appendFileDigestChange(changes, "Dockerfile", previous.dockerfileDigest(), current.dockerfileDigest());
        appendFileDigestChange(changes, "requirements.txt", previous.requirementsDigest(), current.requirementsDigest());
        appendFileDigestChange(changes, "packages.txt", previous.packagesDigest(), current.packagesDigest());
        if (changes.isEmpty()) {
            log.info("Compose build for {}: stamp mismatch without field-level diff (unexpected); rebuilding", deploymentId);
            return;
        }
        log.info("Compose build for {}: changed build inputs - {}", deploymentId, String.join("; ", changes));
    }

    private static void appendFileDigestChange(List<String> changes, String label, String prev, String cur) {
        if (prev.equals(cur)) {
            return;
        }
        if ("absent".equals(prev)) {
            changes.add(label + ": added (digest " + cur + ")");
        } else if ("absent".equals(cur)) {
            changes.add(label + ": removed (was " + prev + ")");
        } else {
            changes.add(label + ": content changed (" + prev + " -> " + cur + ")");
        }
    }

    private record BuildInputsSnapshot(
            String airflowVersion,
            String executor,
            String dockerfileDigest,
            String requirementsDigest,
            String packagesDigest
    ) {
        static BuildInputsSnapshot capture(AirflowDeployment deployment, Path deploymentRoot)
                throws IOException, NoSuchAlgorithmException {
            String version = deployment.getAirflowVersion() != null ? deployment.getAirflowVersion() : "";
            String ex = String.valueOf(deployment.getExecutorType());
            return new BuildInputsSnapshot(
                    version,
                    ex,
                    sha256FileOrAbsent(deploymentRoot.resolve("Dockerfile")),
                    sha256FileOrAbsent(deploymentRoot.resolve("requirements.txt")),
                    sha256FileOrAbsent(deploymentRoot.resolve("packages.txt"))
            );
        }

        /**
         * Same digest as pre–v2 stamps: version, executor, then raw bytes of each optional build file.
         */
        String combinedFingerprint(Path deploymentRoot) throws IOException, NoSuchAlgorithmException {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(airflowVersion.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(executor.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            for (String name : List.of("Dockerfile", "requirements.txt", "packages.txt")) {
                Path p = deploymentRoot.resolve(name);
                md.update(name.getBytes(StandardCharsets.UTF_8));
                if (Files.exists(p)) {
                    md.update(Files.readAllBytes(p));
                }
                md.update((byte) 0);
            }
            return HexFormat.of().formatHex(md.digest());
        }

        private static String sha256FileOrAbsent(Path path) throws IOException, NoSuchAlgorithmException {
            if (!Files.exists(path)) {
                return "absent";
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            return HexFormat.of().formatHex(digest);
        }

        String describe() {
            return "airflowVersion=%s, executor=%s, Dockerfile=%s, requirements.txt=%s, packages.txt=%s".formatted(
                    airflowVersion,
                    executor,
                    shortenDigest(dockerfileDigest),
                    shortenDigest(requirementsDigest),
                    shortenDigest(packagesDigest)
            );
        }

        private static String shortenDigest(String digest) {
            if ("absent".equals(digest) || digest.length() <= 12) {
                return digest;
            }
            return digest.substring(0, 12);
        }
    }

    @Override
    public void uninstall(AirflowDeployment deployment) {
        log.info("Uninstalling Airflow locally: {}", deployment.getDeploymentId());

        try {
            String deploymentDir = getDeploymentDirectory(deployment);

            if (Files.exists(Paths.get(deploymentDir))) {
                // Stop and remove containers
                executeDockerCompose(deploymentDir, "down", "-v");

                // Delete deployment directory
                deleteDirectory(new File(deploymentDir));

                log.info("Airflow uninstalled successfully: {}", deployment.getDeploymentId());
            } else {
                log.warn("Deployment directory not found: {}", deploymentDir);
            }
        } catch (Exception e) {
            log.error("Failed to uninstall Airflow: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Local uninstall failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDeploymentStatus(AirflowDeployment deployment) {
        try {
            String deploymentDir = getDeploymentDirectory(deployment);

            if (!Files.exists(Paths.get(deploymentDir, "docker-compose.yml"))) {
                return "NOT_FOUND";
            }

            // Check if containers are running
            String output = executeDockerCompose(deploymentDir, "ps", "--services", "--filter", "status=running");

            if (output != null && output.toLowerCase().contains("apiserver")) {
                return "RUNNING";
            } else if (output != null && !output.trim().isEmpty()) {
                return "STARTING";
            } else {
                return "STOPPED";
            }
        } catch (Exception e) {
            log.error("Failed to get deployment status: {}", deployment.getDeploymentId(), e);
            return "ERROR";
        }
    }

    @Override
    public String getWebserverUrl(AirflowDeployment deployment) {
        int port = getApiserverHostPort(deployment);
        // Use IPv4 loopback so JVM clients match Docker-published ports (see AirflowApiUrlUtils).
        return "http://127.0.0.1:" + port;
    }

    @Override
    public void scale(AirflowDeployment deployment, int minWorkers, int maxWorkers) {
        log.info("Scaling deployment {} to {} workers", deployment.getDeploymentId(), minWorkers);

        AirflowDeployment.ExecutorType ex = deployment.getExecutorType();
        if (ex != AirflowDeployment.ExecutorType.CELERY && ex != AirflowDeployment.ExecutorType.CELERY_KUBERNETES) {
            log.info("Skipping scale: executor {} does not use Celery workers", ex);
            return;
        }

        try {
            String deploymentDir = getDeploymentDirectory(deployment);
            executeDockerCompose(deploymentDir, "up", "-d", "--scale", "airflow-worker=" + minWorkers);

            log.info("Scaled successfully to {} workers", minWorkers);
        } catch (Exception e) {
            log.error("Failed to scale deployment: {}", deployment.getDeploymentId(), e);
            throw new DeploymentException("Local scaling failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderType() {
        return "local";
    }

    /**
     * Ensures a FAB user exists on this deployment with the same username/password as the platform account.
     * Safe to call when the stack is running; no-op if compose is missing.
     * <p>
     * Does not run {@code airflow users delete} first — each CLI invocation cold-loads Airflow and can take minutes;
     * {@code users create} plus {@code reset-password} on failure is enough for idempotency.
     */
    public void ensureFabUserMatchesPlatform(AirflowDeployment deployment, String username, String plainPassword,
                                             boolean platformAdmin) {
        if (username == null || username.isBlank() || plainPassword == null) {
            return;
        }
        String fabRole = platformAdmin ? "Admin" : "User";
        String deploymentDir = getDeploymentDirectory(deployment);
        if (!Files.exists(Paths.get(deploymentDir, "docker-compose.yml"))) {
            log.debug("Skipping FAB user sync for {}: no compose file", deployment.getDeploymentId());
            return;
        }
        try {
            composeExec(deploymentDir, "airflow-apiserver", false, buildExecArgs(
                    "airflow", "users", "create",
                    "-u", username.trim(),
                    "-f", username.trim(),
                    "-l", "User",
                    "-r", fabRole,
                    "-e", username.trim() + "@managed-airflow.local",
                    "-p", plainPassword));
            log.info("Synced platform user '{}' to Airflow FAB on deployment {}", username, deployment.getDeploymentId());
        } catch (DeploymentException e) {
            log.info("FAB user create failed for {} on {}, trying reset-password: {}", username,
                    deployment.getDeploymentId(), e.getMessage());
            try {
                composeExec(deploymentDir, "airflow-apiserver", false, buildExecArgs(
                        "airflow", "users", "reset-password", "-u", username.trim(), "-p", plainPassword));
                log.info("Reset Airflow FAB password for '{}' on deployment {}", username, deployment.getDeploymentId());
            } catch (Exception e2) {
                log.warn("Could not sync user {} to deployment {}: {}", username, deployment.getDeploymentId(),
                        e2.getMessage());
            }
        } catch (Exception e) {
            log.warn("Could not sync user {} to deployment {}: {}", username, deployment.getDeploymentId(), e.getMessage());
        }
    }

    private static String[] buildExecArgs(String... args) {
        return args;
    }

    /**
     * {@code docker compose exec -T <service> <args...>}.
     *
     * @param ignoreNonZeroExit when true, a non-zero process exit does not throw (still logged).
     */
    public String composeExec(String deploymentDir, String composeService, boolean ignoreNonZeroExit, String... execArgs)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(resolveComposeBaseCommand());
        command.add("exec");
        command.add("-T");
        command.add(composeService);
        command.addAll(Arrays.asList(execArgs));

        log.info("Compose exec in {}: {} {}", deploymentDir, composeService, String.join(" ", execArgs));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(deploymentDir));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug(line);
                output.append(line).append("\n");
            }
        }

        boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new DeploymentException("Docker compose exec timed out after " + timeout + " seconds");
        }
        int exitCode = process.exitValue();
        if (exitCode != 0 && !ignoreNonZeroExit) {
            throw new DeploymentException("Docker compose exec failed with exit code " + exitCode + ". Output:\n"
                    + trimOutput(output.toString()));
        }
        if (exitCode != 0) {
            log.debug("Compose exec non-zero exit {} (ignored): {}", exitCode, trimOutput(output.toString()));
        }
        return output.toString();
    }

    private String getDeploymentDirectory(AirflowDeployment deployment) {
        return baseDirectory + File.separator +
               deployment.getTenant().getTenantId() + File.separator +
               deployment.getDeploymentId();
    }

    /** Host port for airflow-apiserver; must match {@link DockerComposeGenerator} local range. */
    private int getApiserverHostPort(AirflowDeployment deployment) {
        int hash = Math.abs(deployment.getDeploymentId().hashCode());
        return 8090 + (hash % 100);
    }

    private List<String> resolveComposeBaseCommand() throws IOException, InterruptedException {
        List<String> cached = composeBaseCommand;
        if (cached != null) {
            return cached;
        }
        synchronized (LocalDeploymentProvider.class) {
            if (composeBaseCommand != null) {
                return composeBaseCommand;
            }
            if (composeCliWorks("docker", "compose", "version")) {
                composeBaseCommand = List.of("docker", "compose");
            } else if (composeCliWorks("docker-compose", "version")) {
                composeBaseCommand = List.of("docker-compose");
            } else {
                throw new DeploymentException("Neither `docker compose` nor `docker-compose` is available on PATH");
            }
            return composeBaseCommand;
        }
    }

    private boolean composeCliWorks(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process p = pb.start();
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Runs {@code up -d} only for services that use the Airflow image (from Dockerfile / requirements).
     * {@code postgres} and {@code redis} use fixed upstream images and are not derived from project
     * {@code requirements.txt}; a bare project-wide {@code up -d} can still reconcile every service and
     * cause unnecessary postgres/redis churn on some Compose versions.
     */
    private void composeUpAirflowRuntimeServicesOnly(String deploymentDir, AirflowDeployment deployment)
            throws IOException, InterruptedException {
        List<String> services = new ArrayList<>();
        services.add("airflow-init");
        services.add("airflow-apiserver");
        services.add("airflow-scheduler");
        services.add("airflow-dag-processor");
        if (executorNeedsRedis(deployment)) {
            services.add("airflow-worker");
            services.add("airflow-flower");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("up");
        cmd.add("-d");
        cmd.addAll(services);
        executeDockerCompose(deploymentDir, cmd.toArray(new String[0]));
    }

    private static boolean executorNeedsRedis(AirflowDeployment deployment) {
        AirflowDeployment.ExecutorType ex = deployment.getExecutorType();
        return ex == AirflowDeployment.ExecutorType.CELERY
                || ex == AirflowDeployment.ExecutorType.CELERY_KUBERNETES;
    }

    private String executeDockerCompose(String workingDir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(resolveComposeBaseCommand());
        command.addAll(List.of(args));

        log.info("Executing command in {}: {}", workingDir, String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(workingDir));
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug(line);
                output.append(line).append("\n");
            }
        }

        boolean completed = process.waitFor(timeout, java.util.concurrent.TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new DeploymentException("Docker Compose command timed out after " + timeout +
                    " seconds. Last output:\n" + trimOutput(output.toString()));
        }

        int exitCode = process.exitValue();
        if (exitCode != 0 && !args[0].equals("down")) {
            throw new DeploymentException("Docker Compose command failed with exit code: " + exitCode +
                    ". Command: " + String.join(" ", command) +
                    ". Output:\n" + trimOutput(output.toString()));
        }

        return output.toString();
    }

    private String trimOutput(String output) {
        if (output == null || output.isBlank()) {
            return "[no output]";
        }
        int max = 5000;
        if (output.length() <= max) {
            return output;
        }
        return output.substring(output.length() - max);
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}
