package ai.univs.match.shared.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ai.univs.match.shared.locale.MessageService;
import ai.univs.match.shared.web.enums.ErrorType;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 풀 고갈이 503 + SWAGGER-006 + Retry-After 로 나간다 (UG-359).
 *
 * <p>gate 의 디코더는 <b>503 이면서 {@code errors.type} 이 {@code TEMPORARILY_UNAVAILABLE}</b> 일 때만 "잠시 뒤
 * 다시" 로 읽는다. 상태나 유형 이름 중 하나만 어긋나도 gate 는 일반 하위 실패(400 PJ-005)로 내보낸다. 그래서 그 둘을
 * HTTP 응답 수준에서 못박는다. 대조군(일반 500)을 함께 둬 판정이 넓어지는 변이를 잡는다.
 */
@DisplayName("UG-359: 풀 고갈 응답")
class TemporarilyUnavailableResponseTest {

    private static final String POOL_TIMEOUT_MESSAGE =
            "HikariPool-1 - Connection is not available, request timed out after 1000ms";

    @RestController
    static class Probe {

        @GetMapping("/pool/tx")
        void poolTimeoutOpeningTransaction() {
            throw new CannotCreateTransactionException("Could not open JPA EntityManager for transaction",
                    new RuntimeException("JDBCConnectionException",
                            new SQLTransientConnectionException(POOL_TIMEOUT_MESSAGE)));
        }

        @GetMapping("/pool/jdbc")
        void poolTimeoutInJdbc() {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection",
                    new SQLTransientConnectionException(POOL_TIMEOUT_MESSAGE));
        }

        @GetMapping("/db/read-timeout")
        void dbReadTimeout() {
            // UG-367: 드라이버 소켓 읽기 상한에 걸린 모양 — Hibernate JDBCConnectionException 을 Spring 이 감싼다
            throw new DataAccessResourceFailureException("could not execute statement",
                    new RuntimeException("JDBCConnectionException", new SQLException(
                            "An I/O error occurred while sending to the backend.", "08006",
                            new java.net.SocketTimeoutException("Read timed out"))));
        }

        @GetMapping("/sql/other")
        void otherSqlError() {
            throw new UncategorizedSQLException("select", "select 1", new SQLException("syntax error", "42601"));
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("boom");
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        MessageService messageService = Mockito.mock(MessageService.class);
        Mockito.when(messageService.getMessage(Mockito.any(ErrorType.class)))
                .thenAnswer(inv -> "msg:" + ((ErrorType) inv.getArgument(0)).name());
        mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setControllerAdvice(new GlobalExceptionHandler(messageService))
                .build();
    }

    @Test
    @DisplayName("트랜잭션을 열다 풀이 모자라면 503 + SWAGGER-006 + TEMPORARILY_UNAVAILABLE + Retry-After: 1")
    void 트랜잭션_진입_풀_고갈() throws Exception {
        mvc.perform(get("/pool/tx"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.code").value("SWAGGER-006"))
                .andExpect(jsonPath("$.errors.type").value("TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.errors.message").value("msg:TEMPORARILY_UNAVAILABLE"));
    }

    @Test
    @DisplayName("JDBC 접근 중 풀이 모자라도 같다 — 감싸는 타입이 아니라 원인 사슬을 본다")
    void JDBC_접근_풀_고갈() throws Exception {
        mvc.perform(get("/pool/jdbc"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.errors.type").value("TEMPORARILY_UNAVAILABLE"));
    }

    @Test
    @DisplayName("UG-367: DB 가 상한 안에 응답하지 않으면 503 + SWAGGER-006 + Retry-After — 멈춘 DB 를 끝없이 기다리지 않는다")
    void DB_응답_시간_초과는_503() throws Exception {
        mvc.perform(get("/db/read-timeout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.errors.code").value("SWAGGER-006"))
                .andExpect(jsonPath("$.errors.type").value("TEMPORARILY_UNAVAILABLE"));
    }

    @Test
    @DisplayName("대조군: 풀 고갈이 아닌 SQL 오류는 예전처럼 500 SWAGGER-005")
    void 다른_SQL_오류는_500() throws Exception {
        mvc.perform(get("/sql/other"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.errors.code").value("SWAGGER-005"));
    }

    @Test
    @DisplayName("대조군: 일반 예외는 예전처럼 500 SWAGGER-005")
    void 일반_예외는_500() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.errors.code").value("SWAGGER-005"))
                .andExpect(jsonPath("$.errors.type").value("INTERNAL_SERVER_ERROR"));
    }
}
