package com.airflow.platform.service;

import com.airflow.platform.config.DagDeploymentConfig;
import com.airflow.platform.config.SupportedAirflowVersions;
import com.airflow.platform.util.AirflowApiUrlUtils;
import com.airflow.platform.dto.*;
import com.airflow.platform.exception.DeploymentException;
import com.airflow.platform.exception.ResourceNotFoundException;
import com.airflow.platform.model.AirflowDeployment;
import com.airflow.platform.model.Project;
import com.airflow.platform.model.ProjectDeployment;
import com.airflow.platform.model.ProjectFile;
import com.airflow.platform.model.Tenant;
import com.airflow.platform.security.SecurityUtils;
import com.airflow.platform.provider.DeploymentProvider;
import com.airflow.platform.repository.AirflowDeploymentRepository;
import com.airflow.platform.repository.ProjectDeploymentRepository;
import com.airflow.platform.repository.ProjectFileRepository;
import com.airflow.platform.repository.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for managing Airflow projects (Astronomer-style)
 * Supports project creation, file management, and deployment.
 * Projects are not owned by a single deployment; use {@link ProjectDeployment} to link a project to one or more deployments.
 */
@Service
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final AirflowDeploymentRepository deploymentRepository;
    private final TenantService tenantService;
    private final DagDeploymentConfig dagDeploymentConfig;
    private final DefaultProjectTemplateSeeder defaultProjectTemplateSeeder;
    private final RestTemplate restTemplate;

    @Autowired(required = false)
    private DeploymentProvider deploymentProviderImpl;

    @Value("${deployment.provider:local}")
    private String deploymentProvider;

    @Value("${local.base-directory:${user.home}/airflow-deployments}")
    private String localBaseDirectory;

    /**
     * When set (e.g. {@code deployment.compose.airflow-image} in application.yml), default project Dockerfiles use this
     * as the {@code FROM} image so they match the Airflow image used in generated docker-compose.
     */
    @Value("${deployment.compose.airflow-image:}")
    private String composeAirflowImage;

    @Value("${airflow.api.username:admin}")
    private String airflowApiUsername;

    @Value("${airflow.api.password:admin}")
    private String airflowApiPassword;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectFileRepository projectFileRepository,
                          ProjectDeploymentRepository projectDeploymentRepository,
                          AirflowDeploymentRepository deploymentRepository,
                          TenantService tenantService,
                          DagDeploymentConfig dagDeploymentConfig,
                          DefaultProjectTemplateSeeder defaultProjectTemplateSeeder,
                          RestTemplate restTemplate) {
        this.projectRepository = projectRepository;
        this.projectFileRepository = projectFileRepository;
        this.projectDeploymentRepository = projectDeploymentRepository;
        this.deploymentRepository = deploymentRepository;
        this.tenantService = tenantService;
        this.dagDeploymentConfig = dagDeploymentConfig;
        this.defaultProjectTemplateSeeder = defaultProjectTemplateSeeder;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public ProjectResponse createProject(ProjectCreateRequest request) {
        log.info("Creating project: {}", request.getName());

        SupportedAirflowVersions.requireSupported(request.getAirflowVersion());

        // Generate project ID
        String projectId = generateProjectId(request.getName());

        // Create project entity
        Project project = new Project();
        project.setProjectId(projectId);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setStatus(Project.ProjectStatus.DRAFT);
        project.setTenant(resolveTenantForCreate(request));
        project.setRequirementsTxt(request.getRequirementsTxt() != null ? request.getRequirementsTxt() : getDefaultRequirements());
        project.setPackagesTxt(request.getPackagesTxt());
        project.setDockerfile(StringUtils.hasText(request.getDockerfile())
                ? request.getDockerfile().trim()
                : getDefaultDockerfile(request.getAirflowVersion()));
        project.setAirflowSettingsYaml(request.getAirflowSettingsYaml());
        project.setAirflowIgnore(request.getAirflowIgnore() != null ? request.getAirflowIgnore() : getDefaultAirflowIgnore());
        project.setEnvFile(request.getEnvFile());
        project.setGitRepository(request.getGitRepository());
        project.setGitBranch(request.getGitBranch());
        project.setAirflowVersion(request.getAirflowVersion() != null ? request.getAirflowVersion() : "3.1.8");
        project.setOwner(request.getOwner());
        project.setTags(request.getTags());
        project.setDagCount(0);
        project.setPluginCount(0);

        Project savedProject = projectRepository.save(project);
        try {
            defaultProjectTemplateSeeder.seed(savedProject);
        } catch (IOException e) {
            throw new DeploymentException("Failed to seed default project files from template: " + e.getMessage(), e);
        }
        projectRepository.save(savedProject);
        log.info("Project created successfully: {}", savedProject.getProjectId());

        return toResponse(savedProject);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(String projectId) {
        return toResponse(requireAccessibleProject(projectId));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        if (SecurityUtils.isAdmin()) {
            return projectRepository.findAll().stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        }
        String scope = SecurityUtils.getNonAdminTenantScope()
                .orElseThrow(() -> new AccessDeniedException("Not authorized"));
        return projectRepository.findByTenant_TenantId(scope).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjectsByDeployment(String deploymentId) {
        AirflowDeployment deployment = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
        SecurityUtils.assertTenantInScope(deployment.getTenant().getTenantId());
        String tenantId = deployment.getTenant().getTenantId();
        return projectDeploymentRepository.findByDeployment_DeploymentId(deploymentId).stream()
                .map(ProjectDeployment::getProject)
                .distinct()
                .filter(p -> tenantId.equals(p.getTenant().getTenantId()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProjectResponse linkProjectToDeployment(String projectId, String deploymentId) {
        Project project = requireAccessibleProject(projectId);
        AirflowDeployment deployment = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
        SecurityUtils.assertTenantInScope(deployment.getTenant().getTenantId());
        assertProjectDeploymentSameTenant(project, deployment);
        if (projectDeploymentRepository.existsByProject_ProjectIdAndDeployment_DeploymentId(projectId, deploymentId)) {
            return toResponse(project);
        }
        ProjectDeployment link = new ProjectDeployment();
        link.setProject(project);
        link.setDeployment(deployment);
        projectDeploymentRepository.save(link);
        log.info("Linked project {} to deployment {}", projectId, deploymentId);
        return toResponse(projectRepository.findByProjectId(projectId).orElseThrow());
    }

    @Transactional
    public void unlinkProjectFromDeployment(String projectId, String deploymentId) {
        requireAccessibleProject(projectId);
        AirflowDeployment deployment = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
        SecurityUtils.assertTenantInScope(deployment.getTenant().getTenantId());
        projectDeploymentRepository.deleteByProject_ProjectIdAndDeployment_DeploymentId(projectId, deploymentId);
        log.info("Unlinked project {} from deployment {}", projectId, deploymentId);
    }

    private ProjectResponse toResponse(Project project) {
        List<String> ids = projectDeploymentRepository.findByProject_ProjectId(project.getProjectId()).stream()
                .map(pd -> pd.getDeployment().getDeploymentId())
                .sorted()
                .collect(Collectors.toList());
        return ProjectResponse.fromEntity(project, ids);
    }

    @Transactional
    public ProjectResponse updateProject(String projectId, ProjectUpdateRequest request) {
        log.info("Updating project: {}", projectId);

        Project project = requireAccessibleProject(projectId);

        if (request.getName() != null) {
            project.setName(request.getName());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getRequirementsTxt() != null) {
            project.setRequirementsTxt(request.getRequirementsTxt());
        }
        if (request.getPackagesTxt() != null) {
            project.setPackagesTxt(request.getPackagesTxt());
        }
        if (request.getDockerfile() != null) {
            project.setDockerfile(request.getDockerfile());
        }
        if (request.getAirflowSettingsYaml() != null) {
            project.setAirflowSettingsYaml(request.getAirflowSettingsYaml());
        }
        if (request.getAirflowIgnore() != null) {
            project.setAirflowIgnore(request.getAirflowIgnore());
        }
        if (request.getEnvFile() != null) {
            project.setEnvFile(request.getEnvFile());
        }
        if (request.getGitRepository() != null) {
            project.setGitRepository(request.getGitRepository());
        }
        if (request.getGitBranch() != null) {
            project.setGitBranch(request.getGitBranch());
        }
        if (request.getAirflowVersion() != null) {
            SupportedAirflowVersions.requireSupported(request.getAirflowVersion());
            project.setAirflowVersion(request.getAirflowVersion());
        }
        if (request.getOwner() != null) {
            project.setOwner(request.getOwner());
        }
        if (request.getTags() != null) {
            project.setTags(request.getTags());
        }

        project.setStatus(Project.ProjectStatus.UPDATING);
        Project updatedProject = projectRepository.save(project);
        log.info("Project updated successfully: {}", updatedProject.getProjectId());

        return toResponse(updatedProject);
    }

    @Transactional
    public void deleteProject(String projectId) {
        log.info("Deleting project: {}", projectId);

        Project project = requireAccessibleProject(projectId);

        // Delete all associated files
        projectFileRepository.deleteByProject(project);

        projectDeploymentRepository.deleteByProject_ProjectId(projectId);

        // Delete project
        projectRepository.delete(project);
        log.info("Project deleted successfully: {}", projectId);
    }

    @Transactional
    public void addFileToProject(String projectId, ProjectFileRequest request) {
        log.info("Adding file to project: {}, path: {}", projectId, request.getFilePath());

        Project project = requireAccessibleProject(projectId);

        // Check if file already exists
        if (projectFileRepository.findByProjectAndFilePath(project, request.getFilePath()).isPresent()) {
            throw new DeploymentException("File already exists at path: " + request.getFilePath());
        }

        ProjectFile file = new ProjectFile();
        file.setProject(project);
        file.setFilePath(request.getFilePath());
        file.setFileName(request.getFileName());
        file.setFileType(ProjectFile.FileType.valueOf(request.getFileType()));
        file.setContent(request.getContent());
        file.setDescription(request.getDescription());
        file.setFileSize((long) request.getContent().length());

        projectFileRepository.save(file);

        // Update counts
        if (file.getFileType() == ProjectFile.FileType.DAG) {
            project.setDagCount(project.getDagCount() + 1);
        } else if (file.getFileType() == ProjectFile.FileType.PLUGIN) {
            project.setPluginCount(project.getPluginCount() + 1);
        }
        projectRepository.save(project);

        log.info("File added to project: {}", request.getFilePath());
    }

    @Transactional
    public void updateProjectFile(String projectId, Long fileId, ProjectFileUpdateRequest request) {
        log.info("Updating file {} in project {}", fileId, projectId);

        requireAccessibleProject(projectId);

        ProjectFile file = projectFileRepository.findByIdAndProject_ProjectId(fileId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project file not found: " + fileId + " in project " + projectId));

        file.setContent(request.getContent());
        file.setFileSize((long) request.getContent().length());
        if (request.getDescription() != null) {
            file.setDescription(request.getDescription());
        }

        projectFileRepository.save(file);
        log.info("Updated project file: {} ({})", file.getFilePath(), fileId);
    }

    @Transactional
    public List<ProjectFile> getProjectFiles(String projectId) {
        Project project = requireAccessibleProject(projectId);
        return projectFileRepository.findByProject(project);
    }

    /**
     * Lists DAG-type project files for each project–deployment pair that has a successful deploy
     * ({@link ProjectDeployment#getLastDeployedAt()} set).
     */
    @Transactional(readOnly = true)
    public List<DeployedDagResponse> listDeployedProjectDags(String deploymentIdFilter) {
        List<ProjectDeployment> links;
        if (StringUtils.hasText(deploymentIdFilter)) {
            String depId = deploymentIdFilter.trim();
            AirflowDeployment dep = deploymentRepository.findByDeploymentId(depId)
                    .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + depId));
            SecurityUtils.assertTenantInScope(dep.getTenant().getTenantId());
            links = projectDeploymentRepository.findDeployedLinksWithProjectAndDeploymentByDeploymentId(depId);
        } else {
            links = projectDeploymentRepository.findDeployedLinksWithProjectAndDeployment();
            if (!SecurityUtils.isAdmin()) {
                String scope = SecurityUtils.getNonAdminTenantScope()
                        .orElseThrow(() -> new AccessDeniedException("Not authorized"));
                links = links.stream()
                        .filter(l -> scope.equals(l.getProject().getTenant().getTenantId())
                                && scope.equals(l.getDeployment().getTenant().getTenantId()))
                        .collect(Collectors.toList());
            }
        }
        links = links.stream()
                .filter(l -> l.getProject().getTenant().getTenantId()
                        .equals(l.getDeployment().getTenant().getTenantId()))
                .collect(Collectors.toList());

        List<DeployedDagResponse> rows = new ArrayList<>();
        for (ProjectDeployment link : links) {
            Project project = link.getProject();
            AirflowDeployment deployment = link.getDeployment();
            List<ProjectFile> dagFiles = projectFileRepository.findByProjectAndFileType(project, ProjectFile.FileType.DAG);
            for (ProjectFile f : dagFiles) {
                rows.add(DeployedDagResponse.builder()
                        .deploymentId(deployment.getDeploymentId())
                        .deploymentName(deployment.getName())
                        .projectId(project.getProjectId())
                        .projectName(project.getName())
                        .fileId(f.getId())
                        .filePath(f.getFilePath())
                        .fileName(f.getFileName())
                        .airflowDagId(extractAirflowDagId(f.getContent()))
                        .lastDeployedAt(link.getLastDeployedAt())
                        .build());
            }
        }
        rows.sort(Comparator
                .comparing(DeployedDagResponse::getDeploymentId, Comparator.nullsFirst(String::compareTo))
                .thenComparing(DeployedDagResponse::getProjectId, Comparator.nullsFirst(String::compareTo))
                .thenComparing(DeployedDagResponse::getFileName, Comparator.nullsFirst(String::compareTo)));
        return rows;
    }

    @Transactional
    public ProjectResponse deployProject(String projectId, String deploymentId) {
        log.info("Deploying project: {} to deployment: {}", projectId, deploymentId);

        Project project = requireAccessibleProject(projectId);
        AirflowDeployment deployment = deploymentRepository.findByDeploymentId(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
        SecurityUtils.assertTenantInScope(deployment.getTenant().getTenantId());
        assertProjectDeploymentSameTenant(project, deployment);

        ProjectDeployment link = projectDeploymentRepository
                .findByProject_ProjectIdAndDeployment_DeploymentId(projectId, deploymentId)
                .orElseGet(() -> {
                    ProjectDeployment n = new ProjectDeployment();
                    n.setProject(project);
                    n.setDeployment(deployment);
                    return projectDeploymentRepository.save(n);
                });

        project.setStatus(Project.ProjectStatus.DEPLOYING);
        projectRepository.save(project);

        try {
            if ("local".equalsIgnoreCase(deploymentProvider)) {
                deployProjectLocally(project, deployment);
                refreshLocalDeploymentRuntime(deployment);
            } else {
                throw new DeploymentException("Deployment provider not supported: " + deploymentProvider);
            }

            link.setLastDeployedAt(LocalDateTime.now());
            projectDeploymentRepository.save(link);
            project.setStatus(Project.ProjectStatus.DEPLOYED);
            project.setLastDeployedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Failed to deploy project: {}", projectId, e);
            project.setStatus(Project.ProjectStatus.FAILED);
            throw new DeploymentException("Failed to deploy project: " + e.getMessage(), e);
        } finally {
            projectRepository.save(project);
        }

        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> triggerProject(String projectId, String deploymentId, String fileName) {
        log.info("Triggering project DAG runs: {} on deployment {}", projectId, deploymentId);

        Project project = requireAccessibleProject(projectId);

        ProjectDeployment link = projectDeploymentRepository
                .findByProject_ProjectIdAndDeployment_DeploymentId(projectId, deploymentId)
                .orElseThrow(() -> new DeploymentException(
                        "Project is not linked to deployment " + deploymentId
                                + ". Link the project to this deployment or deploy to it first."));
        if (link.getLastDeployedAt() == null) {
            throw new DeploymentException(
                    "Project has not been successfully deployed to deployment " + deploymentId + " yet.");
        }

        AirflowDeployment deployment = link.getDeployment();
        SecurityUtils.assertTenantInScope(deployment.getTenant().getTenantId());
        if (!project.getTenant().getTenantId().equals(deployment.getTenant().getTenantId())) {
            throw new DeploymentException("Project and deployment must belong to the same tenant");
        }

        List<ProjectFile> dagFiles = projectFileRepository.findByProjectAndFileType(project, ProjectFile.FileType.DAG);
        if (dagFiles.isEmpty()) {
            throw new DeploymentException("No DAG files found in project: " + projectId);
        }

        if (fileName != null && !fileName.isBlank()) {
            dagFiles = dagFiles.stream()
                    .filter(file -> fileName.equals(file.getFileName()))
                    .collect(Collectors.toList());
            if (dagFiles.isEmpty()) {
                throw new DeploymentException("No DAG file named '" + fileName + "' found in project: " + projectId);
            }
        }

        String webserverUrl = deployment.getWebserverUrl();
        if (webserverUrl == null || webserverUrl.isBlank()) {
            throw new DeploymentException("Airflow webserver URL not found for deployment: " + deployment.getDeploymentId());
        }

        String baseUrl = AirflowApiUrlUtils.normalizeAirflowBaseUrl(webserverUrl);
        String airflowVersion = deployment.getAirflowVersion();
        log.info("Triggering project {} DAGs via {} (Airflow version: {})",
                projectId, baseUrl, airflowVersion);
        int successCount = 0;
        List<Map<String, Object>> results = new java.util.ArrayList<>();

        for (ProjectFile dagFile : dagFiles) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("fileName", dagFile.getFileName());
            entry.put("filePath", dagFile.getFilePath());

            String airflowDagId = extractAirflowDagId(dagFile.getContent());
            if (airflowDagId == null) {
                entry.put("success", false);
                entry.put("message", "Could not extract dag_id from DAG file content");
                results.add(entry);
                continue;
            }

            try {
                ResponseEntity<Map<String, Object>> response = postTriggerDagRun(baseUrl, airflowDagId, airflowVersion);
                entry.put("success", true);
                entry.put("airflowDagId", airflowDagId);
                entry.put("response", response.getBody());
                successCount++;
            } catch (Exception e) {
                log.error("Failed to trigger DAG {} (airflowVersion={}): {}",
                        airflowDagId, airflowVersion, e.getMessage());
                entry.put("success", false);
                entry.put("airflowDagId", airflowDagId);
                entry.put("message", String.format("Failed to trigger DAG run [airflow=%s]: %s",
                        airflowVersion, e.getMessage()));
            }

            results.add(entry);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("projectId", projectId);
        summary.put("deploymentId", deploymentId);
        summary.put("requestedFileName", (fileName == null || fileName.isBlank()) ? null : fileName);
        summary.put("totalDagFiles", dagFiles.size());
        summary.put("triggeredCount", successCount);
        summary.put("failedCount", dagFiles.size() - successCount);
        summary.put("success", successCount > 0);
        summary.put("results", results);
        return summary;
    }

    private Project requireAccessibleProject(String projectId) {
        Project p = projectRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        SecurityUtils.assertTenantInScope(p.getTenant().getTenantId());
        return p;
    }

    private Tenant resolveTenantForCreate(ProjectCreateRequest request) {
        if (SecurityUtils.isAdmin()) {
            if (!StringUtils.hasText(request.getTenantId())) {
                throw new DeploymentException("tenantId is required when creating a project as an administrator");
            }
            return tenantService.getTenantEntity(request.getTenantId().trim());
        }
        String scope = SecurityUtils.getNonAdminTenantScope()
                .orElseThrow(() -> new AccessDeniedException("Not authorized"));
        return tenantService.getTenantEntity(scope);
    }

    private void assertProjectDeploymentSameTenant(Project project, AirflowDeployment deployment) {
        if (!project.getTenant().getTenantId().equals(deployment.getTenant().getTenantId())) {
            throw new DeploymentException("Project and deployment must belong to the same tenant");
        }
    }

    private void deployProjectLocally(Project project, AirflowDeployment deployment) throws IOException {
        String tenantId = deployment.getTenant().getTenantId();
        String deploymentId = deployment.getDeploymentId();
        Path deploymentRoot = Paths.get(localBaseDirectory, tenantId, deploymentId);
        Path projectPath;

        // Determine project path based on strategy
        if (dagDeploymentConfig.isUnified()) {
            // UNIFIED: Deploy project files to main deployment directory
            // Configuration files at root, DAGs in dags/ folder
            projectPath = Paths.get(localBaseDirectory, tenantId, deploymentId);
            log.info("Using UNIFIED strategy - deploying to: {}", projectPath);
        } else {
            // SEPARATED: Deploy project in dedicated subdirectory
            projectPath = Paths.get(localBaseDirectory, tenantId, deploymentId, "projects", project.getProjectId());
            log.info("Using SEPARATED strategy - deploying to: {}", projectPath);
        }

        // Create project directory structure
        Path dagsDir = dagDeploymentConfig.isUnified()
            ? projectPath.resolve("dags")
            : projectPath.resolve("dags");
        Files.createDirectories(dagsDir);
        Files.createDirectories(projectPath.resolve("contracts"));
        Files.createDirectories(projectPath.resolve("plugins"));
        Files.createDirectories(projectPath.resolve("include"));
        Files.createDirectories(projectPath.resolve("tests"));

        // Write configuration files
        if (project.getRequirementsTxt() != null) {
            Files.writeString(projectPath.resolve("requirements.txt"), project.getRequirementsTxt());
        }
        if (project.getPackagesTxt() != null) {
            Files.writeString(projectPath.resolve("packages.txt"), project.getPackagesTxt());
        }
        if (project.getDockerfile() != null) {
            Files.writeString(projectPath.resolve("Dockerfile"), project.getDockerfile());
        }
        if (project.getAirflowSettingsYaml() != null) {
            Files.writeString(projectPath.resolve("airflow_settings.yaml"), project.getAirflowSettingsYaml());
        }
        if (project.getAirflowIgnore() != null) {
            Files.writeString(projectPath.resolve(".airflowignore"), project.getAirflowIgnore());
        }
        if (project.getEnvFile() != null) {
            Files.writeString(projectPath.resolve(".env"), project.getEnvFile());
        }

        // Runtime config is always written at deployment root so docker build/compose can use it.
        Files.createDirectories(deploymentRoot);
        if (project.getRequirementsTxt() != null) {
            Files.writeString(deploymentRoot.resolve("requirements.txt"), project.getRequirementsTxt());
        }
        if (project.getPackagesTxt() != null) {
            Files.writeString(deploymentRoot.resolve("packages.txt"), project.getPackagesTxt());
        }
        if (project.getDockerfile() != null) {
            Files.writeString(deploymentRoot.resolve("Dockerfile"), project.getDockerfile());
        }

        // Write project files (DAGs, plugins, etc.)
        List<ProjectFile> files = projectFileRepository.findByProject(project);
        for (ProjectFile file : files) {
            Path filePath;

            if (dagDeploymentConfig.isUnified() && file.getFileType() == ProjectFile.FileType.DAG) {
                // UNIFIED: Write DAGs directly to dags/ folder
                // Optionally prefix filename with project ID
                String fileName = dagDeploymentConfig.isPrefixProjectName()
                    ? project.getProjectId() + "__" + file.getFileName()
                    : file.getFileName();
                filePath = dagsDir.resolve(fileName);
            } else {
                // SEPARATED or non-DAG files: Use original path
                filePath = projectPath.resolve(file.getFilePath());
            }

            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, file.getContent());
            log.debug("Wrote project file: {}", filePath);
        }

        log.info("Project deployed locally at: {} (strategy: {})", projectPath, dagDeploymentConfig.getStrategy());
    }

    private void refreshLocalDeploymentRuntime(AirflowDeployment deployment) {
        if (deploymentProviderImpl == null) {
            log.warn("Skipping runtime refresh because deployment provider is unavailable");
            return;
        }
        if (!"local".equalsIgnoreCase(deploymentProviderImpl.getProviderType())) {
            log.info("Skipping runtime refresh: provider {} is not local", deploymentProviderImpl.getProviderType());
            return;
        }
        try {
            log.info("Syncing local Airflow stack for deployment {} after project deploy", deployment.getDeploymentId());
            deploymentProviderImpl.syncAfterProjectDeploy(deployment);
        } catch (Exception e) {
            throw new DeploymentException("Project files were synced but failed to refresh local runtime: " + e.getMessage(), e);
        }
    }

    private String generateProjectId(String name) {
        String baseId = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        String uniqueId = baseId + "_" + UUID.randomUUID().toString().substring(0, 8);

        // Ensure uniqueness
        while (projectRepository.existsByProjectId(uniqueId)) {
            uniqueId = baseId + "_" + UUID.randomUUID().toString().substring(0, 8);
        }

        return uniqueId;
    }

    private String getDefaultRequirements() {
        return """
                requests==2.32.3
                # Data contracts (AIP-07) are not shipped with Apache Airflow. Install your provider here
                # (e.g. git+https://... or a wheel) or use an Airflow image that already includes it
                # (see deployment.compose.airflow-image in application.yml).
                """;
    }

    private String getDefaultDockerfile(String airflowVersion) {
        String baseImage;
        if (StringUtils.hasText(composeAirflowImage)) {
            baseImage = composeAirflowImage.trim();
        } else {
            String version = airflowVersion != null ? airflowVersion : "3.1.8";
            baseImage = "apache/airflow:" + version;
        }
        return """
                FROM %s

                # Copy requirements and install
                COPY requirements.txt /requirements.txt
                RUN pip install --no-cache-dir -r /requirements.txt

                # Copy project files
                COPY . /opt/airflow/
                """.formatted(baseImage);
    }

    private String getDefaultAirflowIgnore() {
        return """
                # Ignore Python cache files
                __pycache__/
                *.py[cod]
                *$py.class

                # Ignore virtual environment
                venv/
                env/

                # Ignore IDE files
                .vscode/
                .idea/

                # Ignore test files
                tests/
                """;
    }

    private ResponseEntity<Map<String, Object>> postTriggerDagRun(String baseUrl, String airflowDagId, String airflowVersion) {
        if (isAirflow3OrLater(airflowVersion)) {
            return postTriggerDagRunAirflow3(baseUrl, airflowDagId);
        }
        return postTriggerDagRunAirflow2(baseUrl, airflowDagId);
    }

    private boolean isAirflow3OrLater(String airflowVersion) {
        if (airflowVersion == null || airflowVersion.isBlank()) {
            return true;
        }
        int end = 0;
        while (end < airflowVersion.length() && Character.isDigit(airflowVersion.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return true;
        }
        try {
            return Integer.parseInt(airflowVersion.substring(0, end)) >= 3;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private String obtainAirflowAccessToken(String baseUrl) {
        String tokenUrl = baseUrl + "/auth/token";
        Map<String, String> body = new HashMap<>();
        body.put("username", airflowApiUsername);
        body.put("password", airflowApiPassword);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenUrl, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});
        Map<String, Object> respBody = response.getBody();
        if (respBody == null || respBody.get("access_token") == null) {
            throw new DeploymentException("Airflow auth response missing access_token");
        }
        return respBody.get("access_token").toString();
    }

    private ResponseEntity<Map<String, Object>> postTriggerDagRunAirflow3(String baseUrl, String airflowDagId) {
        String encodedDagId = UriUtils.encodePathSegment(airflowDagId, StandardCharsets.UTF_8);
        String triggerUrl = baseUrl + "/api/v2/dags/" + encodedDagId + "/dagRuns";
        log.info("Triggering DAG run for {} at {} (Airflow 3+)", airflowDagId, triggerUrl);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("conf", new HashMap<>());
        requestBody.put("logical_date", DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)));
        requestBody.put("dag_run_id", "manual_" + System.currentTimeMillis());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(obtainAirflowAccessToken(baseUrl));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(triggerUrl, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<Map<String, Object>> postTriggerDagRunAirflow2(String baseUrl, String airflowDagId) {
        String encodedDagId = UriUtils.encodePathSegment(airflowDagId, StandardCharsets.UTF_8);
        String triggerUrl = baseUrl + "/api/v1/dags/" + encodedDagId + "/dagRuns";
        log.info("Triggering DAG run for {} at {} (Airflow 2.x)", airflowDagId, triggerUrl);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("conf", new HashMap<>());
        requestBody.put("dag_run_id", "manual__" + Instant.now().toEpochMilli());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(airflowApiUsername, airflowApiPassword);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(triggerUrl, HttpMethod.POST, request, new ParameterizedTypeReference<>() {});
    }

    /**
     * Extract the Airflow dag_id from Python DAG code.
     */
    private String extractAirflowDagId(String dagCode) {
        Pattern dagConstructorPattern = Pattern.compile("DAG\\s*\\(\\s*['\"]([^'\"]+)['\"]");
        java.util.regex.Matcher matcher = dagConstructorPattern.matcher(dagCode);
        if (matcher.find()) {
            return matcher.group(1);
        }

        Pattern dagIdPattern = Pattern.compile("dag_id\\s*=\\s*['\"]([^'\"]+)['\"]");
        matcher = dagIdPattern.matcher(dagCode);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}
