package ai.univs.gate.modules.api_key.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.project.ProjectService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * UG-298: 활성 API 키가 없는 프로젝트는 조용히 지나가면 안 된다.
 *
 * <p>{@code API_KEY_NOT_FOUND} 는 4xx 다. 없는 키·남의 키·삭제된 프로젝트의 키를 <b>같은
 * 코드로</b> 막아 열거 오라클을 피해야 하기 때문이고, 그건 옳다.
 *
 * <p>문제는 같은 코드가 <b>데이터 정합성 문제</b>에도 쓰인다는 것이다. 이 두 UseCase 는
 * {@code validateOwnership} 을 먼저 거치므로, 여기까지 왔다는 것은 "소유가 확인된, 삭제되지
 * 않은 프로젝트에 활성 키가 없다" 는 뜻이다 — {@code SETTINGS_NOT_FOUND} 와 완전히 같은
 * 논리이고, 그쪽은 UG-298 에서 5xx 로 올렸다.
 *
 * <p>이쪽은 올릴 수 없으니 대신 그 자리에서 직접 ERROR 를 남긴다. 반박 리뷰가 짚기 전까지
 * <b>두 자리 모두 아무것도 남기지 않았다</b> — WARN 한 줄로 지나갔고, 4xx 로 두는 대가를
 * 다른 섞인 코드들과 달리 치르지 않고 있었다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-298: 활성 API 키 부재는 ERROR 로 남는다")
class ApiKeyDataIntegrityLogTest {

    private static final long ACCOUNT = 7L;
    private static final long PROJECT = 42L;

    @Mock
    private ProjectService projectService;

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private GetApiKeyUseCase getApiKeyUseCase;

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(GetApiKeyUseCase.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("소유가 확인된 프로젝트에 활성 키가 없으면 ERROR + projectId 를 남긴다")
    void 활성_키_부재는_ERROR() {
        given(projectService.validateOwnership(PROJECT, ACCOUNT))
                .willReturn(Project.builder().accountId(ACCOUNT).isDeleted(false).build());
        given(apiKeyRepository.findLatestActiveByProjectId(PROJECT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> getApiKeyUseCase.execute(ACCOUNT, PROJECT))
                .isInstanceOf(CustomGateException.class)
                .extracting(e -> ((CustomGateException) e).getErrorType())
                .as("응답 코드는 열거 오라클 방지를 위해 그대로 둔다")
                .isEqualTo(ErrorType.API_KEY_NOT_FOUND);

        assertThat(appender.list)
                .as("4xx 라 핸들러는 WARN 만 남긴다. 여기서 안 남기면 데이터가 깨진 사실이 "
                        + "어디에도 기록되지 않는다")
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(event.getFormattedMessage()).contains(String.valueOf(PROJECT));
                });
    }
}
