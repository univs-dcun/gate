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
import static org.mockito.BDDMockito.given;

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

    private void enforce() {
        ReflectionTestUtils.setField(apiKeyService, "mode", ApiKeyService.OwnershipMode.ENFORCE);
    }

    private void logOnly() {
        ReflectionTestUtils.setField(apiKeyService, "mode", ApiKeyService.OwnershipMode.LOG_ONLY);
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
        @DisplayName("기본값은 ENFORCE 다")
        void 기본값_ENFORCE() throws Exception {
            var field = ApiKeyService.class.getDeclaredField("mode");
            String defaultValue = field.getAnnotation(org.springframework.beans.factory.annotation.Value.class)
                    .value();

            // 속성을 안 넣은 환경(온프레미스 포함)에서 조용히 검증이 꺼지면 안 된다.
            assertThat(defaultValue).isEqualTo("${gate.security.api-key-ownership.mode:ENFORCE}");
        }
    }
}
