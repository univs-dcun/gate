package ai.univs.gate.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@code LoggingAspect} 의 예외 로그 수준 (UG-290).
 *
 * <p>반박 리뷰의 지적으로 신설했다. 이 PR 은 "로그 수준은 아무도 보지 않으면 조용히 되돌아간다" 를
 * 근거로 {@code GlobalExceptionHandler} 쪽 테스트를 만들었는데, <b>같은 논리가 적용되는 두 지점 중
 * 한쪽만 지키고 있었다.</b> {@code isClientError} 가 통째로 뒤집혀도 전 테스트가 초록이었다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LoggingAspect 예외 로그 수준 (UG-290)")
class LoggingAspectLogLevelTest {

    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature signature;

    private LoggingAspect aspect;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/feature/face/identify");
        aspect = new LoggingAspect(request);

        given(joinPoint.getSignature()).willReturn(signature);
        given(signature.getDeclaringType()).willReturn(LoggingAspectLogLevelTest.class);
        given(signature.getName()).willReturn("identify");
        // extractRequestData 가 MethodSignature 로 캐스팅한 뒤 파라미터 애노테이션을 읽는다.
        // 인자 없는 실제 메서드를 물려 그 경로가 그대로 돌게 한다.
        given(signature.getMethod()).willReturn(LoggingAspectLogLevelTest.class.getDeclaredMethod("noArgs"));
        given(joinPoint.getArgs()).willReturn(new Object[0]);

        logger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    /** {@code signature.getMethod()} 가 물려줄 대상. 인자가 없어야 요청 데이터 추출이 단순해진다. */
    @SuppressWarnings("unused")
    private void noArgs() {
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private Level levelOfExceptionLine(Throwable thrown) throws Throwable {
        given(joinPoint.proceed()).willThrow(thrown);

        assertThatThrownBy(() -> aspect.logAround(joinPoint))
                .as("애스펙트는 예외를 삼키지 않는다 — 삼키면 응답 계약이 깨진다")
                .isSameAs(thrown);

        List<ILoggingEvent> exceptionLines = appender.list.stream()
                .filter(e -> e.getFormattedMessage().startsWith("[EXCEPTION]"))
                .toList();
        assertThat(exceptionLines).hasSize(1);
        return exceptionLines.get(0).getLevel();
    }

    @Test
    @DisplayName("4xx 로 매핑되는 BusinessException 은 WARN")
    void 클라이언트_오류는_WARN() throws Throwable {
        assertThat(levelOfExceptionLine(new CustomGateException(ErrorType.API_KEY_NOT_FOUND)))
                .isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("5xx 로 매핑되는 예외는 ERROR")
    void 서버_오류는_ERROR() throws Throwable {
        assertThat(levelOfExceptionLine(new RemoteCallException(503, "face.identify", null)))
                .isEqualTo(Level.ERROR);
    }

    @Test
    @DisplayName("하위 서비스의 일반 4xx 는 WARN")
    void 하위_4xx는_WARN() throws Throwable {
        assertThat(levelOfExceptionLine(
                new CustomFeignException("ML-101", "FACE_NOT_FOUND", "no face")))
                .isEqualTo(Level.WARN);
    }

    @Test
    @DisplayName("하위 서비스가 자기 오류라고 말하면 HTTP 400 이어도 ERROR")
    void 하위가_자기오류라_말하면_ERROR() throws Throwable {
        // 반박 리뷰가 찾은 경로. face·palm 은 자기 쪽 5xx 를 CustomFaceException(INTERNAL_SERVER_ERROR)
        // 으로 감싼 뒤 @ResponseStatus(BAD_REQUEST) 로 내려보내고 로그를 남기지 않는다.
        // HTTP 상태만 보고 WARN 으로 내리면 ML 매처 전면 장애에 ERROR 가 한 줄도 안 남는다.
        assertThat(levelOfExceptionLine(
                new CustomFeignException("SWAGGER-005", "INTERNAL_SERVER_ERROR", "matcher down")))
                .isEqualTo(Level.ERROR);
    }

    @Test
    @DisplayName("분류를 모르는 예외는 ERROR — 조용한 쪽으로 보내면 진짜 장애를 놓친다")
    void 미분류는_ERROR() throws Throwable {
        assertThat(levelOfExceptionLine(new IllegalStateException("boom"))).isEqualTo(Level.ERROR);
    }

    @Test
    @DisplayName("정상 응답은 [RESPONSE] 로 끝나고 [EXCEPTION] 을 남기지 않는다")
    void 정상_경로() throws Throwable {
        given(joinPoint.proceed()).willReturn("ok");

        assertThat(aspect.logAround(joinPoint)).isEqualTo("ok");
        assertThat(appender.list)
                .noneMatch(e -> e.getFormattedMessage().startsWith("[EXCEPTION]"));
        assertThat(appender.list)
                .anyMatch(e -> e.getFormattedMessage().startsWith("[RESPONSE]"));
    }

    @Test
    @DisplayName("예외 줄에 경로·소요시간이 남는다 — 이 줄에만 있는 정보다")
    void 종결선_정보() throws Throwable {
        given(joinPoint.proceed()).willThrow(new CustomGateException(ErrorType.INVALID_INPUT));

        assertThatThrownBy(() -> aspect.logAround(joinPoint)).isInstanceOf(CustomGateException.class);

        ILoggingEvent line = appender.list.stream()
                .filter(e -> e.getFormattedMessage().startsWith("[EXCEPTION]"))
                .findFirst()
                .orElseThrow();
        assertThat(line.getFormattedMessage())
                .contains("POST")
                .contains("/api/v1/feature/face/identify")
                .contains("duration=");
    }
}
