package ai.univs.gate.support.project;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectModuleType;
import ai.univs.gate.modules.project.domain.repository.ProjectRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    public Project findById(Long projectId) {
        return projectRepository.findByIdAndIsDeletedFalse(projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.PROJECT_NOT_FOUND));
    }

    public Project validateOwnership(Long projectId, Long userId) {
        Project project = projectRepository.findByIdAndIsDeletedFalse(projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.PROJECT_NOT_FOUND));

        validateOwnership(project, userId);

        return project;
    }

    public void validateFaceModuleType(Project project) {
        if (project.getProjectModuleType() != ProjectModuleType.FACE) {
            throw new CustomGateException(ErrorType.UNSUPPORTED_MODULE_TYPE);
        }
    }

    private void validateOwnership(Project project, Long userId) {
        if (!project.getAccountId().equals(userId)) {
            throw new CustomGateException(ErrorType.NOT_OWNERSHIP);
        }
    }
}
