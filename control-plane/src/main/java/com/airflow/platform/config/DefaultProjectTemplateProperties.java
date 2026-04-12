package com.airflow.platform.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Named project templates: only configuration + files on the classpath (or {@code file:} trees) — no Java changes
 * when adding a template. Register a name in {@link #templates}, set {@link #active}, add a resource folder, optionally
 * {@code extra-requirements.txt} at the template root (see {@link com.airflow.platform.service.DefaultProjectTemplateSeeder}).
 * <p>
 * Override {@code active} with env {@code PROJECT_DEFAULT_TEMPLATE_ACTIVE}.
 */
@Component
@ConfigurationProperties(prefix = "project.default-template")
@Data
public class DefaultProjectTemplateProperties {

    /**
     * When false, new projects are created without seed files (dagCount stays 0).
     */
    private boolean enabled = true;

    /**
     * Which {@link #templates} entry seeds new projects.
     */
    private String active = "classic";

    /**
     * Map template name → root URL (classpath or file), each ending with {@code /}.
     */
    private Map<String, String> templates = new LinkedHashMap<>(Map.of(
            "classic", "classpath:default-project/",
            "taskflow-contracts", "classpath:default-project-taskflow-contracts/"
    ));

    @PostConstruct
    public void initialize() {
        if (templates == null) {
            templates = new LinkedHashMap<>();
        }
        templates.replaceAll((k, v) -> {
            if (!StringUtils.hasText(v)) {
                return v;
            }
            String t = v.trim();
            return t.endsWith("/") ? t : t + "/";
        });
        if (!enabled) {
            return;
        }
        String key = StringUtils.hasText(active) ? active.trim() : "classic";
        String loc = templates.get(key);
        if (!StringUtils.hasText(loc)) {
            throw new IllegalStateException(
                    "project.default-template.active='" + key + "' is missing from project.default-template.templates");
        }
    }

    /**
     * Normalized template root; {@code templateKey} {@code null} or blank uses {@link #active}.
     */
    public String resolveLocation(String templateKey) {
        String key = StringUtils.hasText(templateKey) ? templateKey.trim()
                : (StringUtils.hasText(active) ? active.trim() : "classic");
        String loc = templates != null ? templates.get(key) : null;
        if (loc == null || !StringUtils.hasText(loc)) {
            throw new IllegalArgumentException(
                    "Unknown project default template key: '" + key + "'. Known keys: " + knownKeys());
        }
        String t = loc.trim();
        return t.endsWith("/") ? t : t + "/";
    }

    private String knownKeys() {
        if (templates == null || templates.isEmpty()) {
            return "(none configured)";
        }
        return String.join(", ", templates.keySet());
    }
}
