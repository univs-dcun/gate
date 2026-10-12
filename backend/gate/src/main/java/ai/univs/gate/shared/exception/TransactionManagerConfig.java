package ai.univs.gate.shared.exception;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 부트 기본 {@code JpaTransactionManager} 대신 {@link ConnectionLossTolerantJpaTransactionManager} 를 쓴다 (UG-367).
 * 부트 자동 설정과 같게 {@code spring.transaction.*} 커스터마이저를 적용한다. 엔티티 매니저 팩토리는 매니저가 빈 팩토리에서
 * 스스로 찾는다.
 */
@Configuration(proxyBeanMethods = false)
public class TransactionManagerConfig {

    @Bean
    public PlatformTransactionManager transactionManager(ObjectProvider<TransactionManagerCustomizers> customizers) {
        ConnectionLossTolerantJpaTransactionManager transactionManager = new ConnectionLossTolerantJpaTransactionManager();
        customizers.ifAvailable(c -> c.customize(transactionManager));
        return transactionManager;
    }
}
