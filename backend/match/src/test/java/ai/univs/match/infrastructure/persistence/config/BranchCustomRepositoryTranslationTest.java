package ai.univs.match.infrastructure.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.univs.match.infrastructure.persistence.BranchCustomRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;

/**
 * UG-374: 브랜치 생성 구현의 JPA 예외가 Spring 예외로 번역되는가.
 *
 * <p>{@code BranchProvisioner} 는 Oracle {@code MERGE} 의 드문 동시 위반을 {@link DataIntegrityViolationException} 으로 잡아
 * 다시 읽는다. 구현은 {@code RepositoryConfig} 의 {@code @Bean} 이라 Spring Data 저장소 프록시를 거치지 않는다 — 인터페이스의
 * {@code @Repository} 를 보고 번역 후처리기가 프록시를 씌워야 한다. 그 표시가 빠지면 하이버네이트 예외가 그대로 나가 등록이
 * 실패한다(슬라이스 테스트에서 실제로 그렇게 실패했다). 운영처럼 Boot 의 번역 자동 설정을 켜고 본다.
 */
@DisplayName("UG-374: 브랜치 생성의 예외 번역")
class BranchCustomRepositoryTranslationTest {

    @Configuration(proxyBeanMethods = false)
    @Import(RepositoryConfig.class)
    static class Jpa {
        @Bean
        EntityManager entityManager() {
            EntityManager em = mock(EntityManager.class);
            Query query = mock(Query.class);
            when(em.createNativeQuery(anyString())).thenReturn(query);
            when(query.setParameter(anyString(), any())).thenReturn(query);
            when(query.executeUpdate()).thenThrow(new ConstraintViolationException("unique",
                    new SQLException("ORA-00001: unique constraint (UK_BRANCH_BRANCH_NAME) violated"),
                    "UK_BRANCH_BRANCH_NAME"));
            return em;
        }

        /** 운영에서는 EntityManagerFactory 가 이 역할이다 */
        @Bean
        PersistenceExceptionTranslator jpaTranslator() {
            HibernateJpaDialect dialect = new HibernateJpaDialect();
            return dialect::translateExceptionIfPossible;
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"oracle", "postgresql"})
    @DisplayName("위반은 DataIntegrityViolationException 으로 나온다 — 번역 프록시가 씌워졌다")
    void 번역된다(String profile) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PersistenceExceptionTranslationAutoConfiguration.class))
                .withUserConfiguration(Jpa.class)
                .withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    BranchCustomRepository repository = context.getBean(BranchCustomRepository.class);
                    assertThat(AopUtils.isAopProxy(repository)).as("번역 프록시").isTrue();
                    assertThatThrownBy(() -> repository.insertIfAbsent("b", LocalDateTime.now()))
                            .isInstanceOf(DataIntegrityViolationException.class);
                });
    }
}
