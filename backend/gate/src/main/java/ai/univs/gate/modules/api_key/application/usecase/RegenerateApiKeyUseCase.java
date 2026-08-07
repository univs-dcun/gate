package ai.univs.gate.modules.api_key.application.usecase;

import ai.univs.gate.modules.api_key.application.result.ApiKeyResult;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
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
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegenerateApiKeyUseCase {

    private final ProjectService projectService;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyGenerator apiKeyGenerator;

    @Value("${api-key.expiry-days}")
    private int apiKeyExpiryDays;

    /**
     * 활성 키를 <b>전부</b> 끄고 하나를 새로 발급한다 (UG-302).
     *
     * <p>예전에는 활성 키 하나를 {@code Optional} 로 읽어 그것만 껐다. 두 가지가 문제였다.
     *
     * <ul>
     *   <li>잠금이 없어 동시 호출이면 둘 다 같은 키를 읽고 각자 새 키를 넣었다 — 활성 2개.
     *   <li>활성이 2개가 되는 순간 그 {@code Optional} 조회가
     *       {@code IncorrectResultSizeDataAccessException} 을 던져 <b>재발급 자체가 500</b> 이
     *       됐다. 상태를 고칠 유일한 수단이 그 상태 때문에 막히는 구조였다.
     * </ul>
     *
     * <p>그래서 프로젝트 행을 잠가 직렬화하고, 활성인 것을 개수와 무관하게 전부 끈다. 이미
     * 어긋나 있던 프로젝트도 이 한 번의 호출로 정상으로 돌아온다.
     */
    @Transactional
    public ApiKeyResult execute(Long accountId, Long projectId) {
        Project project = projectService.validateOwnershipForUpdate(projectId, accountId);

        List<ApiKey> activeKeys = apiKeyRepository.findAllActiveByProjectId(projectId);
        if (activeKeys.isEmpty()) {
            // UG-298 리뷰 지적. 여기까지 왔다는 것은 "소유가 확인된, 삭제되지 않은 프로젝트에
            // 활성 API 키가 없다" 는 뜻이다. CreateProjectUseCase 가 키를 프로젝트와 같은
            // 트랜잭션에서 저장하고, 키를 끄는 두 곳(삭제·재발급)은 모두 같은 트랜잭션 안에서
            // 처리하므로 그런 행은 있을 수 없다 — 데이터가 깨진 것이다.
            //
            // API_KEY_NOT_FOUND 자체는 4xx 로 둔다. 없는 키·남의 키와 같은 코드를 써서 열거
            // 오라클을 막는 것이 우선이기 때문이다(ApiKeyService 참고). 대신 이 자리에서 직접
            // 남긴다 — 안 그러면 WARN 한 줄로 조용히 지나간다.
            log.error("활성 API 키가 없는 프로젝트다 — 데이터 정합성 문제. projectId={}", projectId);
            throw new CustomGateException(ErrorType.API_KEY_NOT_FOUND);
        }

        if (activeKeys.size() > 1) {
            log.error("활성 API 키가 {}개인 프로젝트를 재발급으로 정리한다. projectId={}, apiKeyIds={}",
                    activeKeys.size(), projectId, activeKeys.stream().map(ApiKey::getId).toList());
        }
        activeKeys.forEach(ApiKey::deactivate);

        // 비활성화를 <b>지금</b> 내보낸다 (반박 리뷰가 실측으로 찾은 블로커).
        //
        // deactivate() 는 더티 마킹일 뿐이고, 아래 ApiKeyGenerator 는 SecureRandom 만 써서
        // DB 를 건드리지 않으므로 auto-flush 가 일어나지 않는다. 그대로 두면 IDENTITY 인
        // save() 가 id 를 받으려고 INSERT 를 먼저 내보내고, 그 시점에 DB 에는 기존 행이
        // 아직 활성이다 — V24 의 부분 유니크 인덱스가 그 순간을 잡아 재발급이 전부 실패한다.
        //
        // 인덱스가 없던 때는 순서가 드러나지 않았다. 그래서 이 한 줄이 빠져도 V24 전에는
        // 아무 신호가 없다. RegenerateApiKeyFlushOrderTest 가 순서를 못박는다.
        apiKeyRepository.flush();

        log.info("Old API Key deactivated: count={}, projectId={}", activeKeys.size(), projectId);


        // 새 API Key 발급
        String newApiKeyString = apiKeyGenerator.generateApiKey();
        String newSecretKey = apiKeyGenerator.generateSecretKey();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        ApiKey newApiKey = ApiKey.builder()
                .project(project)
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
