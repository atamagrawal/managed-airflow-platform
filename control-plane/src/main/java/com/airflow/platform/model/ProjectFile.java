package com.airflow.platform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity representing a file within a project (DAGs, plugins, includes, tests, etc.)
 */
@Entity
@Table(name = "project_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 500)
    private String filePath; // e.g., "dags/my_dag.py", "plugins/my_plugin.py"

    @Column(nullable = false, length = 100)
    private String fileName; // e.g., "my_dag.py"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileType fileType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content; // File content

    @Column(length = 1000)
    private String description;

    @Column
    private Long fileSize; // Size in bytes

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum FileType {
        DAG,        // Files in dags/ directory
        PLUGIN,     // Files in plugins/ directory
        INCLUDE,    // Files in include/ directory
        TEST,       // Files in tests/ directory
        UTIL,       // Utility files
        OTHER       // Other files
    }
}
