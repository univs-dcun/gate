package ai.univs.gate.support.api_key;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.utils.ApiKeyMasker;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * API 키 조회 진입점.
 *
 * <p>UG-281: 조회 메서드가 <b>소유 검증을 하느냐</b>로 갈린다. 이름 없는 {@code findByApiKey(String)}
 * 하나만 있던 시절에는 인증 경로도 그것을 불렀고, 그래서 계정 A 가 계정 B 의 키를 헤더에 넣으면
 * B 의 프로젝트로 동작했다. 지금은 세 메서드가 각각 무엇을 보장하는지 이름에 드러나며, 예전 이름을
 * <b>남기지 않았다</b> — 호출처가 컴파일 에러로 드러나 하나하나 의식적으로 고르게 하려는 것이다.
 *
 * <ul>
 *   <li>{@link #findOwnedByApiKey} — 인증 경로 전용. 항상 검증한다.
 *   <li>{@link #findByApiKey(CallerType, String, Long)} — 데모·인증이 공유하는 UseCase.
 *       {@link CallerType} 으로 갈린다.
 *   <li>{@link #findByApiKeyUnverified} — 무인증(데모) 경로 전용. 신규 사용 금지.
 * </ul>
 *
 * <p>{@code findByApiKeyUnverified} 가 {@code facade.demo} 밖에서 불리면 실패하는 테스트가 있다
 * ({@code ApiKeyOwnershipGuardTest}). 나중에 새 인증 API 를 만들며 무심코 집어드는 것을 막는다.
 *
 * <p><b>왜 데모는 검증하지 않는가.</b> {@code /api/v1/demo/**} 는 4개 환경(dev·stage·prod·onpremise)
 * 게이트웨이 전부에서 {@code AuthenticationFilter} 가 붙어 있지 않다. QR 로 접근한 일반 사용자에게는
 * 계정이 없고, 그 자리에서 카메라가 바로 떠야 하기 때문이다. 즉 데모에는 대조할 accountId 자체가
 * 없으므로 검증할 수 없다 — 생략이 아니라 부재다.
 *
 * <p>그래서 이 검증이 오히려 데모 설계를 지탱한다. 데모 페이지는 브라우저에서 직접
 * {@code /api/v1/demo/**} 를 호출하므로 API 키가 반드시 클라이언트에 노출된다. 그 키만으로 할 수 있는
 * 일이 데모 범위(등록·매칭·라이브니스·목록)에 머물러야 "키는 공개돼도 된다" 가 성립하는데, 검증이
 * 없으면 아무나 자기 계정으로 로그인한 뒤 그 키로 <b>매칭 이력·대시보드·특징점 삭제</b>까지 닿는다.
 */
@Slf4j
@Service
@RefreshScope
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    /**
     * 소유 검증 실패 시의 동작.
     *
     * <p>{@code ENFORCE}(기본)는 차단하고, {@code LOG_ONLY} 는 경고만 남기고 통과시킨다.
     * 어느 쪽이든 WARN 로그는 동일하게 남는다.
     *
     * <p>LOG_ONLY 를 남겨 둔 이유: 배포 시점에 "정상인데 불일치로 호출하던 기존 고객" 이 있는지
     * 실트래픽으로 확인할 방법이 없었다. 되돌릴 수단이 롤백뿐인 상황을 만들지 않으려는 안전장치다.
     *
     * <p><b>실제 전환 절차</b>({@link RefreshScope}). 게이트웨이를 통한 호출은 되지 않는다 —
     * actuator 라우트는 dev 게이트웨이에만 있고 stage·prod·onpremise 에는 없으며, 액추에이터가
     * {@code MANAGEMENT_SERVER_PORT}(9001) 라는 별도 포트에 뜨기 때문이다. 서버에서 컨테이너
     * 내부 포트로 직접 호출해야 한다.
     *
     * <pre>{@code
     * # 1. gate-config main 에서 mode 를 LOG_ONLY 로 바꾸고 push
     * # 2. gate 컨테이너가 뜬 서버에서
     * docker exec <gate-container> curl -s -X POST localhost:9001/gate/actuator/refresh
     * }</pre>
     *
     * <p>이 절차는 <b>실제 환경에서 검증하지 않았다.</b> 되돌림이 필요해지는 상황에서 처음
     * 시도하는 일이 없도록, dev 에 배포한 뒤 한 번 확인해 둘 것. 동작하지 않으면 컨테이너
     * 재기동(설정은 기동 시 다시 읽는다)이 차선책이다.
     *
     * <p>관측 결과 불일치가 없다고 확인되면 이 속성과 분기를 제거할 것.
     */
    public enum OwnershipMode {
        ENFORCE,
        LOG_ONLY
    }

    /**
     * enum 이 아니라 문자열로 받는다. enum 으로 직접 바인딩하면 오타 하나가 전면 장애가 되기
     * 때문이다 — {@link RefreshScope} 빈은 refresh 이후 지연 생성되므로, 알 수 없는 값으로
     * refresh 하면 그때부터 매 요청이 {@code BeanCreationException} 으로 터지고 이 빈을 주입받는
     * 30여 개 컴포넌트가 전부 500 이 된다. 되돌리려다 더 큰 장애를 내는 셈이다.
     *
     * <p>문자열로 받아 {@link #mode()} 에서 해석하면, 잘못된 값은 경고와 함께 ENFORCE 로 떨어진다.
     * 보안 통제이므로 해석 실패 시 <b>막는 쪽</b>이 안전한 기본값이다.
     */
    @Value("${gate.security.api-key-ownership.mode:ENFORCE}")
    private String modeProperty;

    private OwnershipMode mode() {
        try {
            return OwnershipMode.valueOf(modeProperty.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("gate.security.api-key-ownership.mode 값을 해석할 수 없어 ENFORCE 로 처리한다. value={}",
                    modeProperty);
            return OwnershipMode.ENFORCE;
        }
    }

    /**
     * 인증 경로 전용 조회. {@code accountId} 가 이 키의 프로젝트 소유자와 다르면 거부한다.
     *
     * @param accountId 게이트웨이가 JWT 검증 결과로 덮어쓴 {@code X-Account-Id}. 클라이언트가 보낸
     *                  값은 {@code AuthenticationFilter} 가 항상 덮어쓰므로 위조할 수 없다.
     */
    public ApiKey findOwnedByApiKey(String apiKey, Long accountId) {
        ApiKey found = findByApiKeyUnverified(apiKey);
        validateOwnership(found, accountId);
        return found;
    }

    /**
     * 데모와 인증 경로가 <b>같은 빈을 공유하는</b> UseCase 전용.
     *
     * <p>{@code FaceVerifyByFeatureId}·{@code FaceVerifyByFeatureImage}·{@code IdentifyFace}·
     * {@code LivenessFace}·{@code IdentifyPalm}·{@code LivenessPalm} 여섯 개가 여기 해당한다.
     * {@code DemoController} 와 {@code FaceController}/{@code PalmController} 가 같은 인스턴스를
     * 주입받으므로, 검증 여부를 컴파일 시점에 정할 수 없고 요청마다 {@link CallerType} 으로 가른다.
     */
    public ApiKey findByApiKey(CallerType callerType, String apiKey, Long accountId) {
        return callerType == CallerType.DEMO
                ? findByApiKeyUnverified(apiKey)
                : findOwnedByApiKey(apiKey, accountId);
    }

    /**
     * 소유 검증 <b>없이</b> 조회한다. {@code facade.demo} 패키지에서만 쓴다.
     *
     * <p>인증 경로에서 이것을 부르면 테넌트 격리가 뚫린다. 새 코드에서 이 메서드가 필요해 보이면
     * 십중팔구 {@link #findOwnedByApiKey} 를 써야 하는 상황이다.
     *
     * <p><b>UG-288: 삭제된 프로젝트의 키는 여기서 걸린다.</b> 세 조회 메서드가 전부 이 메서드를
     * 거치므로, 삭제 검사를 여기 한 곳에만 두면 인증·데모·공유 UseCase 가 함께 닫힌다. 소유 검증과
     * 달리 데모도 예외가 아니다 — 데모 키가 공개돼도 되는 근거는 "그 키로 할 수 있는 일이 데모
     * 범위에 머문다" 인데, 삭제된 프로젝트에는 머물 범위 자체가 없다.
     *
     * <p>없는 키와 같은 {@link ErrorType#API_KEY_NOT_FOUND} 로 막는다. "삭제된 프로젝트의 키"라고
     * 알려주면 그 키가 <b>실재했다</b>는 사실을 확인해 주는 셈이라, {@link #validateOwnership} 과
     * 같은 열거 오라클이 된다.
     */
    public ApiKey findByApiKeyUnverified(String apiKey) {
        ApiKey found = apiKeyRepository.findByApiKeyAndIsActiveTrue(apiKey)
                .orElseThrow(() -> new CustomGateException(ErrorType.API_KEY_NOT_FOUND));

        validateProjectNotDeleted(found);

        return found;
    }

    /**
     * 삭제된 프로젝트의 키를 거부한다 (UG-288).
     *
     * <p>조회 쿼리에 조건을 붙이면(파생 쿼리 {@code ...AndProject_IsDeletedFalse}) 쿼리 한 번으로
     * 끝나지만, <b>이 프로젝트에는 그것을 검증할 테스트가 없다.</b> {@code @DataJpaTest} 도
     * {@code @SpringBootTest} 도 없고 H2 는 {@code developmentOnly} 라 테스트 클래스패스에도 없다.
     * 보안 통제를 어떤 테스트도 닿지 않는 자리에 두지 않으려고 자바 조건으로 뒀다 — 대가는
     * {@code getProject()} 지연 로딩 한 번이며, 인증 경로는 어차피 {@link #validateOwnership} 에서
     * 같은 연관을 읽는다.
     *
     * <p>JPA 슬라이스 테스트가 생기면 쿼리로 옮기는 편이 낫다.
     */
    private void validateProjectNotDeleted(ApiKey apiKey) {
        if (!apiKey.getProject().isDeleted()) {
            return;
        }

        // 정상 사용에서는 나올 수 없다. 삭제 시 키도 함께 비활성화되므로(DeleteProjectUseCase)
        // 여기까지 왔다는 것은 그 경로를 타지 않고 삭제된 행이 있다는 뜻이다.
        log.warn("삭제된 프로젝트의 API 키로 호출이 들어왔다. projectId={}, apiKey={}",
                apiKey.getProject().getId(), ApiKeyMasker.mask(apiKey.getApiKey()));

        throw new CustomGateException(ErrorType.API_KEY_NOT_FOUND);
    }

    public ApiKey findByProject(Project project) {
        return apiKeyRepository.findActiveByProjectId(project.getId())
                .orElseThrow(() -> new CustomGateException(ErrorType.API_KEY_NOT_FOUND));
    }

    /**
     * 오류 코드는 {@link ErrorType#API_KEY_NOT_FOUND} 로 통일한다. "키는 있으나 네 것이 아님" 을
     * 별도 코드로 알려주면 남의 키의 <b>존재</b>를 확인해 주는 열거 오라클이 된다. 공격자가 키 후보를
     * 넣어 보며 유효/무효를 가려낼 수 있게 되므로, 없는 키와 남의 키를 구분 없이 같은 응답으로 막는다
     * (UG-250 계정 열거 방지와 같은 논리).
     *
     * <p>{@code NOT_OWNERSHIP} 을 쓰지 않은 것도 같은 이유다. 그 코드는 프로젝트 ID 처럼 이미
     * 소유자에게만 알려진 식별자를 다루는 경로용이다.
     */
    private void validateOwnership(ApiKey apiKey, Long accountId) {
        Long ownerAccountId = apiKey.getProject().getAccountId();
        if (ownerAccountId.equals(accountId)) {
            return;
        }

        OwnershipMode currentMode = mode();

        // 정상 사용에서는 나올 수 없는 조합이다. 조사할 수 있도록 남기되 키 원문은 가린다.
        log.warn("API 키 소유 불일치 — 요청 accountId={}, 키 소유 accountId={}, projectId={}, apiKey={}, mode={}",
                accountId, ownerAccountId, apiKey.getProject().getId(),
                ApiKeyMasker.mask(apiKey.getApiKey()), currentMode);

        if (currentMode == OwnershipMode.ENFORCE) {
            throw new CustomGateException(ErrorType.API_KEY_NOT_FOUND);
        }
    }
}
