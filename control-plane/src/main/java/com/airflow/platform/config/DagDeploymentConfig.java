package com.airflow.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for DAG deployment strategies
 */
@Configuration
@ConfigurationProperties(prefix = "dag.deployment")
@Data
public class DagDeploymentConfig {

    /**
     * Strategy for organizing DAG files
     * - UNIFIED: All DAGs (standalone and project-based) go to {deployment}/dags/
     * - SEPARATED: Standalone DAGs go to {deployment}/dags/, project DAGs go to {deployment}/projects/{project}/dags/
     */
    private Strategy strategy = Strategy.UNIFIED;

    /**
     * Whether to include project metadata in filenames
     * When true, project DAGs will be prefixed with project name (e.g., myproject__my_dag.py)
     */
    private boolean prefixProjectName = false;

    /**
     * Base directory path for Airflow DAG files
     * Used for multiple DAG folder scanning in Airflow configuration
     */
    private String additionalDagFolders;

    public enum Strategy {
        /**
         * Unified strategy: All DAGs in single dags/ folder
         * Path: {localBaseDirectory}/{deploymentId}/dags/
         *
         * Pros:
         * - Simpler Airflow configuration (single DAG folder)
         * - All DAGs visible in one place
         * - No need for multiple DAG folder scanning
         *
         * Cons:
         * - Potential filename conflicts
         * - Less organized for large projects
         */
        UNIFIED,

        /**
         * Separated strategy: Project DAGs in separate folders
         * Standalone DAGs: {localBaseDirectory}/{deploymentId}/dags/
         * Project DAGs: {localBaseDirectory}/{deploymentId}/projects/{projectId}/dags/
         *
         * Pros:
         * - Clear separation between standalone and project DAGs
         * - Better organization for multiple projects
         * - No filename conflicts between projects
         *
         * Cons:
         * - Requires Airflow DAG folder configuration
         * - More complex directory structure
         */
        SEPARATED
    }

    /**
     * Check if unified strategy is enabled
     */
    public boolean isUnified() {
        return strategy == Strategy.UNIFIED;
    }

    /**
     * Check if separated strategy is enabled
     */
    public boolean isSeparated() {
        return strategy == Strategy.SEPARATED;
    }
}
