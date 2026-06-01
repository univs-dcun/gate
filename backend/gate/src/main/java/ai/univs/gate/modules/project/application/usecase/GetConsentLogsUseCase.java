package ai.univs.gate.modules.project.application.usecase;

import ai.univs.gate.modules.project.application.result.ConsentLogResult;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.repository.ConsentLogRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GetConsentLogsUseCase {

    private final ProjectService projectService;
    private final ConsentLogRepository consentLogRepository;

    @Transactional(readOnly = true)
    public List<ConsentLogResult> execute(Long projectId) {
        UserContext ctx = UserContext.get();
        Project project = projectService.validateOwnership(projectId, ctx.getAccountIdAsLong());

        return consentLogRepository.findByProjectOrderByCreatedAtDesc(project)
                .stream()
                .map(log -> ConsentLogResult.from(log, ctx.getTimezone()))
                .toList();
    }
}
