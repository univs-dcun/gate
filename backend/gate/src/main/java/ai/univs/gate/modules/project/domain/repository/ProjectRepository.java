package ai.univs.gate.modules.project.domain.repository;

import ai.univs.gate.modules.project.application.input.ProjectQuery;
import ai.univs.gate.modules.project.application.result.ProjectSummaryResult;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface ProjectRepository {

    Project save(Project project);

    Page<ProjectSummaryResult> findByAccountIdAndIsDeletedFalse(ProjectQuery query);

    Optional<Project> findByIdAndIsDeletedFalse(Long id);

    /** 행 잠금을 걸고 읽는다 — 같은 프로젝트의 동시 변경을 직렬화한다 (UG-302). */
    Optional<Project> findForUpdateByIdAndIsDeletedFalse(Long id);

    long countByAccountIdAndIsDeletedFalse(Long userId);
}
