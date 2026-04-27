package ai.univs.gate.modules.api_key.application.usecase;

import ai.univs.gate.modules.api_key.application.result.ApiKeyResult;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetApiKeyUseCase {

    private final ProjectService projectService;
    private final ApiKeyRepository apiKeyRepository;

    @Transactional(readOnly = true)
    public ApiKeyResult execute(Long accountId, Long projectId) {
        projectService.validateOwnership(projectId, accountId);

        ApiKey apiKey = apiKeyRepository.findActiveByProjectId(projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.API_KEY_NOT_FOUND));

        return ApiKeyResult.from(apiKey, false);
    }
}
