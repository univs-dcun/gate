package ai.univs.gate.modules.api_key.application.usecase;

import ai.univs.gate.modules.api_key.application.result.ApiKeyResult;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyGenerator;
import ai.univs.gate.support.project.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegenerateApiKeyUseCase {

    private final ProjectService projectService;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyGenerator apiKeyGenerator;

    @Value("${api-key.expiry-days}")
    private int apiKeyExpiryDays;

    @Transactional
    public ApiKeyResult execute(Long accountId, Long projectId) {
        projectService.validateOwnership(projectId, accountId);

        // 기존 API Key 비활성화
        ApiKey oldApiKey = apiKeyRepository.findActiveByProjectId(projectId)
                .orElseThrow(() -> new CustomGateException(ErrorType.API_KEY_NOT_FOUND));
        oldApiKey.deactivate();
        log.info("Old API Key deactivated: apiKeyId={}", oldApiKey.getId());

        // 새 API Key 발급
        String newApiKeyString = apiKeyGenerator.generateApiKey();
        String newSecretKey = apiKeyGenerator.generateSecretKey();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        ApiKey newApiKey = ApiKey.builder()
                .project(oldApiKey.getProject())
                .apiKey(newApiKeyString)
                .secretKey(newSecretKey)
                .issuedAt(now)
                .expiresAt(now.plusDays(apiKeyExpiryDays))
                .isActive(true)
                .build();

        ApiKey savedApiKey = apiKeyRepository.save(newApiKey);
        log.info("New API Key issued: apiKeyId={}", savedApiKey.getId());

        return ApiKeyResult.from(savedApiKey, true);
    }
}
