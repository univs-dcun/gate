package ai.univs.gate.shared.utils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DisplayName("UG-367: 정리 잡 트랜잭션만 DB 응답 상한을 늘린다")
class JobConnectionTimeoutTest {

    private static final int JOB_MILLIS = (int) JobConnectionTimeout.JOB.toMillis();

    @AfterEach
    void clear() {
        // 스레드 로컬이라 남기면 같은 스레드의 다른 테스트가 트랜잭션 안인 줄 안다
        new java.util.ArrayList<>(TransactionSynchronizationManager.getResourceMap().keySet())
                .forEach(TransactionSynchronizationManager::unbindResource);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private static void 트랜잭션_시작(DataSource dataSource, Connection connection) {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.bindResource(dataSource, new ConnectionHolder(connection));
    }

    @Test
    @DisplayName("트랜잭션의 커넥션에 5분을 건다")
    void 트랜잭션_안() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        트랜잭션_시작(dataSource, connection);

        new JobConnectionTimeout(dataSource).extendForCurrentTransaction();

        verify(connection).setNetworkTimeout(any(Executor.class), eq(JOB_MILLIS));
        verify(connection, never()).close();
    }

    @Test
    @DisplayName("PostgreSQL 이면 이 트랜잭션의 statement_timeout 도 5분으로 늘린다 — SET LOCAL 이라 트랜잭션이 끝나면 돌아온다")
    void PostgreSQL_서버_상한() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        java.sql.DatabaseMetaData metaData = mock(java.sql.DatabaseMetaData.class);
        java.sql.Statement statement = mock(java.sql.Statement.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.createStatement()).thenReturn(statement);
        트랜잭션_시작(dataSource, connection);

        new JobConnectionTimeout(dataSource).extendForCurrentTransaction();

        verify(statement).execute("SET LOCAL statement_timeout = " + JobConnectionTimeout.JOB.toMillis());
        verify(statement).close();
    }

    @Test
    @DisplayName("트랜잭션 밖이면 커넥션을 빌리지 않는다 — 걸어 봐야 곧바로 반납된다")
    void 트랜잭션_밖() {
        DataSource dataSource = mock(DataSource.class);

        new JobConnectionTimeout(dataSource).extendForCurrentTransaction();

        verifyNoInteractions(dataSource);
    }

    @Test
    @DisplayName("드라이버가 거절해도 잡을 멈추지 않는다 — 기본 상한으로 돌 뿐")
    void 실패해도_진행() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        doThrow(new SQLException("not supported")).when(connection).setNetworkTimeout(any(), anyInt());
        트랜잭션_시작(dataSource, connection);

        assertThatCode(() -> new JobConnectionTimeout(dataSource).extendForCurrentTransaction())
                .doesNotThrowAnyException();
    }

    /**
     * 실제 HikariCP — 늘린 상한이 풀로 돌아갈 때 원래 값으로 되돌아가는가. 되돌아가지 않으면 그 커넥션을 다음에 빌리는 요청이
     * 5분을 물려받아, DB 가 멈췄을 때 다시 끝없이 가까이 기다린다. 이 클래스가 기대는 사실이라 여기서 못박는다.
     */
    @Test
    @DisplayName("실제 Hikari: 풀에 돌려줄 때 원래 상한으로 되돌린다")
    void 반납하면_되돌린다() throws Exception {
        Connection physical = mock(Connection.class);
        when(physical.isValid(anyInt())).thenReturn(true);
        when(physical.getAutoCommit()).thenReturn(true);
        when(physical.getNetworkTimeout()).thenReturn(10_000);   // gate-config 의 socketTimeout 10초
        when(physical.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        DataSource driver = mock(DataSource.class);
        when(driver.getConnection()).thenReturn(physical);

        HikariConfig config = new HikariConfig();
        config.setDataSource(driver);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setPoolName("ug367-reset");
        try (HikariDataSource pool = new HikariDataSource(config)) {
            Connection borrowed = pool.getConnection();
            트랜잭션_시작(pool, borrowed);
            new JobConnectionTimeout(pool).extendForCurrentTransaction();
            borrowed.close();

            InOrder order = inOrder(physical);
            order.verify(physical).setNetworkTimeout(any(), eq(JOB_MILLIS));
            order.verify(physical).setNetworkTimeout(any(), eq(10_000));
        }
    }
}
