package ai.univs.gate.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 앱의 트랜잭션 매니저가 {@link ConnectionLossTolerantJpaTransactionManager} 이고 엔티티 매니저 팩토리를 스스로 찾는가 (UG-367).
 * 이 서비스에는 앱 기동 테스트가 없어 빈 배선을 여기서 본다.
 */
@DisplayName("UG-367: 트랜잭션 매니저 배선")
class TransactionManagerConfigTest {

    @Test
    @DisplayName("트랜잭션 매니저는 끊긴 커넥션의 롤백 실패를 삼키는 매니저이고, 엔티티 매니저 팩토리를 찾아 붙는다")
    void 배선() {
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        new ApplicationContextRunner()
                .withUserConfiguration(TransactionManagerConfig.class)
                .withBean(EntityManagerFactory.class, () -> emf)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PlatformTransactionManager manager = context.getBean(PlatformTransactionManager.class);
                    assertThat(manager).isInstanceOf(ConnectionLossTolerantJpaTransactionManager.class);
                    assertThat(((JpaTransactionManager) manager).getEntityManagerFactory()).isSameAs(emf);
                });
    }
}
