package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.project.application.input.UpdatePackageKeyInput;
import ai.univs.gate.modules.project.application.result.ProjectResult;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdatePackageKeyUseCase {

    private final ProjectService projectService;

    @Transactional
    public ProjectResult execute(UpdatePackageKeyInput input) {
        Project project = projectService.validateOwnership(input.projectId(), input.accountId());

        if (!project.isExternal()) {
            throw new CustomGateException(ErrorType.PACKAGE_KEY_NOT_ALLOWED);
        }

        project.updatePackageKey(input.packageKey());

        return ProjectResult.from(project);
    }
}
