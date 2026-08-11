package ai.univs.gate.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.shared.web.enums.ErrorType;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ai.univs.gate.support.message.MessageService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
    @DisplayName("하위 서비스가 자기 오류라고 말하면 HTTP 400 이어도 ERROR 다")
    void 하위가_자기오류라_말하면_ERROR() {
        // 반박 리뷰가 찾은 경로. face·palm 은 자기 쪽 5xx 를
        // CustomFaceException(INTERNAL_SERVER_ERROR) 로 감싼 뒤 @ResponseStatus(BAD_REQUEST) 로
        // 내려보내며, 그 과정에서 로그를 아예 남기지 않는다. 즉 ML 매처가 전면 장애여도 gate 에는
        // 400 으로 도착한다. HTTP 상태만 보고 WARN 으로 내리면 어느 서비스에서도 ERROR 가 한 줄도
        // 남지 않는다 — "4xx = 클라이언트 잘못" 이 이 코드베이스에서 성립하지 않는 지점이다.
        handler.CustomFeignException(
                new CustomFeignException("SWAGGER-005", "INTERNAL_SERVER_ERROR", "matcher down"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("INTERNAL_SERVER_ERROR");
    }

    @Test
    @DisplayName("검증 실패 로그에 어느 필드인지 남는다")
    void 검증_실패는_필드명을_남긴다() {
        // i18n 해석 결과만 남기면 "MUST NOT BE BLANK" 처럼 필드를 알 수 없는 줄이 된다 —
        // 메시지 키 없이 @NotBlank 만 쓴 DTO 가 여럿 있다. 거부값은 일부러 빼놓는다
        // (비밀번호·descriptor 가 들어올 수 있고 LoggingAspect 의 마스킹이 이 경로엔 없다).
        BeanPropertyBindingResult binding =
                new BeanPropertyBindingResult(new Object(), "webhookConfigRequestDTO");
        binding.addError(new FieldError("webhookConfigRequestDTO", "webhookUrl", "must not be blank"));

        handler.handleMethodArgumentNotValidException(
                new MethodArgumentNotValidException((MethodParameter) null, binding));

        String message = onlyEvent().getFormattedMessage();
        assertThat(message).contains("webhookUrl");
        assertThat(message)
                .as("거부값은 남기지 않는다 — 민감 정보가 들어올 수 있다")
                .doesNotContain("rejected value");
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

    @Test
    @DisplayName("우리 쪽 문제로 분류된 ErrorType 은 ERROR 로 올라간다")
    void 서버_오류로_분류되면_ERROR() {
        // 전수 분류 자체는 ErrorTypeClassificationTest 가 못박는다 (UG-298). 여기서는 그 분류가
        // 실제로 로그 수준을 바꾸는지만 본다 — 목록과 동작이 따로 놀면 둘 다 무의미해진다.
        handler.handleBusinessException(new CustomGateException(ErrorType.SETTINGS_NOT_FOUND));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy())
                .as("존재하는 프로젝트에 설정 행이 없다는 것은 데이터가 깨졌다는 뜻이다 (UG-298)")
                .isNotNull();
        assertThat(event.getFormattedMessage()).contains("PJ-106");
    }
}
