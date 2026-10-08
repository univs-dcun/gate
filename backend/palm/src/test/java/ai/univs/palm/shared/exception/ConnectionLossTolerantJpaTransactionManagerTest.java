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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 롤백 실패가 원래 예외를 덮는가 (UG-367 반박 리뷰 H1).
 *
 * <p>실제 PostgreSQL 에서 재현한 흐름을 그대로 흉내 낸다: 트랜잭션 안의 쿼리가 소켓 읽기 상한에 걸려 실패하고(원래 예외),
 * Hikari 가 그 커넥션을 닫아 둔 탓에 이어지는 롤백이 {@code Connection is closed}(08003)로 실패한다.
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

    private static PersistenceException 닫힌_커넥션_롤백() {
        // Hibernate 가 감싸는 모양 — Unable to rollback against JDBC Connection ← Connection is closed (08003)
        return new PersistenceException("Unable to rollback against JDBC Connection",
                new SQLException("Connection is closed", "08003"));
    }

    @Test
    @DisplayName("커넥션이 끊겨 롤백하지 못하면 원래 예외(읽기 타임아웃)가 그대로 올라온다 → 처리기가 503 으로 낸다")
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
