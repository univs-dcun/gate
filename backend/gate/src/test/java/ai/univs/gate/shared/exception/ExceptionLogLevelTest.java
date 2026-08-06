package ai.univs.gate.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.shared.web.enums.ErrorType;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ai.univs.gate.support.message.MessageService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 예외 로깅 수준 계약 (UG-290, UG-291).
 *
 * <p>예전에는 모든 예외가 {@code log.error} + 스택트레이스였다. 잘못된 API 키로 한 번 호출하면
 * ERROR 두 줄에 90여 줄 스택트레이스가 붙었다 — 원인이 클라이언트 입력인데 서버 오류로 기록되니,
 * 에러 대시보드를 붙이면 오탐이 쌓여 진짜 5xx 가 묻힌다.
 *
 * <p>로그 수준은 아무도 보지 않으면 조용히 되돌아간다. 컴파일러도 기존 테스트도 잡지 않는다.
 * 그래서 실제 appender 를 붙여 확인한다.
 */
@DisplayName("예외 로깅 수준 (UG-290)")
class ExceptionLogLevelTest {

    private GlobalExceptionHandler handler;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        MessageService messageService = Mockito.mock(MessageService.class);
        Mockito.lenient().when(messageService.getMessage(Mockito.any(ErrorType.class)))
                .thenReturn("메시지");
        handler = new GlobalExceptionHandler(messageService);

        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/match");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        RequestContextHolder.resetRequestAttributes();
    }

    private ILoggingEvent onlyEvent() {
        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    @Test
    @DisplayName("4xx 는 WARN 이고 스택트레이스를 남기지 않는다")
    void 클라이언트_오류는_WARN() {
        handler.handleBusinessException(new CustomGateException(ErrorType.API_KEY_NOT_FOUND));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy())
                .as("원인이 클라이언트 입력인데 우리 호출 스택 90줄을 남길 이유가 없다")
                .isNull();
        assertThat(event.getFormattedMessage())
                .contains("PJ-105")
                .contains("API_KEY_NOT_FOUND");
    }

    @Test
    @DisplayName("5xx 는 ERROR 이고 스택트레이스를 남긴다")
    void 서버_오류는_ERROR() {
        handler.handleGlobalException(new IllegalStateException("boom"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy())
                .as("우리 쪽 문제이므로 스택트레이스가 유일한 단서다")
                .isNotNull();
        assertThat(event.getFormattedMessage()).contains("PJ-005");
    }

    @Test
    @DisplayName("하위 서비스 실패는 ERROR 로 남고 상태 코드·operation 을 함께 적는다")
    void 하위서비스_실패는_ERROR() {
        handler.handleRemoteCallException(new RemoteCallException(503, "face.identify", null));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage())
                .contains("face.identify")
                .contains("503");
    }

    @Test
    @DisplayName("하위 서비스가 돌려준 4xx 는 WARN 이다")
    void 하위서비스_4xx는_WARN() {
        handler.CustomFeignException(
                new CustomFeignException("ML-101", "FACE_NOT_FOUND", "no face"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getFormattedMessage()).contains("ML-101").contains("FACE_NOT_FOUND");
    }

    @Test
    @DisplayName("어느 요청이었는지 함께 남긴다 — 컨트롤러 진입 전 예외도 추적 가능해야 한다")
    void 요청_정보를_남긴다() {
        // UG-291: 이 핸들러는 예외는 알지만 요청은 몰랐고, LoggingAspect 는 반대였다.
        // 바인딩·검증 예외는 LoggingAspect 를 아예 거치지 않으므로 여기에 경로가 없으면
        // 어느 엔드포인트에서 난 오류인지 알 방법이 없다.
        handler.handleBusinessException(new CustomGateException(ErrorType.PROJECT_NOT_FOUND));

        assertThat(onlyEvent().getFormattedMessage()).contains("GET /api/v1/match");
    }

    @Test
    @DisplayName("요청 컨텍스트가 없어도 터지지 않는다")
    void 요청_컨텍스트가_없어도_동작한다() {
        // @Async 스레드나 스케줄러에서 불릴 수 있다. 로깅이 예외를 내면 원래 오류가 가려진다.
        RequestContextHolder.resetRequestAttributes();

        handler.handleBusinessException(new CustomGateException(ErrorType.INVALID_INPUT));

        assertThat(onlyEvent().getFormattedMessage()).contains("요청 정보 없음");
    }

    @ParameterizedTest
    @EnumSource(ErrorType.class)
    @DisplayName("모든 ErrorType 에 status 가 채워져 있다 — 판정 기준이 비면 분류가 무너진다")
    void 모든_ErrorType에_상태코드가_있다(ErrorType errorType) {
        // getStatus() 는 이 변경 전까지 프로덕션 코드에서 한 번도 읽히지 않는 죽은 코드였다.
        // 값이 방치돼 있었다는 뜻이므로, 살려 쓰기로 한 이상 전수로 못박는다.
        assertThat(errorType.getStatus()).isNotNull();
        assertThat(errorType.getStatus().isError())
                .as("%s 의 상태 코드가 오류 범위가 아니다", errorType.name())
                .isTrue();
    }

    @Test
    @DisplayName("5xx 로 분류되는 ErrorType 은 INTERNAL_SERVER_ERROR 하나뿐이다")
    void 서버_오류_분류는_하나뿐이다() {
        // 새 ErrorType 에 5xx 를 달면 그 오류가 ERROR + 스택트레이스로 승격된다. 의도한 것이라면
        // 이 테스트를 함께 고치면 되고, 실수라면 여기서 걸린다.
        assertThat(Arrays.stream(ErrorType.values())
                .filter(e -> e.getStatus().is5xxServerError())
                .toList())
                .containsExactly(ErrorType.INTERNAL_SERVER_ERROR);
    }
}
