package ai.univs.palm.shared.exception;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 롤백 실패가 원래 예외를 덮는가 (UG-367 반박 리뷰 H1).
 *
 * <p>실제 PostgreSQL 재현(델타 리뷰 H1-bis)의 흐름을 흉내 낸다: 트랜잭션 안의 쿼리가 소켓 읽기 상한에 걸려 실패하고(원래
 * 예외), Hikari 가 그 커넥션을 닫아 둔 탓에 이어지는 롤백이 {@code Connection is closed} 로 실패한다. Hikari 의
 * {@code ClosedConnection} 은 이 예외에 SQLState 를 싣지 않는다 — 그 모양 그대로 넣는다.
 */
@DisplayName("UG-367: 끊긴 커넥션의 롤백 실패는 원래 예외를 덮지 않는다")
class ConnectionLossTolerantJpaTransactionManagerTest {

    private static final RuntimeException READ_TIMEOUT = new DataAccessResourceFailureException("could not execute statement",
            new SQLException("An I/O error occurred while sending to the backend.", "08006",
                    new java.net.SocketTimeoutException("Read timed out")));

    private static JpaTransactionManager 매니저(JpaTransactionManager manager, RuntimeException rollbackFailure) {
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        when(emf.createEntityManager()).thenReturn(em);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(true);
        doThrow(rollbackFailure).when(tx).rollback();
        manager.setEntityManagerFactory(emf);
        return manager;
    }

    /** 다른 드라이버·경로 — 연결 계열 SQLState 를 싣는 경우. */
    private static PersistenceException 닫힌_커넥션_롤백() {
        return new PersistenceException("Unable to rollback against JDBC Connection",
                new SQLException("Connection is closed", "08003"));
    }

    /** HikariCP 6.3 의 ClosedConnection 이 던지는 모양 — SQLState 없음. */
    private static PersistenceException 히카리_닫힌_커넥션_롤백() {
        return new PersistenceException("Unable to rollback against JDBC Connection", new SQLException("Connection is closed"));
    }

    @AfterEach
    void 묶음_해제() {
        new java.util.ArrayList<>(TransactionSynchronizationManager.getResourceMap().keySet())
                .forEach(TransactionSynchronizationManager::unbindResource);
    }

    /**
     * 트랜잭션에 묶인 커넥션 — 실제로는 JpaTransactionManager 가 트랜잭션을 열 때 Hibernate 방언으로 묶는다. 미리 묶어 두면
     * 「Pre-bound JDBC Connection」으로 거절되므로, 트랜잭션 안(콜백)에서 묶는다.
     */
    private static Runnable 커넥션_묶기(JpaTransactionManager manager, boolean closed) throws SQLException {
        javax.sql.DataSource dataSource = mock(javax.sql.DataSource.class);
        java.sql.Connection connection = mock(java.sql.Connection.class);
        when(connection.isClosed()).thenReturn(closed);
        manager.setDataSource(dataSource);
        return () -> TransactionSynchronizationManager.bindResource(dataSource, new ConnectionHolder(connection));
    }

    @Test
    @DisplayName("실제 Hikari 모양: SQLState 없는 「Connection is closed」라도 묶인 커넥션이 닫혔으면 원래 예외를 살린다")
    void 히카리_모양() throws SQLException {
        JpaTransactionManager manager = 매니저(new ConnectionLossTolerantJpaTransactionManager(), 히카리_닫힌_커넥션_롤백());
        Runnable 묶기 = 커넥션_묶기(manager, true);
        TransactionTemplate template = new TransactionTemplate(manager);

        assertThatThrownBy(() -> template.executeWithoutResult(status -> {
            묶기.run();
            throw READ_TIMEOUT;
        })).isSameAs(READ_TIMEOUT);
    }

    @Test
    @DisplayName("대조군: 같은 예외라도 묶인 커넥션이 살아 있으면 삼키지 않는다")
    void 살아_있는_커넥션() throws SQLException {
        JpaTransactionManager manager = 매니저(new ConnectionLossTolerantJpaTransactionManager(), 히카리_닫힌_커넥션_롤백());
        Runnable 묶기 = 커넥션_묶기(manager, false);
        TransactionTemplate template = new TransactionTemplate(manager);

        assertThatThrownBy(() -> template.executeWithoutResult(status -> {
            묶기.run();
            throw READ_TIMEOUT;
        })).isInstanceOf(JpaSystemException.class);
    }

    @Test
    @DisplayName("연결 계열 SQLState(08xxx)로 롤백하지 못해도 원래 예외(읽기 타임아웃)가 그대로 올라온다 → 처리기가 503 으로 낸다")
    void 원래_예외를_살린다() {
        TransactionTemplate template = new TransactionTemplate(
                매니저(new ConnectionLossTolerantJpaTransactionManager(), 닫힌_커넥션_롤백()));

        assertThatThrownBy(() -> template.executeWithoutResult(status -> {
            throw READ_TIMEOUT;
        })).isSameAs(READ_TIMEOUT);
    }

    @Test
    @DisplayName("대조군: 부트 기본 매니저는 롤백 예외가 원래 예외를 덮는다 — 이 클래스가 필요한 이유")
    void 기본_매니저는_덮는다() {
        TransactionTemplate template = new TransactionTemplate(매니저(new JpaTransactionManager(), 닫힌_커넥션_롤백()));

        assertThatThrownBy(() -> template.executeWithoutResult(status -> {
            throw READ_TIMEOUT;
        })).isInstanceOf(JpaSystemException.class);
    }

    @Test
    @DisplayName("연결 문제가 아닌 롤백 실패는 예전처럼 던진다")
    void 다른_롤백_실패는_던진다() {
        TransactionTemplate template = new TransactionTemplate(매니저(new ConnectionLossTolerantJpaTransactionManager(),
                new PersistenceException("rollback failed", new SQLException("deadlock", "40P01"))));

        assertThatThrownBy(() -> template.executeWithoutResult(status -> {
            throw READ_TIMEOUT;
        })).isInstanceOf(JpaSystemException.class);
    }
}
