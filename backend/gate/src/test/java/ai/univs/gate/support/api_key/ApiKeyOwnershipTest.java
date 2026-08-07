package ai.univs.gate.support.api_key;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * UG-281: X-Api-Key 가 인증 계정 소유인지 검증한다.
 *
 * <p>이 검증이 없으면 계정 A 가 계정 B 의 API 키를 헤더에 넣는 것만으로 B 의 갤러리를 대상으로
 * 매칭 이력 조회·대시보드 열람·특징점 삭제까지 할 수 있다. 데모 경로가 무인증이라 API 키는 설계상
 * 브라우저에 노출되므로, 이 검증이 "키는 공개돼도 된다" 를 성립시키는 유일한 방어선이다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-281: API 키 소유 검증")
class ApiKeyOwnershipTest {

    private static final long OWNER = 100L;
    private static final long ATTACKER = 200L;
    private static final String KEY = "univs_live_abcdefghijklmnop";

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private ApiKey apiKey;

    @BeforeEach
    void setUp() {
        Project project = Project.builder().accountId(OWNER).build();
        ReflectionTestUtils.setField(project, "id", 42L);

        apiKey = ApiKey.builder().project(project).apiKey(KEY).isActive(true).build();

        enforce();
    }

    private void mode(String raw) {
        // 프로퍼티는 enum 이 아니라 문자열로 받는다 — 오타가 전면 장애가 되지 않게 하기 위해서다.
        // 자세한 이유는 ApiKeyService.modeProperty 주석 참고.
        ReflectionTestUtils.setField(apiKeyService, "modeProperty", raw);
    }

    private void enforce() {
        mode("ENFORCE");
    }

    private void logOnly() {
        mode("LOG_ONLY");
    }

    private void keyExists() {
        given(apiKeyRepository.findByApiKeyAndIsActiveTrue(KEY)).willReturn(Optional.of(apiKey));
    }

    @Nested
    @DisplayName("findOwnedByApiKey — 인증 경로")
    class Owned {

        @Test
        @DisplayName("소유자가 호출하면 통과한다")
        void 소유자_통과() {
            keyExists();

            assertThat(apiKeyService.findOwnedByApiKey(KEY, OWNER)).isSameAs(apiKey);
        }

        @Test
        @DisplayName("타 계정이 호출하면 거부한다 — 이 티켓의 본문")
        void 타계정_거부() {
            keyExists();

            assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey(KEY, ATTACKER))
                    .isInstanceOf(CustomGateException.class)
                    .satisfies(e -> assertThat(((CustomGateException) e).getErrorType())
                            .isEqualTo(ErrorType.API_KEY_NOT_FOUND));
        }

        @Test
        @DisplayName("없는 키와 남의 키가 같은 오류를 낸다 — 열거 오라클 방지")
        void 열거_오라클_없음() {
            given(apiKeyRepository.findByApiKeyAndIsActiveTrue("없는키")).willReturn(Optional.empty());
            keyExists();

            ErrorType 없는키 = errorTypeOf(() -> apiKeyService.findOwnedByApiKey("없는키", ATTACKER));
            ErrorType 남의키 = errorTypeOf(() -> apiKeyService.findOwnedByApiKey(KEY, ATTACKER));

            // 코드가 갈리면 공격자가 키 후보를 넣어 보며 '존재하는 키' 를 가려낼 수 있다.
            // NOT_OWNERSHIP 같은 별도 코드를 쓰고 싶어지면 이 테스트가 막는다. (UG-250 과 같은 논리)
            assertThat(남의키).isEqualTo(없는키).isEqualTo(ErrorType.API_KEY_NOT_FOUND);
        }

