package com.airflow.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO for updating an existing project file (content is the primary use case).
 */
@Data
public class ProjectFileUpdateRequest {

    @NotBlank(message = "Content is required")
    private String content;

    private String description;
}
