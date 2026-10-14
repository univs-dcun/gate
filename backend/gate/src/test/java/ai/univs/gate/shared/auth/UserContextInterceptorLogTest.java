package ai.univs.gate.shared.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 헤더 정보 줄은 DEBUG 다 (UG-383) — 요청마다 남는 INFO 줄이 LoggingAspect 종결선 말고 또 있으면
 * 「INFO 는 요청당 한 줄」 이 깨진다. 계정은 종결선의 account= 로 남는다.
 */
@DisplayName("UserContextInterceptor 헤더 로그 수준 (UG-383)")
class UserContextInterceptorLogTest {

    private final UserContextInterceptor interceptor = new UserContextInterceptor();
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(UserContextInterceptor.class);
        originalLevel = logger.getLevel();
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
        UserContext.clear();
        MDC.remove("accountId");
    }

    @Test
    @DisplayName("INFO 에서는 헤더 정보 줄을 남기지 않는다")
    void INFO에서는_남기지_않는다() {
        logger.setLevel(Level.INFO);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        request.addHeader("X-Account-Id", "4242");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(appender.list).isEmpty();
    }
}
