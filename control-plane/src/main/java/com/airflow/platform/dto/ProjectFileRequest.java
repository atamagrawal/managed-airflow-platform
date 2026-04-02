package com.airflow.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for adding files to a project
 */
@Data
public class ProjectFileRequest {

    @NotBlank(message = "File path is required")
    @Size(max = 500, message = "File path must not exceed 500 characters")
    private String filePath;

    @NotBlank(message = "File name is required")
    @Size(max = 100, message = "File name must not exceed 100 characters")
    private String fileName;

    @NotBlank(message = "File type is required")
    private String fileType;  // DAG, CONTRACT, PLUGIN, INCLUDE, TEST, UTIL, OTHER

    @NotBlank(message = "Content is required")
    private String content;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;
}