        private ErrorType errorTypeOf(Runnable call) {
            try {
                call.run();
                throw new AssertionError("예외가 발생하지 않았다");
            } catch (CustomGateException e) {
                return e.getErrorType();
            }
        }
    }

    /**
     * UG-301: 모드와 무관하게 막는 조회.
     *
     * <p>대시보드 네 엔드포인트가 쓴다. {@link ApiKeyService#findOwnedByApiKey} 는 LOG_ONLY 에서
     * 통과시키는데, 그 스위치를 켜는 순간 프로젝트 집계가 통째로 나가기 때문이다.
     *
     * <p>초판은 이 검증을 UseCase 안에서 {@code ProjectService.validateOwnership} 으로 했다.
     * 반박 리뷰가 두 가지를 짚어 여기로 옮겼다 — 그쪽은 {@code NOT_OWNERSHIP} 이라는 열거
     * 오라클을 만들고(이 클래스가 피하려는 바로 그것), SELECT 를 한 번 더 친다. 옮기면서
     * <b>모드를 실제로 세팅해 진짜 구현으로</b> 검증하게 됐다. UseCase 쪽은 이제 "어느 쪽을
     * 부르는가" 만 못박는다.
     */
    @Nested
    @DisplayName("findStrictlyOwnedByApiKey — 모드 무관 (UG-301)")
    class StrictOwned {

        @Test
        @DisplayName("소유자는 ENFORCE·LOG_ONLY 어느 쪽에서도 통과한다")
        void 소유자는_통과() {
            keyExists();

            enforce();
            assertThat(apiKeyService.findStrictlyOwnedByApiKey(KEY, OWNER)).isSameAs(apiKey);

            logOnly();
            assertThat(apiKeyService.findStrictlyOwnedByApiKey(KEY, OWNER)).isSameAs(apiKey);
        }

        /**
         * 이 묶음의 본문. 같은 인자로 {@link ApiKeyService#findOwnedByApiKey} 는 통과하는데
         * 이쪽은 막는다는 <b>대비</b>를 한 테스트 안에서 보인다 — 두 메서드가 갈리는 이유가
         * 곧 이 티켓이다.
         */
        @Test
        @DisplayName("LOG_ONLY 여도 타 계정을 거부한다 — 느슨한 쪽은 같은 인자로 통과한다")
        void 로그만이어도_거부() {
            keyExists();
            logOnly();

            assertThatCode(() -> apiKeyService.findOwnedByApiKey(KEY, ATTACKER))
                    .as("느슨한 쪽은 통과해야 한다. 여기가 깨지면 LOG_ONLY 가 이미 죽은 것이다")
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> apiKeyService.findStrictlyOwnedByApiKey(KEY, ATTACKER))
                    .isInstanceOf(CustomGateException.class)
                    .satisfies(e -> assertThat(((CustomGateException) e).getErrorType())
                            .isEqualTo(ErrorType.API_KEY_NOT_FOUND));
        }

        /**
         * 오류 코드가 느슨한 쪽과 같아야 한다.
         *
         * <p>{@code NOT_OWNERSHIP} 을 쓰면 "이 키는 실재하고 남의 것" 을 확인해 주는 열거
         * 오라클이 된다 — {@link ApiKeyService#validateOwnership} 이 세 문단에 걸쳐 피하는
         * 것이다. 코드가 같아야 두 모드의 응답도 같아지고, 계약 스펙에 새 코드가 실리지 않는다.
         */
        @Test
        @DisplayName("없는 키·남의 키·ENFORCE 거부가 전부 같은 코드다")
        void 열거_오라클_없음() {
            given(apiKeyRepository.findByApiKeyAndIsActiveTrue("없는키")).willReturn(Optional.empty());
            keyExists();
            logOnly();

            ErrorType 없는키 = errorTypeOf(() -> apiKeyService.findStrictlyOwnedByApiKey("없는키", ATTACKER));
            ErrorType 남의키 = errorTypeOf(() -> apiKeyService.findStrictlyOwnedByApiKey(KEY, ATTACKER));

            enforce();
            ErrorType 엄격거부 = errorTypeOf(() -> apiKeyService.findStrictlyOwnedByApiKey(KEY, ATTACKER));

            assertThat(남의키).isEqualTo(없는키).isEqualTo(엄격거부).isEqualTo(ErrorType.API_KEY_NOT_FOUND);
        }

        @Test
        @DisplayName("X-Account-Id 부재도 여전히 막는다 — UG-277 가드를 우회하지 않는다")
        void 계정_부재도_막는다() {
            logOnly();

            assertThatThrownBy(() -> apiKeyService.findStrictlyOwnedByApiKey(KEY, null))
                    .isInstanceOf(CustomGateException.class);

            verify(apiKeyRepository, never()).findByApiKeyAndIsActiveTrue(any());
        }

        private ErrorType errorTypeOf(Runnable call) {
            try {
                call.run();
                throw new AssertionError("예외가 발생하지 않았다");
            } catch (CustomGateException e) {
                return e.getErrorType();
            }
        }
    }

    @Nested
    @DisplayName("findByApiKey(CallerType, ...) — 데모·인증 공유 UseCase")
    class Shared {

        @Test
        @DisplayName("DEMO 는 accountId 가 달라도 통과한다")
        void 데모는_검증하지_않는다() {
            keyExists();

            // 데모(/api/v1/demo/**)는 4개 환경 게이트웨이 전부에서 AuthenticationFilter 가 없다.
            // QR 로 접근한 사용자에게는 계정이 없어 대조할 accountId 자체가 존재하지 않는다.
            // 데모가 넘기는 0L 은 '계정 없음' 을 뜻하는 자리표지, 실제 계정이 아니다.
            assertThat(apiKeyService.findByApiKey(CallerType.DEMO, KEY, 0L)).isSameAs(apiKey);
        }

        @Test
        @DisplayName("API 는 타 계정을 거부한다")
        void 인증경로는_검증한다() {
            keyExists();

            assertThatThrownBy(() -> apiKeyService.findByApiKey(CallerType.API, KEY, ATTACKER))
                    .isInstanceOf(CustomGateException.class);
        }

        @Test
        @DisplayName("API 는 소유자를 통과시킨다")
        void 인증경로_소유자_통과() {
            keyExists();

            assertThat(apiKeyService.findByApiKey(CallerType.API, KEY, OWNER)).isSameAs(apiKey);
        }
    }

    @Nested
    @DisplayName("mode 속성")
    class Mode {

        @Test
        @DisplayName("LOG_ONLY 는 불일치를 통과시킨다 — 재배포 없는 되돌림 수단")
        void 로그만() {
            keyExists();
            logOnly();

            assertThatCode(() -> apiKeyService.findOwnedByApiKey(KEY, ATTACKER))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("LOG_ONLY 여도 없는 키는 여전히 거부한다")
        void 로그만이어도_없는키는_거부() {
            given(apiKeyRepository.findByApiKeyAndIsActiveTrue("없는키")).willReturn(Optional.empty());
            logOnly();

            // LOG_ONLY 는 '소유 검증' 만 끄는 스위치다. 키 존재 여부까지 통과시키면
            // 인증 자체가 사라진다.
            assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey("없는키", OWNER))
                    .isInstanceOf(CustomGateException.class);
        }

        @Test
        @DisplayName("알 수 없는 값은 ENFORCE 로 떨어진다 — 되돌리려다 장애 내지 않는다")
        void 잘못된_값은_막는쪽으로() {
            keyExists();

            // @RefreshScope 빈이라 refresh 이후 지연 생성된다. enum 으로 직접 바인딩했다면
            // 오타 하나로 이 빈을 주입받는 30여 개 컴포넌트가 전부 500 이 됐다.
            // 문자열로 받아 해석 실패 시 '막는 쪽' 으로 떨어뜨린다 — 보안 통제의 안전한 기본값.
            for (String 잘못된값 : new String[] {"OFF", "LOGONLY", "", "  "}) {
                mode(잘못된값);
                assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey(KEY, ATTACKER))
                        .as("mode=%s 일 때 검증이 꺼지면 안 된다", 잘못된값)
                        .isInstanceOf(CustomGateException.class);
            }
        }

        @Test
        @DisplayName("대소문자·공백은 관대하게 받는다")
        void 관대한_파싱() {
            keyExists();

            for (String 유효값 : new String[] {"log_only", " LOG_ONLY ", "Log_Only"}) {
                mode(유효값);
                assertThatCode(() -> apiKeyService.findOwnedByApiKey(KEY, ATTACKER))
                        .as("mode=%s 는 LOG_ONLY 로 해석돼야 한다", 유효값)
                        .doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("기본값은 ENFORCE 다")
        void 기본값_ENFORCE() throws Exception {
            var field = ApiKeyService.class.getDeclaredField("modeProperty");
            String defaultValue = field.getAnnotation(org.springframework.beans.factory.annotation.Value.class)
                    .value();

            // 속성을 안 넣은 환경(온프레미스 포함)에서 조용히 검증이 꺼지면 안 된다.
            assertThat(defaultValue).isEqualTo("${gate.security.api-key-ownership.mode:ENFORCE}");
        }
    }

    /**
     * UG-277: 인증 경로에 {@code X-Account-Id} 가 없으면 400 으로 끝낸다.
     *
     * <p>없으면 {@code null} 이 그대로 흘러 매칭 UseCase 의
     * {@code input.accountId().toString()} 에서 NPE(500)가 난다 — 그것도 매칭 이력을 저장한
     * 뒤라 사유 없는 실패 행이 남는다.
     *
     * <p><b>ENFORCE 만 보면 이 가드가 있으나 없으나 초록이다.</b> 아래 소유 검증이 {@code null}
     * 을 불일치로 보고 먼저 거부하기 때문이다. 그래서 {@code LOG_ONLY} 케이스가 이 묶음의
     * 본문이다 — 가드를 지우면 거기서만 깨진다.
     */
    @Nested
    @DisplayName("UG-277: X-Account-Id 부재")
    class MissingAccountId {

        /**
         * 키가 실재한다는 사실만 세팅한다. 가드가 <b>조회에 닿기 전에</b> 끝내므로 이 스텁은
         * 쓰이지 않는 것이 정상이다 — strict stubbing 에 걸리지 않도록 lenient 로 둔다.
         * 가드를 지우면 그때 이 스텁이 쓰이면서 LOG_ONLY 를 타고 정상 반환하게 되고,
         * 그것이 테스트를 깨뜨린다.
         */
        private void 키는_있다() {
            lenient().when(apiKeyRepository.findByApiKeyAndIsActiveTrue(KEY))
                    .thenReturn(Optional.of(apiKey));
        }

        /**
         * <b>키가 실재해야 이 테스트가 의미를 가진다.</b> 처음에는 {@code keyExists()} 를 빼고
         * 썼는데, 그러면 가드를 지워도 조회가 빈 결과를 내며 같은 {@code API_KEY_NOT_FOUND} 를
         * 던져 초록이었다 — 변이 심기로 확인했다. 키가 있어야 가드 없는 코드가 LOG_ONLY 를 타고
         * <b>정상 반환</b>하고, 그제야 이 단언이 깨진다.
         */
        @Test
        @DisplayName("LOG_ONLY 여도 accountId 가 없으면 거부한다 — 이 가드의 본문")
        void 로그만이어도_거부() {
            키는_있다();
            logOnly();

            assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey(KEY, null))
                    .isInstanceOf(CustomGateException.class)
                    .satisfies(e -> assertThat(((CustomGateException) e).getErrorType())
                            .isEqualTo(ErrorType.API_KEY_NOT_FOUND));
        }

        /**
         * 가드가 <b>조회·소유검증보다 앞</b>이라는 것까지 못박는다.
         *
         * <p>순서를 뒤로 미뤄도 호출자가 받는 예외는 같아서 위 테스트로는 잡히지 않는다. 하지만
         * 뒤에 두면 {@link ApiKeyService#validateOwnership} 이 {@code accountId=null} 을 소유
         * 불일치로 보고 WARN 을 남긴다 — UG-281 이 관측하려는 "정상인데 불일치로 호출하던 기존
         * 고객" 집계에 헤더 누락이 섞여 들어간다.
         */
        @Test
        @DisplayName("키 조회에 닿기 전에 끝낸다 — 소유 불일치 로그를 오염시키지 않는다")
        void 조회_전에_끝낸다() {
            logOnly();

            assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey(KEY, null))
                    .isInstanceOf(CustomGateException.class);

            verify(apiKeyRepository, never()).findByApiKeyAndIsActiveTrue(any());
        }

        /**
         * 기본 모드에서의 응답이 바뀌지 않았음을 고정한다.
         *
         * <p>이 테스트는 <b>가드를 지워도 초록이다</b> — 지우면 소유 검증이 {@code null} 을
         * 불일치로 보고 같은 코드를 던지기 때문이다. 그게 요점이다. 이 변경이 ENFORCE 트래픽의
         * 계약을 건드리지 않는다는 사실 자체를 기록해 두는 것이고, 나중에 여기서 새 오류 코드를
         * 내보내려는 변경이 오면 그때 깨진다.
         */
        @Test
        @DisplayName("ENFORCE 에서의 응답은 그대로다 — 계약이 바뀌지 않았다")
        void ENFORCE_응답_불변() {
            키는_있다();
            enforce();

            assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey(KEY, null))
                    .isInstanceOf(CustomGateException.class)
                    .satisfies(e -> assertThat(((CustomGateException) e).getErrorType())
                            .isEqualTo(ErrorType.API_KEY_NOT_FOUND));
        }

        @Test
        @DisplayName("데모·인증 공유 UseCase 도 인증 경로면 같이 막힌다")
        void 공유_UseCase_인증경로도_막는다() {
            키는_있다();
            logOnly();

            // 매칭 UseCase 여섯 개가 타는 경로다. 여기가 뚫리면
            // input.accountId().toString() 에서 NPE 가 난다.
            assertThatThrownBy(() ->
                    apiKeyService.findByApiKey(CallerType.API, KEY, null))
                    .isInstanceOf(CustomGateException.class);
        }

        /**
         * 데모는 대조할 accountId 자체가 없다. 여기까지 막으면 QR 데모가 통째로 죽는다.
         *
         * <p>실제 데모 DTO 는 {@code 0L} 을 넘기지만, 가드를 {@code findByApiKey} 진입부로
         * 올리는 변경이 이 테스트를 깨뜨리도록 {@code null} 로 둔다.
         */
        @Test
        @DisplayName("데모 경로는 accountId 가 없어도 통과한다")
        void 데모는_막지_않는다() {
            keyExists();

            assertThat(apiKeyService.findByApiKey(CallerType.DEMO, KEY, null)).isSameAs(apiKey);
        }
    }
}
