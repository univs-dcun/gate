package ai.univs.gate.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.shared.exception.CustomGateException;
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
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 요청 로그의 양 (UG-383) — INFO 에는 요청당 끝날 때 한 줄, 요청 데이터는 DEBUG 에만.
 *
 * <p>운영의 로그 수준은 INFO 라서, 이 테스트는 로거 수준을 직접 정해 두고 본다. 테스트 실행 환경의 기본
 * 수준(DEBUG)에 기대면 "INFO 에서 몇 줄인가" 를 지킬 수 없다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LoggingAspect 요청 로그 양 (UG-383)")
class LoggingAspectRequestLogTest {

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String ACCOUNT_ID = "4242";

    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature signature;

    private LoggingAspect aspect;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private Level originalLevel;

    record Body(String name) {
    }

    @SuppressWarnings("unused")
    private void withBody(@RequestBody Body body) {
    }

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/feature/face/identify");
        request.addHeader("X-Forwarded-For", CLIENT_IP);
        request.addHeader("X-Account-Id", ACCOUNT_ID);
        aspect = new LoggingAspect(request);

        given(joinPoint.getSignature()).willReturn(signature);
        given(signature.getDeclaringType()).willReturn(LoggingAspectRequestLogTest.class);
        given(signature.getName()).willReturn("identify");
        given(signature.getMethod()).willReturn(
                LoggingAspectRequestLogTest.class.getDeclaredMethod("withBody", Body.class));
        given(joinPoint.getArgs()).willReturn(new Object[] {new Body("body-value")});

        logger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
        originalLevel = logger.getLevel();
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }

    @Test
    @DisplayName("INFO 에서는 정상 요청 하나에 [RESPONSE] 한 줄만 남고, 거기에 IP·계정·경로·소요시간이 있다")
    void INFO는_요청당_한_줄() throws Throwable {
        logger.setLevel(Level.INFO);
        given(joinPoint.proceed()).willReturn("ok");

        aspect.logAround(joinPoint);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent line = appender.list.get(0);
        assertThat(line.getLevel()).isEqualTo(Level.INFO);
        assertThat(line.getFormattedMessage())
                .startsWith("[RESPONSE]")
                .contains("POST")
                .contains("/api/v1/feature/face/identify")
                .contains("ip=" + CLIENT_IP)
                .contains("account=" + ACCOUNT_ID)
                .contains("duration=")
                .doesNotContain("body-value");
    }

    @Test
    @DisplayName("INFO 에서는 요청 데이터를 만들지도 않는다 — 매 요청 리플렉션 비용")
    void INFO에서는_요청데이터를_만들지_않는다() throws Throwable {
        logger.setLevel(Level.INFO);
        given(joinPoint.proceed()).willReturn("ok");

        aspect.logAround(joinPoint);

        verify(joinPoint, never()).getArgs();
    }

    @Test
    @DisplayName("INFO 에서 실패한 요청은 [EXCEPTION] 한 줄이고, 거기에도 IP·계정이 있다")
    void INFO_실패는_EXCEPTION_한_줄() throws Throwable {
        logger.setLevel(Level.INFO);
        given(joinPoint.proceed()).willThrow(new CustomGateException(ErrorType.INVALID_INPUT));

        assertThatThrownBy(() -> aspect.logAround(joinPoint)).isInstanceOf(CustomGateException.class);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .startsWith("[EXCEPTION]")
                .contains("ip=" + CLIENT_IP)
                .contains("account=" + ACCOUNT_ID)
                .contains("duration=");
    }

    @Test
    @DisplayName("DEBUG 를 켜면 [REQUEST] 가 DEBUG 수준으로 요청 데이터와 함께 남는다")
    void DEBUG에서는_요청데이터가_남는다() throws Throwable {
        logger.setLevel(Level.DEBUG);
        given(joinPoint.proceed()).willReturn("ok");

        aspect.logAround(joinPoint);

        List<ILoggingEvent> requestLines = appender.list.stream()
                .filter(e -> e.getFormattedMessage().startsWith("[REQUEST]"))
                .toList();
        assertThat(requestLines).hasSize(1);
        assertThat(requestLines.get(0).getLevel()).isEqualTo(Level.DEBUG);
        assertThat(requestLines.get(0).getFormattedMessage()).contains("body-value");
        assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.INFO && e.getFormattedMessage().startsWith("[RESPONSE]"));
    }
}
