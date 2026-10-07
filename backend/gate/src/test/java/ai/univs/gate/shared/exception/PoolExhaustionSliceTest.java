package ai.univs.gate.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ai.univs.gate.modules.project.infrastructure.persistence.ProjectJpaRepository;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.jpa.JpaSliceTest;
import ai.univs.gate.support.message.MessageService;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLTransientConnectionException;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * <b>실제</b> Hikari 풀 고갈이 판정기에 걸리고 503 이 되는가 (UG-359).
 *
 * <p>다른 테스트는 손으로 만든 예외를 쓴다. 그 모양이 실제와 다르면 — 예를 들어 Spring·Hibernate 가 원인을
 * 버리고 메시지만 옮겨 싣는다면 — 전부 초록인 채로 운영에서는 계속 500 이 나간다. 그래서 크기 1 인 풀의 유일한
 * 커넥션을 쥐고, 프레임워크가 실제로 던지는 예외를 받아 본다. UG-336 의 {@code SingleConnectionSliceTest} 와
 * 같은 설정이다.
 *
 * <p>세 경로를 본다 — 운영에서 커넥션을 요구하는 입구가 이 셋이다.
 * <ul>
 *   <li>트랜잭션 진입({@code TransactionTemplate}·{@code @Transactional}) → {@code CannotCreateTransactionException}
 *   <li>Spring Data 리포지토리를 트랜잭션 밖에서 호출 → 리포지토리 자체의 읽기 트랜잭션 진입
 *   <li>JDBC 직접 접근 → {@code CannotGetJdbcConnectionException}({@code DataAccessException})
 * </ul>
 */
@JpaSliceTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gate-ug359;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1",
        // Hikari 의 하한(250ms). 여기서는 기다리는 것 자체가 목적이라 짧을수록 좋다.
        "spring.datasource.hikari.connection-timeout=250"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("UG-359: 실제 풀 고갈 → 503")
class PoolExhaustionSliceTest {

    @Autowired private DataSource dataSource;
    @Autowired private TransactionTemplate tx;
    @Autowired private ProjectJpaRepository projectJpaRepository;

    /** 유일한 커넥션을 쥔 채 {@code action} 을 돌리고, 그것이 던진 예외를 돌려준다. */
    private Throwable 풀을_비운_채로(Runnable action) throws Exception {
        assertThat(((HikariDataSource) dataSource).getMaximumPoolSize())
                .as("전제: 풀 크기 1 — @DataJpaTest 가 데이터소스를 바꿔 끼우면 아래가 아무것도 증명하지 못한다")
                .isEqualTo(1);
        try (Connection held = dataSource.getConnection()) {
            assertThat(held).isNotNull();
            return catchThrowable(action::run);
        }
    }

    private static GlobalExceptionHandler handler() {
        MessageService messageService = Mockito.mock(MessageService.class);
        Mockito.when(messageService.getMessage(Mockito.any(ErrorType.class))).thenReturn("메시지");
        return new GlobalExceptionHandler(messageService);
    }

    private static void 판정되고_503이_된다(Throwable thrown) {
        assertThat(thrown).as("커넥션을 못 얻었으면 예외가 나야 한다").isNotNull();

        SQLTransientConnectionException timeout = PoolExhaustion.find(thrown)
                .orElseThrow(() -> new AssertionError("원인 사슬에 풀 타임아웃이 없다 — 실제 예외: " + thrown, thrown));
        assertThat(PoolExhaustion.isCongestion(timeout))
                .as("DB 는 살아 있고 커넥션이 모두 빌려 나갔을 뿐이다 — 혼잡(WARN)으로 분류돼야 한다. 원인: %s",
                        timeout.getCause())
                .isTrue();

        ResponseEntity<ResponseApi<?>> response = handler().handleGlobalException((Exception) thrown);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertThat(response.getBody().errors().code()).isEqualTo("PJ-006");
    }

    @Test
    @DisplayName("트랜잭션 진입에서 풀이 모자라면 CannotCreateTransactionException → 503")
    void 트랜잭션_진입() throws Exception {
        Throwable thrown = 풀을_비운_채로(() -> tx.executeWithoutResult(status -> {
        }));

        assertThat(thrown).isInstanceOf(CannotCreateTransactionException.class);
        판정되고_503이_된다(thrown);
    }

    @Test
    @DisplayName("리포지토리를 트랜잭션 밖에서 부를 때도 → 503")
    void 리포지토리_호출() throws Exception {
        Throwable thrown = 풀을_비운_채로(projectJpaRepository::count);

        판정되고_503이_된다(thrown);
    }

    @Test
    @DisplayName("JDBC 직접 접근에서는 DataAccessException 계열 → 503")
    void JDBC_직접_접근() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Throwable thrown = 풀을_비운_채로(() -> jdbc.queryForObject("SELECT 1", Integer.class));

        assertThat(thrown).isInstanceOf(DataAccessException.class);
        판정되고_503이_된다(thrown);
    }

    @Test
    @DisplayName("대조군: 커넥션이 남아 있으면 같은 호출이 성공한다 — 위 실패가 풀 때문임을 보인다")
    void 대조군() {
        assertThat(new JdbcTemplate(dataSource).queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        tx.executeWithoutResult(status -> projectJpaRepository.count());
    }
}
