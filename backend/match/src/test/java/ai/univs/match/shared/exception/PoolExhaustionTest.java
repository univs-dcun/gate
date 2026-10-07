package ai.univs.match.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * 풀 고갈 판정 (UG-359).
 *
 * <p>실제 Hikari 가 만드는 예외로도 확인한다. 판정이 기대는 두 사실 — "타임아웃은
 * {@link SQLTransientConnectionException} 이다", "DB 에 닿지 못하면 원인이 실린다" — 은 Hikari 내부 동작이라
 * 손으로 만든 예외만으로는 증명되지 않는다. 혼잡(원인 없음) 쪽은 gate 의 {@code PoolExhaustionSliceTest} 가 실제 풀로
 * 잰다 — 판정 코드가 네 서비스에서 같다.
 */
@DisplayName("UG-359: PoolExhaustion 판정")
class PoolExhaustionTest {

    @Test
    @DisplayName("원인 사슬 깊숙이 있는 타임아웃도 찾는다")
    void 사슬_안쪽까지_찾는다() {
        SQLTransientConnectionException timeout = new SQLTransientConnectionException("timeout");
        Exception wrapped = new CannotCreateTransactionException("tx",
                new RuntimeException("hibernate", new IllegalStateException("middle", timeout)));

        assertThat(PoolExhaustion.find(wrapped)).containsSame(timeout);
    }

    @Test
    @DisplayName("다른 SQL 오류는 풀 고갈이 아니다")
    void 다른_SQL_오류는_아니다() {
        assertThat(PoolExhaustion.find(new RuntimeException(new SQLException("syntax error", "42601"))))
                .isEmpty();
        assertThat(PoolExhaustion.find(new IllegalStateException("boom"))).isEmpty();
        assertThat(PoolExhaustion.find(null)).isEmpty();
    }

    @Test
    @Timeout(5)
    @DisplayName("순환하는 원인 사슬에서도 끝난다 — 예외 처리 중 무한 루프는 요청 스레드를 묶는다")
    void 순환_사슬에서_끝난다() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);   // a → b → a

        assertThat(PoolExhaustion.find(a)).isEmpty();
    }

    @Test
    @DisplayName("원인 없는 타임아웃은 혼잡, 원인 있는 타임아웃은 DB 불통")
    void 원인_유무로_가른다() {
        assertThat(PoolExhaustion.isCongestion(new SQLTransientConnectionException("timeout"))).isTrue();
        assertThat(PoolExhaustion.isCongestion(new SQLTransientConnectionException(
                "timeout", "08001", new java.net.ConnectException("refused")))).isFalse();
    }

    /**
     * 실제 Hikari — 닿을 수 없는 DB 로 향한 풀의 타임아웃에는 원인이 실린다.
     *
     * <p>{@code 127.0.0.1:1} 은 루프백이라 연결 거부가 즉시 돌아온다(드롭돼 타임아웃이 되는 일이 없다). Hikari 가
     * 그 실패를 기억해 두었다가 타임아웃 예외의 원인으로 싣는다. 이 사실이 깨지면(라이브러리 변경) DB 다운이 WARN
     * 으로 조용히 지나가므로 여기서 못박는다.
     */
    @Test
    @DisplayName("실제 Hikari: DB 에 닿지 못하는 풀의 타임아웃은 원인을 싣는다 → 혼잡이 아니다")
    void 실제_Hikari_DB_불통() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://127.0.0.1:1/ug359");
        config.setUsername("none");
        config.setPassword("none");
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(1000);
        config.setInitializationFailTimeout(-1);   // 시작 시 연결을 시도하지 않는다 — 운영에서 DB 가 나중에 죽은 상황
        config.setPoolName("ug359-unreachable");

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            assertThatThrownBy(() -> {
                try (Connection ignored = dataSource.getConnection()) {
                    // 오지 않는다
                }
            }).satisfies(thrown -> {
                SQLTransientConnectionException timeout = PoolExhaustion.find(thrown).orElseThrow();
                assertThat(PoolExhaustion.isCongestion(timeout))
                        .as("Hikari 가 마지막 생성 실패를 원인으로 실어야 한다 — 실제 원인: %s", timeout.getCause())
                        .isFalse();
            });
        }
    }
}
