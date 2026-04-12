package com.airflow.platform.service;

import com.airflow.platform.config.DefaultProjectTemplateProperties;
import com.airflow.platform.model.Project;
import com.airflow.platform.model.ProjectFile;
import com.airflow.platform.repository.ProjectFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Predicate;

/**
 * Seeds {@link ProjectFile} rows from the configured template ({@code project.default-template.active}).
 * Add templates by registering a root in YAML and placing files under {@code src/main/resources/...} (or a {@code file:} tree).
 * Optional {@code extra-requirements.txt} at the template root merges non-duplicate lines into the project's
 * {@code requirements.txt} (comment lines {@code #} and blanks skipped).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultProjectTemplateSeeder {

    private final DefaultProjectTemplateProperties properties;
    private final ProjectFileRepository projectFileRepository;

    private static final List<SubdirRule> SUBDIR_RULES = List.of(
            new SubdirRule("dags", "dags/**", p -> p.endsWith(".py")),
            new SubdirRule("contracts", "contracts/**", p -> endsWithAny(p, ".yml", ".yaml")),
            new SubdirRule("plugins", "plugins/**", p -> true),
            new SubdirRule("include", "include/**", p -> true),
            new SubdirRule("tests", "tests/**", p -> true)
    );

    /**
     * Loads template files, persists them, and sets {@link Project#setDagCount}.
     */
    public void seed(Project project) throws IOException {
        if (!properties.isEnabled()) {
            log.info("Default project template disabled; skipping seed for project {}", project.getProjectId());
            project.setDagCount(0);
            return;
        }

        String root = properties.resolveLocation(null);
        String rootFolderName = templateRootFolderName(root);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        mergeExtraRequirements(project, resolver, root);
        applyDefaultAirflowSettings(project, resolver, root);

        Properties descriptions = loadDescriptions(resolver, root);
        Map<String, Resource> unique = new LinkedHashMap<>();
        for (SubdirRule rule : SUBDIR_RULES) {
            Resource[] batch;
            try {
                batch = resolver.getResources(root + rule.glob);
            } catch (IOException e) {
                // Missing empty dirs (e.g. plugins/) are not packaged on the classpath — skip.
                log.debug("No template tree for {} [{}]: {}", rule.prefix, rule.glob, e.getMessage());
                continue;
            }
            for (Resource resource : batch) {
                if (!resource.isReadable()) {
                    continue;
                }
                String relative = pathUnderTemplateRoot(resource, rootFolderName);
                if (relative == null || !relative.startsWith(rule.prefix + "/")) {
                    continue;
                }
                if (!rule.filter.test(relative)) {
                    continue;
                }
                unique.putIfAbsent(relative, resource);
            }
        }

        if (unique.isEmpty()) {
            log.warn("No dags/contracts/plugins template files under {} — check project.default-template.templates",
                    root);
            project.setDagCount(0);
            return;
        }

        int dagFiles = 0;
        for (Map.Entry<String, Resource> e : unique.entrySet()) {
            String relativePath = e.getKey();
            Resource resource = e.getValue();
            ProjectFile.FileType type = fileTypeFor(relativePath);
            if (type == ProjectFile.FileType.OTHER) {
                continue;
            }
            String content = readAndSubstitute(resource, project);
            ProjectFile entity = new ProjectFile();
            entity.setProject(project);
            entity.setFilePath(relativePath);
            entity.setFileName(fileName(relativePath));
            entity.setFileType(type);
            entity.setDescription(descriptions.getProperty(relativePath, ""));
            entity.setContent(content);
            entity.setFileSize((long) content.getBytes(StandardCharsets.UTF_8).length);
            projectFileRepository.save(entity);
            if (type == ProjectFile.FileType.DAG) {
                dagFiles++;
            }
        }
        project.setDagCount(dagFiles);
        log.info("Seeded {} template file(s) ({} DAG(s)) for project {} from {}",
                unique.size(), dagFiles, project.getProjectId(), root);
    }

    /**
     * Copies {@code airflow_settings.yaml} from the template into the project when the API did not supply settings.
     * Documents the local dummy data-contract connection (no external catalog).
     */
    private void applyDefaultAirflowSettings(
            Project project, PathMatchingResourcePatternResolver resolver, String root) throws IOException {
        if (StringUtils.hasText(project.getAirflowSettingsYaml())) {
            return;
        }
        Resource r = resolver.getResource(root + "airflow_settings.yaml");
        if (!r.exists()) {
            return;
        }
        try (InputStream in = r.getInputStream()) {
            String yaml = applyPlaceholders(StreamUtils.copyToString(in, StandardCharsets.UTF_8), project);
            project.setAirflowSettingsYaml(yaml);
                log.info("Applied template airflow_settings.yaml to project {}", project.getProjectId());
        }
    }

    /**
     * If the template contains {@code extra-requirements.txt}, append any lines not already present in the project
     * requirements (simple substring check per line).
     */
    private void mergeExtraRequirements(Project project, PathMatchingResourcePatternResolver resolver, String root)
            throws IOException {
        Resource r = resolver.getResource(root + "extra-requirements.txt");
        if (!r.exists()) {
            return;
        }
        String extra;
        try (InputStream in = r.getInputStream()) {
            extra = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
        String current = project.getRequirementsTxt() != null ? project.getRequirementsTxt() : "";
        String merged = current;
        boolean changed = false;
        for (String line : extra.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (merged.contains(t)) {
                continue;
            }
            if (!merged.isEmpty() && !merged.endsWith("\n")) {
                merged += "\n";
            }
            merged += t + "\n";
            changed = true;
        }
        if (changed) {
            project.setRequirementsTxt(merged.stripTrailing());
            log.info("Merged extra-requirements.txt from template into project {}", project.getProjectId());
        }
    }

    private static boolean endsWithAny(String path, String... suffixes) {
        String lower = path.toLowerCase(Locale.ROOT);
        for (String s : suffixes) {
            if (lower.endsWith(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Last path segment of the template root (e.g. {@code default-project} from {@code classpath:default-project/}).
     */
    static String templateRootFolderName(String normalizedRoot) {
        String t = Objects.requireNonNull(normalizedRoot, "normalizedRoot").trim();
        if (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        int lastSlash = t.lastIndexOf('/');
        if (lastSlash >= 0) {
            return t.substring(lastSlash + 1);
        }
        int colon = t.indexOf(':');
        if (colon >= 0 && colon < t.length() - 1) {
            return t.substring(colon + 1);
        }
        return t;
    }

    private Properties loadDescriptions(PathMatchingResourcePatternResolver resolver, String root) {
        Properties p = new Properties();
        try {
            Resource r = resolver.getResource(root + "file-descriptions.properties");
            if (r.exists()) {
                try (InputStream in = r.getInputStream();
                     Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    p.load(reader);
                }
            }
        } catch (IOException e) {
            log.warn("Could not load file-descriptions.properties from {}: {}", root, e.getMessage());
        }
        return p;
    }

    private String readAndSubstitute(Resource resource, Project project) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            String raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            return applyPlaceholders(raw, project);
        }
    }

    static String applyPlaceholders(String content, Project project) {
        String name = project.getName() != null ? project.getName() : "";
        return content
                .replace("${projectId}", project.getProjectId() != null ? project.getProjectId() : "")
                .replace("${projectName}", name);
    }

    private static ProjectFile.FileType fileTypeFor(String relativePath) {
        int slash = relativePath.indexOf('/');
        if (slash <= 0) {
            return ProjectFile.FileType.OTHER;
        }
        String top = relativePath.substring(0, slash);
        return switch (top) {
            case "dags" -> ProjectFile.FileType.DAG;
            case "contracts" -> ProjectFile.FileType.CONTRACT;
            case "plugins" -> ProjectFile.FileType.PLUGIN;
            case "include" -> ProjectFile.FileType.INCLUDE;
            case "tests" -> ProjectFile.FileType.TEST;
            default -> ProjectFile.FileType.OTHER;
        };
    }

    private static String fileName(String relativePath) {
        int i = relativePath.lastIndexOf('/');
        return i >= 0 ? relativePath.substring(i + 1) : relativePath;
    }

    /**
     * Returns path relative to the template root folder, e.g. {@code dags/foo.py}.
     */
    static String pathUnderTemplateRoot(Resource resource, String rootFolderName) {
        String marker = rootFolderName + "/";
        String path;
        try {
            path = resource.getURL().getPath();
        } catch (IOException e) {
            log.debug("Could not resolve URL for template resource {}: {}", resource, e.getMessage());
            return null;
        }
        int idx = path.indexOf(marker);
        if (idx >= 0) {
            return path.substring(idx + marker.length());
        }
        String desc = resource.getDescription();
        String prefix = "class path resource [";
        if (desc.startsWith(prefix) && desc.endsWith("]")) {
            String inner = desc.substring(prefix.length(), desc.length() - 1);
            int m = inner.indexOf(marker);
            if (m >= 0) {
                return inner.substring(m + marker.length());
            }
        }
        log.debug("Could not locate {} under {}; URL path was {}", marker, desc, path);
        return null;
    }

    private record SubdirRule(String prefix, String glob, Predicate<String> filter) {
    }
}
