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
    }

    private void keyExists() {
        given(apiKeyRepository.findActiveByApiKeyWithLiveProject(KEY)).willReturn(Optional.of(apiKey));
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
            given(apiKeyRepository.findActiveByApiKeyWithLiveProject("없는키")).willReturn(Optional.empty());
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
    @DisplayName("UG-277: X-Account-Id 부재")
    class MissingAccountId {

        /**
         * 키가 실재한다는 사실만 세팅한다. 가드가 <b>조회에 닿기 전에</b> 끝내므로 이 스텁은
         * 쓰이지 않는 것이 정상이다 — strict stubbing 에 걸리지 않도록 lenient 로 둔다.
         */
        private void 키는_있다() {
            lenient().when(apiKeyRepository.findActiveByApiKeyWithLiveProject(KEY))
                    .thenReturn(Optional.of(apiKey));
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

            assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey(KEY, null))
                    .isInstanceOf(CustomGateException.class);

            verify(apiKeyRepository, never()).findActiveByApiKeyWithLiveProject(any());
        }

        /**
         * 기본 모드에서의 응답이 바뀌지 않았음을 고정한다.
         *
         * <p>이 테스트는 <b>가드를 지워도 초록이다</b> — 지우면 소유 검증이 {@code null} 을
         * 불일치로 보고 같은 코드를 던지기 때문이다. 그게 요점이다. 이 가드가 응답 계약을
         * 건드리지 않는다는 사실 자체를 기록해 두는 것이고, 나중에 여기서 새 오류 코드를
         * 내보내려는 변경이 오면 그때 깨진다.
         */
        @Test
        @DisplayName("응답 코드는 키 없음과 같다 — 계약이 바뀌지 않았다")
        void 응답_불변() {
            키는_있다();

            assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey(KEY, null))
                    .isInstanceOf(CustomGateException.class)
                    .satisfies(e -> assertThat(((CustomGateException) e).getErrorType())
                            .isEqualTo(ErrorType.API_KEY_NOT_FOUND));
        }

        @Test
        @DisplayName("데모·인증 공유 UseCase 도 인증 경로면 같이 막힌다")
        void 공유_UseCase_인증경로도_막는다() {
            키는_있다();

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
