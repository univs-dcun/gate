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
                .orElseThrow(() -> {
                    // UG-298 리뷰 지적. 여기까지 왔다는 것은 "소유가 확인된, 삭제되지 않은
                    // 프로젝트에 활성 API 키가 없다" 는 뜻이다. CreateProjectUseCase 가 키를
                    // 프로젝트와 같은 트랜잭션에서 저장하고, 키를 끄는 두 곳(삭제·재발급)은
                    // 모두 같은 트랜잭션 안에서 처리하므로 그런 행은 있을 수 없다 —
                    // 데이터가 깨진 것이다.
                    //
                    // API_KEY_NOT_FOUND 자체는 4xx 로 둔다. 없는 키·남의 키와 같은 코드를
                    // 써서 열거 오라클을 막는 것이 우선이기 때문이다(ApiKeyService 참고).
                    // 대신 이 자리에서 직접 남긴다 — 안 그러면 WARN 한 줄로 조용히 지나간다.
                    log.error("활성 API 키가 없는 프로젝트다 — 데이터 정합성 문제. projectId={}",
                            projectId);
                    return new CustomGateException(ErrorType.API_KEY_NOT_FOUND);
                });
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
