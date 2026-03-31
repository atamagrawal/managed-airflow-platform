package com.airflow.platform.repository;

import com.airflow.platform.model.Project;
import com.airflow.platform.model.ProjectFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ProjectFile entity
 */
@Repository
public interface ProjectFileRepository extends JpaRepository<ProjectFile, Long> {

    List<ProjectFile> findByProject(Project project);

    List<ProjectFile> findByProjectProjectId(String projectId);

    List<ProjectFile> findByProjectAndFileType(Project project, ProjectFile.FileType fileType);

    Optional<ProjectFile> findByProjectAndFilePath(Project project, String filePath);

    Optional<ProjectFile> findByIdAndProject_ProjectId(Long id, String projectId);

    List<ProjectFile> findByFileType(ProjectFile.FileType fileType);

    void deleteByProject(Project project);
}
