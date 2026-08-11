package ai.univs.match.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.match.shared.locale.MessageService;
import ai.univs.match.shared.web.enums.ErrorType;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 예외 로깅 수준 계약 (UG-299).
 *
 * <p>UG-290 이 gate 만 정리하고 이 서비스는 그대로 뒀다. 모든 예외가 {@code log.error} +
 * 스택트레이스로 남고 있었고, CustomFaceMatcherException 핸들러에는 <b>로그 호출이 아예 없었다</b>.
 *
 * <p>로그 수준은 아무도 보지 않으면 조용히 되돌아간다. 컴파일러도 기존 테스트도 잡지 않는다.
 * 그래서 실제 appender 를 붙여 확인한다.
 */
@DisplayName("예외 로깅 수준 (UG-299)")
class ExceptionLogLevelTest {

    private GlobalExceptionHandler handler;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        MessageService messageService = Mockito.mock(MessageService.class);
        Mockito.lenient().when(messageService.getMessage(Mockito.any(ErrorType.class)))
                .thenReturn("메시지");
        Mockito.lenient().when(messageService.getMessage(Mockito.anyString()))
                .thenReturn("메시지");
        handler = new GlobalExceptionHandler(messageService);

        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/match/identify");
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
        handler.handleFaceMatcherCustomException(
                new CustomFaceMatcherException(ErrorType.NOT_SUPPORTED_VERSION));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy())
                .as("지원하지 않는 descriptor 버전은 클라이언트 입력 문제다")
                .isNull();
        assertThat(event.getFormattedMessage())
                .contains("MATCH-002")
                .contains("NOT_SUPPORTED_VERSION");
    }

    @Test
    @DisplayName("자기 5xx 는 ERROR 로 남는다")
    void 자기_장애는_ERROR() {
        handler.handleFaceMatcherCustomException(
                new CustomFaceMatcherException(ErrorType.INTERNAL_SERVER_ERROR));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();
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
        assertThat(event.getFormattedMessage()).contains("SWAGGER-005");
    }

    @Test
    @DisplayName("검증 실패 로그에 어느 필드인지 남는다")
    void 검증_실패는_필드명을_남긴다() {
        // i18n 해석 결과만 남기면 "MUST NOT BE BLANK" 처럼 필드를 알 수 없는 줄이 된다.
        // 거부값은 일부러 빼놓는다 — descriptor·이미지 바이트가 들어올 수 있다.
        BeanPropertyBindingResult binding =
                new BeanPropertyBindingResult(new Object(), "requestDTO");
        binding.addError(new FieldError("requestDTO", "descriptor", "must not be blank"));

        handler.handleMethodArgumentNotValidException(
                new MethodArgumentNotValidException((MethodParameter) null, binding));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("descriptor");
        assertThat(event.getFormattedMessage())
                .as("거부값은 남기지 않는다 — 민감 정보가 들어올 수 있다")
                .doesNotContain("rejected value");
    }

    @Test
    @DisplayName("어느 요청이었는지 함께 남긴다")
    void 요청_정보를_남긴다() {
        handler.handleGlobalException(new IllegalStateException("boom"));

        assertThat(onlyEvent().getFormattedMessage()).contains("POST /api/v1/match/identify");
    }

    @Test
    @DisplayName("요청 컨텍스트가 없어도 터지지 않는다")
    void 요청_컨텍스트가_없어도_동작한다() {
        // @Async 스레드나 스케줄러에서 불릴 수 있다. 로깅이 예외를 내면 원래 오류가 가려진다.
        RequestContextHolder.resetRequestAttributes();

        handler.handleGlobalException(new IllegalStateException("boom"));

        assertThat(onlyEvent().getFormattedMessage()).contains("요청 정보 없음");
    }

    @ParameterizedTest
    @EnumSource(ErrorType.class)
    @DisplayName("모든 ErrorType 에 status 가 채워져 있다 — 판정 기준이 비면 분류가 무너진다")
    void 모든_ErrorType에_상태코드가_있다(ErrorType errorType) {
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
