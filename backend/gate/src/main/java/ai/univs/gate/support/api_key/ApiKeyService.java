package ai.univs.gate.support.api_key;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKey findByApiKey(String apiKey) {
        return apiKeyRepository.findByApiKeyAndIsActiveTrue(apiKey)
                .orElseThrow(() -> new CustomGateException(ErrorType.API_KEY_NOT_FOUND));
    }

    public ApiKey findByProject(Project project) {
        return apiKeyRepository.findActiveByProjectId(project.getId())
                .orElseThrow(() -> new CustomGateException(ErrorType.API_KEY_NOT_FOUND));
    }
}
