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
 * Seeds {@link ProjectFile} rows from a template directory (classpath or file). To change the default project,
 * edit files under {@code src/main/resources/default-project/} or point
 * {@code project.default-template.location} elsewhere — no Java code changes required.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultProjectTemplateSeeder {

    private static final String ROOT_DIR = "default-project";

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

        String root = normalizeRoot(properties.getLocation());
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

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
                String relative = pathUnderTemplateRoot(resource);
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
            log.warn("No dags/contracts/plugins template files under {} — check project.default-template.location",
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
        log.info("Seeded {} template file(s) ({} DAG(s)) for project {}", unique.size(), dagFiles, project.getProjectId());
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

    private static boolean endsWithAny(String path, String... suffixes) {
        String lower = path.toLowerCase(Locale.ROOT);
        for (String s : suffixes) {
            if (lower.endsWith(s)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeRoot(String location) {
        String t = Objects.requireNonNullElse(location, "classpath:default-project/").trim();
        if (!t.endsWith("/")) {
            return t + "/";
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
     * Returns path relative to {@code default-project/}, e.g. {@code dags/foo.py}.
     */
    static String pathUnderTemplateRoot(Resource resource) {
        String marker = ROOT_DIR + "/";
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
