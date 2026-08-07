package ai.univs.face.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.face.shared.feign.CommonErrorDecoder;
import ai.univs.face.shared.locale.MessageService;
import ai.univs.face.shared.web.enums.ErrorType;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * ML 모듈 장애 <b>한 번</b>에 ERROR 가 <b>한 줄</b>인지 (UG-299 반박 리뷰의 MAJOR).
 *
 * <p>UG-299 의 첫 시도는 {@code CommonErrorDecoder} 에서 상태 코드를 로그로 남기는 것이었다.
 * 리뷰가 그것을 실측해 보여 줬다 — 디코더가 한 줄, 그 예외를 받은 핸들러가 스택트레이스와 함께
 * 또 한 줄. ML 매처가 죽어서 초당 50 요청이 실패하면 <b>초당 ERROR 100 줄에 스택트레이스
 * 50 개</b>다. 대시보드는 장애 규모를 두 배로 센다.
 *
 * <p>UG-291 이 gate 에서 없앤 이중 기록과 같은 문제라, 같은 해법을 썼다 — 상태 코드를 예외에
 * 실어 보내고 로그는 핸들러가 한 번만 남긴다.
 *
 * <p>디코더와 핸들러를 <b>둘 다 실제로</b> 통과시켜 센다. 어느 한쪽만 보면 이 성질을 지킬 수 없다.
 */
@DisplayName("UG-299: ML 장애 한 번에 ERROR 한 줄")
class UpstreamFailureLogCountTest {

    private final CommonErrorDecoder decoder = new CommonErrorDecoder();
    private GlobalExceptionHandler handler;
    private ListAppender<ILoggingEvent> appender;
    private Logger root;

