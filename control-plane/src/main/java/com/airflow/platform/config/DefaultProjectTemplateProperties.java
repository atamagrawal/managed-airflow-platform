package com.airflow.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Root folder for files copied into every new project. Defaults to {@code classpath:default-project/}.
 * Override {@code project.default-template.location} with another {@code classpath:...} or {@code file:...} URL.
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
     * Must end with {@code /}. Under this root, use subfolders {@code dags/}, {@code contracts/}, {@code plugins/},
     * {@code include/}, {@code tests/} — same layout as an Astronomer-style project.
     */
    private String location = "classpath:default-project/";
}
