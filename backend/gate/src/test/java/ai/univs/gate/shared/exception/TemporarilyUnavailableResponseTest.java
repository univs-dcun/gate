package ai.univs.gate.shared.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.univs.gate.support.message.MessageService;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 풀 고갈·하위의 "잠시 뒤 다시" 가 503 + PJ-006 + Retry-After 로 나간다 (UG-359).
 *
 * <p>핸들러 메서드를 직접 부르지 않고 MockMvc 로 HTTP 응답을 본다. 상태 코드가 {@code @ResponseStatus} 가
 * 아니라 {@code ResponseEntity} 로 정해지므로, 실제 응답에 무엇이 실리는지는 디스패처를 거쳐야 확인된다.
 * 메시지도 실제 번들로 푼다 — 번들에 키가 빠지면 여기서 걸린다.
 *
 * <p>대조군(일반 500, 다른 하위 503)을 함께 둔다. 판정이 넓어져 모든 오류가 503 이 되는 변이를 잡는다.
 */
@DisplayName("UG-359: 일시적 처리 불가 응답")
class TemporarilyUnavailableResponseTest {

    private static final String POOL_TIMEOUT_MESSAGE =
            "HikariPool-1 - Connection is not available, request timed out after 1000ms";

    @RestController
    static class Probe {

        @GetMapping("/pool/tx")
        void poolTimeoutOpeningTransaction() {
            // JpaTransactionManager.doBegin 이 감싸는 모양 — 트랜잭션을 열다 커넥션을 못 얻었다
            throw new CannotCreateTransactionException("Could not open JPA EntityManager for transaction",
                    new RuntimeException("JDBCConnectionException",
                            new SQLTransientConnectionException(POOL_TIMEOUT_MESSAGE)));
        }

        @GetMapping("/pool/jdbc")
        void poolTimeoutInJdbc() {
            // DataSourceUtils 가 감싸는 모양 — 트랜잭션 없이 JDBC 접근 중 커넥션을 못 얻었다
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection",
                    new SQLTransientConnectionException(POOL_TIMEOUT_MESSAGE));
        }

        @GetMapping("/sql/other")
        void otherSqlError() {
            // 같은 DataAccessException 이지만 풀 고갈이 아니다 — 예전처럼 500 이어야 한다
            throw new UncategorizedSQLException("select", "select 1", new SQLException("syntax error", "42601"));
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/upstream/busy")
        void upstreamBusy() {
            throw RemoteCallException.temporarilyUnavailable(503, "FaceClient#identify()");
        }

        @GetMapping("/upstream/503")
        void upstreamPlain503() {
            throw new RemoteCallException(503, "FaceClient#identify()", null);
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setControllerAdvice(new GlobalExceptionHandler(new MessageService(source)))
                .build();
        LocaleContextHolder.setLocale(Locale.KOREAN);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("트랜잭션을 열다 풀이 모자라면 503 + PJ-006 + Retry-After: 1")
    void 트랜잭션_진입_풀_고갈() throws Exception {
        mvc.perform(get("/pool/tx").locale(Locale.KOREAN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.code").value("PJ-006"))
                .andExpect(jsonPath("$.errors.type").value("TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.errors.message")
                        .value("일시적으로 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    @DisplayName("JDBC 접근 중 풀이 모자라도 같다 — 감싸는 타입이 아니라 원인 사슬을 본다")
    void JDBC_접근_풀_고갈() throws Exception {
        mvc.perform(get("/pool/jdbc").locale(Locale.ENGLISH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.errors.code").value("PJ-006"));
    }

    @Test
    @DisplayName("대조군: 풀 고갈이 아닌 SQL 오류는 예전처럼 500 PJ-005 이고 Retry-After 가 없다")
    void 다른_SQL_오류는_500() throws Exception {
        mvc.perform(get("/sql/other").locale(Locale.KOREAN))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.errors.code").value("PJ-005"));
    }

    @Test
    @DisplayName("대조군: 일반 예외는 예전처럼 500 PJ-005")
    void 일반_예외는_500() throws Exception {
        mvc.perform(get("/boom").locale(Locale.KOREAN))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.errors.code").value("PJ-005"))
                .andExpect(jsonPath("$.errors.type").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    @DisplayName("하위가 '잠시 뒤 다시' 를 알려 오면 503 + PJ-006 + Retry-After — 하위 코드(SWAGGER-006)는 드러내지 않는다")
    void 하위_일시_불가는_503() throws Exception {
        mvc.perform(get("/upstream/busy").locale(Locale.ENGLISH))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.errors.code").value("PJ-006"))
                .andExpect(jsonPath("$.errors.type").value("TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.errors.message").value(
                        "The service is temporarily unable to handle the request. Please try again shortly."));
    }

    @Test
    @DisplayName("대조군: 다른 하위 503 은 예전처럼 400 PJ-005 — 계약 불변")
    void 다른_하위_503은_400() throws Exception {
        mvc.perform(get("/upstream/503").locale(Locale.KOREAN))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.errors.code").value("PJ-005"));
    }
}
