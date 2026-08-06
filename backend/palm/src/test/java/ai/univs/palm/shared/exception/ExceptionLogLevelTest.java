package ai.univs.palm.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.palm.shared.locale.MessageService;
import ai.univs.palm.shared.web.enums.ErrorType;
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
 * 스택트레이스로 남고 있었고, CustomPalmException 핸들러에는 <b>로그 호출이 아예 없었다</b>.
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

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/palm/verify");
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
        handler.handlePalmCustomException(new CustomPalmException(ErrorType.NOT_PALM_IMAGE));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy())
                .as("손바닥이 잡히지 않은 사진은 클라이언트 입력 문제다. 우리 호출 스택 90줄을 남길 이유가 없다")
                .isNull();
        assertThat(event.getFormattedMessage())
                .contains("PALM-001")
                .contains("NOT_PALM_IMAGE");
    }

    @Test
    @DisplayName("ML 모듈이 자기 5xx 를 낸 경우 — 이 서비스가 그것을 기록한다")
    void 자기_장애를_기록한다() {
        // UG-299 의 핵심. 이 핸들러에는 로그 호출이 아예 없었다. CommonErrorDecoder 가 ML 모듈의
        // 3xx/5xx 를 CustomPalmException(INTERNAL_SERVER_ERROR) 로 감싸 던지므로, ML 매처가
        // 전면 장애여도 palm 로그에는 한 줄도 남지 않았다. gate 에는 @ResponseStatus(BAD_REQUEST)
        // 때문에 400 으로 도착했다 — 어느 서비스에서도 ERROR 가 남지 않는 구간이었다.
        handler.handlePalmCustomException(new CustomPalmException(ErrorType.INTERNAL_SERVER_ERROR));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();
    }

    @Test
    @DisplayName("이미지 거부도 흔적을 남긴다")
    void 이미지_거부도_기록한다() {
        handler.handleInvalidPalmImageException(
                new InvalidPalmImageException(ErrorType.NO_DOUBLE_SIMILARITY));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("NO_DOUBLE_SIMILARITY");
    }

    @Test
    @DisplayName("하위 모듈이 돌려준 4xx 는 WARN 이다")
    void 하위_4xx는_WARN() {
        handler.CustomFeignException(
                new CustomFeignException("ML-101", "PALM_NOT_FOUND", "no face"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getFormattedMessage()).contains("ML-101").contains("PALM_NOT_FOUND");
    }

    @Test
    @DisplayName("하위 모듈이 자기 오류라고 말하면 HTTP 400 이어도 ERROR 다")
    void 하위가_자기오류라_말하면_ERROR() {
        handler.CustomFeignException(
                new CustomFeignException("ML-500", "INTERNAL_SERVER_ERROR", "matcher down"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("INTERNAL_SERVER_ERROR");
    }

    @Test
    @DisplayName("모듈 오류도 같은 기준으로 가른다")
    void 모듈_오류도_같은_기준() {
        handler.handleInvalidPalmModuleException(
                new InvalidPalmModuleException("ML-500", "SERVER_ERROR", "module down"));

        assertThat(onlyEvent().getLevel()).isEqualTo(Level.ERROR);
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

        assertThat(onlyEvent().getFormattedMessage()).contains("POST /api/v1/palm/verify");
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

    @Test
    @DisplayName("하위 모듈이 type 을 안 주면 NPE 없이 WARN 으로 남긴다")
    void type_이_없어도_터지지_않는다() {
        // 반박 리뷰가 찾은 생존 변이. logUpstream 의 type != null 가드를 지워도 전 테스트가
        // 초록이었다. Set.of(...).contains(null) 은 NPE 이므로, 그 가드가 사라지면
        // **예외 핸들러 안에서** NPE 가 난다 — 원래 오류가 통째로 가려진다.
        //
        // 도달 가능한 경로다. CommonErrorDecoder 가 feignErrors.getType() 을 그대로 싣는데,
        // ML 모듈 응답에 type 이 없으면 null 이 들어온다.
        handler.CustomFeignException(new CustomFeignException("ML-1", null, "no type field"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("INTERNAL_ERROR 도 하위 자기 오류로 본다")
    void INTERNAL_ERROR_도_ERROR() {
        // 이 값은 판정 집합에 있는데 어떤 테스트도 지나가지 않았다 — 지워도 초록이었다.
        handler.CustomFeignException(
                new CustomFeignException("ML-500", "INTERNAL_ERROR", "boom"));

        assertThat(onlyEvent().getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    @DisplayName("ML 모듈 호출 실패는 ERROR 한 줄이고 스택트레이스가 없다")
    void 하위_호출_실패는_한_줄이다() {
        // 반박 리뷰의 MAJOR. 처음에는 CommonErrorDecoder 가 한 줄, 이 핸들러가 스택트레이스와
        // 함께 또 한 줄을 남겨 한 사건에 ERROR 가 둘이었다. ML 모듈이 죽어 초당 50 요청이
        // 실패하면 초당 ERROR 100 줄에 스택트레이스 50 개다.
        handler.handleUpstreamCallException(
                new UpstreamCallException(503, "PalmClient#extract()", "Service Unavailable"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy())
                .as("원인은 하위 모듈이고 우리 호출 스택은 매번 같다")
                .isNull();
        assertThat(event.getFormattedMessage())
                .contains("503")
                .contains("PalmClient#extract()")
                .contains("Service Unavailable");
    }

    @Test
    @DisplayName("응답 해석 실패일 때만 스택트레이스를 남긴다")
    void 파싱_실패는_스택트레이스를_남긴다() {
        handler.handleUpstreamCallException(new UpstreamCallException(
                400, "PalmClient#extract()", "응답 해석 실패", new IllegalStateException("bad json")));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy())
                .as("이쪽은 우리 파싱 코드가 원인일 수 있다")
                .isNotNull();
    }
}