    @BeforeEach
    void setUp() {
        MessageService messageService = Mockito.mock(MessageService.class);
        Mockito.lenient().when(messageService.getMessage(Mockito.any(ErrorType.class)))
                .thenReturn("메시지");
        handler = new GlobalExceptionHandler(messageService);

        // 디코더와 핸들러가 서로 다른 로거를 쓰므로 루트에 붙인다 — 어느 쪽이 찍든 잡힌다.
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("POST", "/api/v1/face/extract")));
    }

    @AfterEach
    void tearDown() {
        root.detachAppender(appender);
        RequestContextHolder.resetRequestAttributes();
    }

    private static Response response(int status, String body) {
        return Response.builder()
                .status(status)
                .reason("Service Unavailable")
                .request(Request.create(Request.HttpMethod.POST, "http://ml/extract",
                        Collections.emptyMap(), new byte[0], StandardCharsets.UTF_8, null))
                .headers(Collections.emptyMap())
                .body(body, StandardCharsets.UTF_8)
                .build();
    }

    private List<ILoggingEvent> errors() {
        return appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
    }

    @Test
    @DisplayName("ML 5xx — ERROR 한 줄, 스택트레이스 없음, 상태 코드 포함")
    void ML_5xx_는_한_줄이다() {
        Exception decoded = decoder.decode("FaceClient#extract(MultipartFile)", response(503, ""));
        handler.handleUpstreamCallException((UpstreamCallException) decoded);

        assertThat(errors())
                .as("디코더와 핸들러가 각각 찍으면 두 줄이 된다 — 그게 이 테스트가 막는 것이다")
                .hasSize(1);

        ILoggingEvent event = errors().get(0);
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(event.getFormattedMessage())
                .contains("503")
                .contains("FaceClient#extract(MultipartFile)")
                .contains("POST /api/v1/face/extract");
    }

    @Test
    @DisplayName("ML 3xx 도 상태 코드가 그대로 남는다")
    void ML_3xx_도_상태코드가_남는다() {
        // 리다이렉트는 "죽었다" 와 성격이 다르다. Feign 은 따라가지 않으므로 호출은 실패하지만,
        // 302 인지 503 인지 구분할 수 있어야 원인을 찾는다.
        Exception decoded = decoder.decode("FaceClient#extract(MultipartFile)", response(302, ""));
        handler.handleUpstreamCallException((UpstreamCallException) decoded);

        assertThat(errors()).hasSize(1);
        assertThat(errors().get(0).getFormattedMessage()).contains("302");
    }

    @Test
    @DisplayName("4xx 인데 본문을 해석 못 한 경우 — 한 줄이지만 스택트레이스는 남는다")
    void 파싱_실패도_한_줄이다() {
        UpstreamCallException thrown = null;
        try {
            decoder.decode("FaceClient#extract(MultipartFile)", response(400, "not json"));
        } catch (UpstreamCallException e) {
            thrown = e;
        }
        assertThat(thrown).isNotNull();

        handler.handleUpstreamCallException(thrown);

        assertThat(errors()).hasSize(1);
        assertThat(errors().get(0).getThrowableProxy())
                .as("이쪽은 우리 파싱 코드가 원인일 수 있어 스택트레이스가 단서다")
                .isNotNull();
    }

    /**
     * 이 핸들러가 실제로 <b>선택되는지</b> (델타 리뷰 지적).
     *
     * <p>위 테스트들은 핸들러 메서드를 직접 부른다. 그래서 {@code @ExceptionHandler} 애노테이션을
     * 지워도 전부 초록이었다 — 그러면 운영에서는 {@code handleXxxCustomException} 으로 떨어져
     * 이 커밋이 없앤 <b>스택트레이스가 그대로 돌아오고</b> upstreamStatus 도 사라진다.
     *
     * <p>UG-308 에서 {@code @ResponseStatus} 고정이 사라졌다. 상태 코드가 상류 실패의 종류에
     * 따라 갈리기 때문이다 — 상세는 {@code UpstreamCallStatusTest}. 여기서는 고정 애노테이션이
     * 다시 붙지 않았는지만 본다.
     */
    @Test
    @DisplayName("상류 실패 핸들러가 전용 애노테이션으로 등록돼 있다")
    void 전용_핸들러로_등록돼_있다() throws NoSuchMethodException {
        var method = GlobalExceptionHandler.class.getMethod(
                "handleUpstreamCallException", UpstreamCallException.class);

        var handler = method.getAnnotation(
                org.springframework.web.bind.annotation.ExceptionHandler.class);
        assertThat(handler)
                .as("이 애노테이션이 없으면 상위 핸들러로 떨어져 스택트레이스가 돌아온다")
                .isNotNull();
        assertThat(handler.value()).containsExactly(UpstreamCallException.class);

        // UG-308: @ResponseStatus 고정이던 것을 ResponseEntity 로 바꿨다. 응답을 못 받은
        // 실패(NO_RESPONSE)만 5xx 로 내보내야 하기 때문이다 — 400 으로 두면 gate 의
        // CommonErrorDecoder 가 4xx 로 분류하고, gate 의 IdentifyPalmUseCase 가 그것을
        // 정상 결과로 흡수해 팜 모듈 전면 장애가 HTTP 200 "매칭 실패" 로 둔갑한다.
        //
        // 상태 코드 자체는 UpstreamCallStatusTest 가 핸들러를 실제로 호출해 못박는다.
        // 여기서는 '고정 애노테이션이 다시 붙지 않았는지' 만 본다 — 붙는 순간 분기가
        // 무력화되기 때문이다.
        assertThat(method.getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class))
                .as("@ResponseStatus 를 다시 붙이면 NO_RESPONSE 분기가 무력화된다")
                .isNull();
        assertThat(method.getReturnType())
                .isEqualTo(org.springframework.http.ResponseEntity.class);
    }

    @Test
    @DisplayName("operation 과 reason 이 자리를 바꾸지 않는다")
    void operation_과_reason_이_자리를_지킨다() {
        // contains 만으로는 두 값이 뒤바뀌어도 통과한다 (델타 리뷰 지적).
        handler.handleUpstreamCallException(
                new UpstreamCallException(503, "OP-VALUE", "REASON-VALUE"));

        assertThat(errors()).hasSize(1);
        assertThat(errors().get(0).getFormattedMessage())
                .containsSubsequence("operation=OP-VALUE", "reason=REASON-VALUE");
    }
}
