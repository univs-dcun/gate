package ai.univs.gate.support.api_key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.shared.web.enums.ErrorType;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * UG-288: 삭제된 프로젝트의 API 키는 어느 경로로도 통하지 않는다.
 *
 * <p>{@code DeleteProjectUseCase} 가 삭제 시 키를 비활성화하지만, 그것만으로는 부족하다. 그 경로를
 * 타지 않고 {@code is_deleted} 가 켜진 행(직접 DB 수정, 배치, 앞으로 생길 다른 삭제 경로)이 있으면
 * 키가 그대로 유효해진다. 그래서 <b>조회 시점에도</b> 막는다.
 *
 * <p>검사를 {@code findByApiKeyUnverified} 한 곳에만 둔 이유는 세 조회 메서드가 전부 그것을 거치기
 * 때문이다. 이 테스트가 세 진입점을 모두 두드리는 것은 그 구조가 유지되는지 확인하기 위해서다 —
 * 누군가 {@code findOwnedByApiKey} 를 리포지토리 직행으로 바꾸면 여기서 걸린다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-288: 삭제된 프로젝트의 API 키")
class ApiKeyDeletedProjectTest {

    private static final long OWNER = 100L;
    private static final String KEY = "univs_live_abcdefghijklmnop";

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(apiKeyService, "modeProperty", "ENFORCE");
    }

    private void givenKeyOfProject(boolean projectDeleted) {
        Project project = Project.builder().accountId(OWNER).isDeleted(projectDeleted).build();
        ReflectionTestUtils.setField(project, "id", 42L);

        ApiKey apiKey = ApiKey.builder().project(project).apiKey(KEY).isActive(true).build();
        given(apiKeyRepository.findByApiKeyAndIsActiveTrue(KEY)).willReturn(Optional.of(apiKey));
    }

    private ErrorType errorTypeOf(Runnable call) {
        try {
            call.run();
            throw new AssertionError("예외가 발생하지 않았다");
        } catch (CustomGateException e) {
            return e.getErrorType();
        }
    }

    @Test
    @DisplayName("인증 경로 — 소유자가 불러도 거부한다")
    void 인증경로에서_거부() {
        givenKeyOfProject(true);

        assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey(KEY, OWNER))
                .isInstanceOf(CustomGateException.class);
    }

    @Test
    @DisplayName("데모 경로도 예외가 아니다")
    void 데모경로에서도_거부() {
        // 소유 검증은 데모를 면제한다 — 대조할 accountId 가 없기 때문이다. 삭제 검사는 다르다.
        // "데모 키가 공개돼도 되는" 근거는 그 키로 할 수 있는 일이 데모 범위에 머문다는 것인데,
        // 삭제된 프로젝트에는 머물 범위 자체가 없다. 여기서 면제하면 UG-286 이 지적한 무인증
        // 목록·등록 경로가 삭제 후에도 살아 있게 된다.
        givenKeyOfProject(true);

        assertThatThrownBy(() -> apiKeyService.findByApiKey(CallerType.DEMO, KEY, 0L))
                .isInstanceOf(CustomGateException.class);
    }

    @Test
    @DisplayName("공유 UseCase 의 API 경로도 거부한다")
    void 공유_UseCase_API경로에서도_거부() {
        givenKeyOfProject(true);

        assertThatThrownBy(() -> apiKeyService.findByApiKey(CallerType.API, KEY, OWNER))
                .isInstanceOf(CustomGateException.class);
    }

    @Test
    @DisplayName("LOG_ONLY 여도 삭제된 프로젝트는 거부한다")
    void LOG_ONLY_는_이_검사를_끄지_않는다() {
        // LOG_ONLY 는 UG-281 의 '소유 검증' 만 되돌리는 스위치다. 여기까지 함께 꺼지면
        // 되돌림 스위치 하나가 서로 다른 두 통제를 동시에 무력화하게 된다.
        ReflectionTestUtils.setField(apiKeyService, "modeProperty", "LOG_ONLY");
        givenKeyOfProject(true);

        assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey(KEY, OWNER))
                .isInstanceOf(CustomGateException.class);
    }

    @Test
    @DisplayName("없는 키와 같은 오류 코드다 — 열거 오라클 방지")
    void 열거_오라클_없음() {
        given(apiKeyRepository.findByApiKeyAndIsActiveTrue("없는키")).willReturn(Optional.empty());
        givenKeyOfProject(true);

        ErrorType 없는키 = errorTypeOf(() -> apiKeyService.findOwnedByApiKey("없는키", OWNER));
        ErrorType 삭제된프로젝트의키 = errorTypeOf(() -> apiKeyService.findOwnedByApiKey(KEY, OWNER));

        // 코드가 갈리면 "이 키는 실재했다" 를 알려주는 셈이 된다. UG-281·UG-250 과 같은 논리.
        assertThat(삭제된프로젝트의키).isEqualTo(없는키).isEqualTo(ErrorType.API_KEY_NOT_FOUND);
    }

    @Test
    @DisplayName("거부 로그에 API 키 원문을 남기지 않는다")
    void 로그에_키_원문이_없다() {
        // 반박 리뷰가 찾은 생존 변이. ApiKeyMasker.mask 를 빼고 원문을 찍어도 아무 테스트도
        // 깨지지 않았다. API 키는 특징점 등록·매칭 전 기능의 인증 수단이라, 로그 열람 권한만으로
        // 남의 생체 API 를 호출할 수 있게 된다. 온프레미스에서는 로그 묶음이 그대로 밖으로 나간다.
        // (UG-274 가 ApiKeyMasker 를 만든 이유와 같다)
        Logger logger = (Logger) LoggerFactory.getLogger(ApiKeyService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            givenKeyOfProject(true);

            assertThatThrownBy(() -> apiKeyService.findOwnedByApiKey(KEY, OWNER))
                    .isInstanceOf(CustomGateException.class);

            assertThat(appender.list)
                    .as("조사 단서가 남아야 하므로 로그 자체는 있어야 한다")
                    .isNotEmpty();
            assertThat(appender.list)
                    .noneMatch(event -> event.getFormattedMessage().contains(KEY));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("살아 있는 프로젝트는 그대로 통과한다")
    void 살아있는_프로젝트는_통과() {
        // 대조군. 이게 없으면 "전부 거부" 로 바뀌어도 위 테스트들은 초록이다.
        givenKeyOfProject(false);

        assertThat(apiKeyService.findOwnedByApiKey(KEY, OWNER)).isNotNull();
        assertThat(apiKeyService.findByApiKey(CallerType.DEMO, KEY, 0L)).isNotNull();
        assertThat(apiKeyService.findByApiKeyUnverified(KEY)).isNotNull();
    }
}
