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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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
 * <p><b>왜 클래스에 {@code @Transactional(readOnly = true)} 가 붙어 있는가 (UG-335).</b>
 * {@link #validateOwnership} 이 {@code apiKey.getProject().getAccountId()} 로 지연 프록시를
 * 초기화한다. 영속성 컨텍스트가 열려 있어야 하는데, 예전에는 그 책임이 <b>호출자</b>에게
 * 있었다 — 유스케이스 47개 중 43개가 {@code @Transactional} 이라 대부분 성립했고, 나머지는
 * {@code open-in-view} 기본값(true)이 요청 끝까지 컨텍스트를 열어 둔 덕에 동작했다.
 *
 * <p>UG-335 가 그것을 끄면서 두 유스케이스({@code ExtractUseCase},
 * {@code GetFeatureListUseCase})가 드러났다. 반박 리뷰가 세 번째
 * ({@code CreatePalmFeatureUseCase})를 더 찾았는데, 그쪽은 이 클래스가 아니라 자기
 * {@code @Transactional} 로 해결했다 — 쌍둥이인 face 쪽과 대칭을 맞추는 편이 맞았다.
 * 앞의 둘에 선언을 붙이는 방법도 있었지만
 * {@code ExtractUseCase} 는 조회 직후 face 서비스를 Feign 으로 부른다 — 트랜잭션으로 감싸면
 * <b>원격 호출 내내 DB 커넥션을 붙든다.</b> 부하가 걸릴 때 풀을 고갈시키는, OSIV 를 끄려던
 * 이유와 똑같은 형태의 문제다.
 *
 * <p>그래서 자기 지연 로딩의 경계는 자기가 연다. 이 클래스는 조회만 하고 원격 호출이 없으므로
 * 경계가 짧고, 바깥 트랜잭션이 있으면 {@code REQUIRED} 로 합류해 기존 43곳의 동작은 달라지지
 * 않는다. 호출자는 반환된 엔티티의 {@code getProject()} 를 트랜잭션 밖에서도 읽을 수 있다 —
 * 소유 검증이 이미 프록시를 초기화해 두기 때문이다.
 *
 * <p>그래서 이 검증이 오히려 데모 설계를 지탱한다. 데모 페이지는 브라우저에서 직접
 * {@code /api/v1/demo/**} 를 호출하므로 API 키가 반드시 클라이언트에 노출된다. 그 키만으로 할 수 있는
 * 일이 데모 범위(등록·매칭·라이브니스·목록)에 머물러야 "키는 공개돼도 된다" 가 성립하는데, 검증이
 * 없으면 아무나 자기 계정으로 로그인한 뒤 그 키로 <b>매칭 이력·대시보드·특징점 삭제</b>까지 닿는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    /**
     * 인증 경로 전용 조회. {@code accountId} 가 이 키의 프로젝트 소유자와 다르면 거부한다.
     *
     * @param accountId 게이트웨이가 JWT 검증 결과로 덮어쓴 {@code X-Account-Id}. 클라이언트가 보낸
     *                  값은 {@code AuthenticationFilter} 가 항상 덮어쓰므로 위조할 수 없다.
     */
    public ApiKey findOwnedByApiKey(String apiKey, Long accountId) {
        requireAccountId(apiKey, accountId);

        ApiKey found = findByApiKeyUnverified(apiKey);
        validateOwnership(found, accountId);
        return found;
    }

    /**
     * UG-277: 인증 경로인데 {@code X-Account-Id} 가 없으면 여기서 끝낸다.
     *
     * <p>{@code UserContext.getAccountIdAsLong()} 은 헤더가 없으면 {@code null} 을 돌려준다.
     * 게이트웨이를 경유하면 {@code AuthenticationFilter} 가 항상 채우므로 정상 트래픽에서는
     * 나오지 않고, gate 포트에 직접 붙는 경우(내부망 호출·디버깅·포트 노출)에만 생긴다.
     *
     * <p><b>왜 여기인가.</b> 그 {@code null} 이 그대로 흘러가면 매칭 UseCase 들이
     * {@code input.accountId().toString()} 에서 NPE(500)를 낸다. 그것도
     * {@code matchHistoryRepository.save()} 뒤라 사유 없는 실패 이력 행이 남는다. 소유 검증보다
     * 앞에 두면 {@link #findByApiKey} 를 타는 공유 UseCase 8곳과 인증 전용 조회 20곳이 한
     * 자리에서 닫힌다. (반박 리뷰 지적으로 "6곳" 을 고쳤다 —
     * {@code FaceFeatureService}·{@code PalmFeatureService} 경유 2곳이 빠져 있었다.)
     *
     * <p><b>왜 필요해졌나.</b> 아래 {@link #validateOwnership} 이 {@code null} 을 불일치로 보고
     * 거부하므로 지금은 NPE 까지 가지 않는다. 이 가드가 필요해진 것은 되돌림 스위치
     * ({@code LOG_ONLY}) 가 그 거부를 통과시켰기 때문인데, 그 스위치는 UG-306 에서 제거됐다.
     * 그래도 이 가드는 남긴다 — 소유 검증에 의존해 NPE 를 막는 구조는 한 겹이 사라지면
     * 조용히 무너지고, 여기서 막으면 그 의존 자체가 없어진다.
     *
     * <p><b>이 가드가 아래 겹을 관측 불가로 만들었다</b> (반박 리뷰 지적). 이제 {@code null} 이
     * {@link #validateOwnership} 까지 갈 수 없으므로, 그쪽이 {@code null} 을 어떻게 다루는지는
     * 어떤 테스트로도 볼 수 없다. 이 가드를 지우는 변이는 잡히지만, 지운 <b>뒤에</b> 아래 겹까지
     * 손대는 조합은 잡히지 않는다. 이 가드가 사실상 단일 방어선이라는 뜻이다.
     *
     * <p><b>왜 새 오류 코드를 만들지 않았나.</b> {@link ErrorType#API_KEY_NOT_FOUND} 를 그대로
     * 쓰면 응답이 <b>바이트 단위로 그대로</b>다 — 소유 불일치도 같은 코드가 나간다. 열거 오라클을
     * 만들지 않으려면 여기서도 같아야 한다.
     *
     * <p>로그는 소유 불일치와 <b>구분해서</b> 남긴다. 같은 WARN 으로 뭉치면 소유 불일치 집계에
     * 헤더 누락이 섞여 들어가 원인을 가를 수 없다.
     */
    private void requireAccountId(String apiKey, Long accountId) {
        if (accountId != null) {
            return;
        }

        log.warn("인증 경로 호출에 X-Account-Id 가 없다. 게이트웨이를 우회한 호출로 보인다. apiKey={}",
                ApiKeyMasker.mask(apiKey));
        throw new CustomGateException(ErrorType.API_KEY_NOT_FOUND);
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
     * <p><b>UG-288: 삭제된 프로젝트의 키는 여기서 걸린다.</b> 키 문자열로 조회하는 세 메서드
     * ({@link #findOwnedByApiKey}, {@link #findByApiKey}, 이 메서드)가 전부 여기를 거치므로,
     * 검사를 한 곳에 두면 인증·데모·공유 UseCase 가 함께 닫힌다. 프로젝트로 조회하는
     * {@link #findByProject} 만 이 경로 밖이라 거기서 따로 막는다. 소유 검증과
     * 달리 데모도 예외가 아니다 — 데모 키가 공개돼도 되는 근거는 "그 키로 할 수 있는 일이 데모
     * 범위에 머문다" 인데, 삭제된 프로젝트에는 머물 범위 자체가 없다.
     *
     * <p>없는 키와 같은 {@link ErrorType#API_KEY_NOT_FOUND} 로 막는다. "삭제된 프로젝트의 키"라고
     * 알려주면 그 키가 <b>실재했다</b>는 사실을 확인해 주는 셈이라, {@link #validateOwnership} 과
     * 같은 열거 오라클이 된다.
     */
    public ApiKey findByApiKeyUnverified(String apiKey) {
        return apiKeyRepository.findActiveByApiKeyWithLiveProject(apiKey)
                .orElseThrow(() -> {
                    warnIfProjectDeleted(apiKey);
                    return new CustomGateException(ErrorType.API_KEY_NOT_FOUND);
                });
    }

    /**
     * 조회가 빈 이유가 "삭제된 프로젝트" 였는지 확인해 로그만 남긴다 (UG-300).
     *
     * <p><b>규칙 자체는 이제 쿼리가 강제한다</b>
     * ({@code findActiveByApiKeyWithLiveProject}). UG-288 은 이 검사를 자바 조건으로 뒀는데,
     * 그때는 조회 조건을 검증할 슬라이스 테스트가 없었기 때문이다 — 보안 통제를 "더 나은
     * 자리" 가 아니라 "검증 가능한 자리" 에 둔 것이다. UG-300 이 인프라를 만들어 제자리로
     * 옮겼다.
     *
     * <p>그러면서 잃을 뻔한 것이 이 로그다. 쿼리가 걸러 버리면 "키가 없다" 와 "프로젝트가
     * 삭제됐다" 가 호출자에게도 로그에도 구분되지 않는다. 응답이 같아야 하는 것은 맞지만
     * (열거 오라클 방지) 운영자는 구분할 수 있어야 한다. 그래서 <b>실패 경로에서만</b> 한 번 더
     * 조회해 남긴다. 정상 호출에는 추가 쿼리가 없다.
     *
     * <p>정상 사용에서는 나올 수 없는 로그다. 삭제 시 키도 함께 비활성화되므로
     * ({@code DeleteProjectUseCase}) 이 WARN 은 그 경로를 타지 않고 삭제된 행이 있다는 신호다.
     *
     * <p><b>진단이 응답을 바꾸면 안 된다</b> (반박 리뷰 지적). {@code getProject()} 는 지연
     * 프록시고, {@code api_keys.project_id} 에는 외래 키 제약이 없다 (V1 확인). 즉 프로젝트
     * 행이 사라진 고아 키가 물리적으로 가능하고, 그 프록시를 건드리면
     * {@link jakarta.persistence.EntityNotFoundException} 이 난다. 그러면 "없는 키" 는 400,
     * "고아 키" 는 500 이 되어, 이 클래스가 피하려는 열거 오라클이 <b>진단 코드 때문에</b>
     * 생긴다. 그래서 통째로 감싼다 — 로그를 남기려다 응답을 바꾸는 일은 없어야 한다.
     */
    private void warnIfProjectDeleted(String apiKey) {
        try {
            apiKeyRepository.findByApiKeyAndIsActiveTrue(apiKey)
                    .ifPresent(found -> log.warn(
                            "살아 있는 프로젝트가 없는 API 키로 호출이 들어왔다. "
                                    + "projectId={}, deleted={}, apiKey={}",
                            found.getProject().getId(), found.getProject().isDeleted(),
                            ApiKeyMasker.mask(found.getApiKey())));
        } catch (RuntimeException e) {
            // 프로젝트 행이 없는 고아 키 등. 진단이 실패해도 호출자에게는 같은 응답을 준다.
            log.warn("프로젝트를 읽을 수 없는 API 키로 호출이 들어왔다. apiKey={}, 원인={}",
                    ApiKeyMasker.mask(apiKey), e.getClass().getSimpleName());
        }
    }

    /**
     * 프로젝트로 활성 키를 찾는다.
     *
     * <p>이 메서드는 {@link #findByApiKeyUnverified} 를 거치지 않으므로 삭제 검사를 여기서 따로
     * 한다 (반박 리뷰 지적). 현재 유일한 호출처인 {@code GetProjectUseCase} 는
     * {@code findByIdAndIsDeletedFalse} 로 얻은 프로젝트만 넘겨서 도달할 수 없지만, 호출처가
     * 늘면 조용히 뚫리는 자리다.
     *
     * <p><b>키가 없을 때와 같은 {@link ErrorType#API_KEY_NOT_FOUND} 로 막는다</b> (델타 리뷰
     * 지적). 처음에는 {@code PROJECT_NOT_FOUND} 를 던졌는데, 그러면 이 클래스가 세 문단에 걸쳐
     * 피하려고 한 열거 오라클을 이 메서드만 다시 만든다 — 호출자가 "삭제된 프로젝트" 와 "키 없음"
     * 을 응답으로 구분할 수 있게 된다. 이 가드는 <b>아직 없는 호출처</b>를 위한 것이므로 그 호출처가
     * API 키 인증 경로일지 계정 인증 경로일지 알 수 없고, 안전한 쪽으로 맞춘다.
     */
    public ApiKey findByProject(Project project) {
        if (project.isDeleted()) {
            log.warn("삭제된 프로젝트로 API 키를 조회했다. projectId={}", project.getId());
            throw new CustomGateException(ErrorType.API_KEY_NOT_FOUND);
        }

        return apiKeyRepository.findLatestActiveByProjectId(project.getId())
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

        // 정상 사용에서는 나올 수 없는 조합이다. 조사할 수 있도록 남기되 키 원문은 가린다.
        log.warn("API 키 소유 불일치 — 요청 accountId={}, 키 소유 accountId={}, projectId={}, apiKey={}",
                accountId, ownerAccountId, apiKey.getProject().getId(),
                ApiKeyMasker.mask(apiKey.getApiKey()));

        throw new CustomGateException(ErrorType.API_KEY_NOT_FOUND);
    }
}
