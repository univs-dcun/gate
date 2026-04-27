package ai.univs.match.infrastructure.persistence.config;

import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
import ai.univs.match.infrastructure.persistence.OracleDescriptorCustomRepositoryImpl;
import ai.univs.match.infrastructure.persistence.PostgresqlDescriptorCustomRepositoryImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("RepositoryConfig - 프로필별 DescriptorCustomRepository 빈 등록")
class RepositoryConfigTest {

    /**
     * 실제 DB 연결 없이 RepositoryConfig만 테스트하기 위한 최소 설정.
     * EntityManager를 mock으로 제공하여 JPA 컨텍스트 없이도 빈 생성 가능.
     */
    @Configuration
    @Import(RepositoryConfig.class)
    static class TestConfig {
        @Bean
        EntityManager entityManager() {
            return mock(EntityManager.class);
        }
    }

    // -------------------------------------------------------------------------
    // oracle 프로필
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("oracle 프로필 활성화 시")
    @SpringJUnitConfig(TestConfig.class)
    @ActiveProfiles("oracle")
    class OracleProfileTest {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("DescriptorCustomRepository 빈이 OracleDescriptorCustomRepositoryImpl 타입으로 등록된다")
        void whenOracleProfileActive_thenOracleImplIsRegistered() {
            DescriptorCustomRepository repository = context.getBean(DescriptorCustomRepository.class);

            assertThat(repository).isInstanceOf(OracleDescriptorCustomRepositoryImpl.class);
        }

        @Test
        @DisplayName("PostgresqlDescriptorCustomRepositoryImpl 빈은 등록되지 않는다")
        void whenOracleProfileActive_thenPostgresqlImplIsNotRegistered() {
            assertThatThrownBy(() -> context.getBean(PostgresqlDescriptorCustomRepositoryImpl.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }

        @Test
        @DisplayName("주입된 EntityManager가 OracleDescriptorCustomRepositoryImpl에 전달된다")
        void whenOracleProfileActive_thenEntityManagerIsInjectedIntoOracleImpl() {
            EntityManager providedEntityManager = context.getBean(EntityManager.class);
            OracleDescriptorCustomRepositoryImpl oracleImpl =
                    context.getBean(OracleDescriptorCustomRepositoryImpl.class);

            assertThat(oracleImpl).hasFieldOrPropertyWithValue("em", providedEntityManager);
        }
    }

    // -------------------------------------------------------------------------
    // postgresql 프로필
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("postgresql 프로필 활성화 시")
    @SpringJUnitConfig(TestConfig.class)
    @ActiveProfiles("postgresql")
    class PostgresqlProfileTest {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("DescriptorCustomRepository 빈이 PostgresqlDescriptorCustomRepositoryImpl 타입으로 등록된다")
        void whenPostgresqlProfileActive_thenPostgresqlImplIsRegistered() {
            DescriptorCustomRepository repository = context.getBean(DescriptorCustomRepository.class);

            assertThat(repository).isInstanceOf(PostgresqlDescriptorCustomRepositoryImpl.class);
        }

        @Test
        @DisplayName("OracleDescriptorCustomRepositoryImpl 빈은 등록되지 않는다")
        void whenPostgresqlProfileActive_thenOracleImplIsNotRegistered() {
            assertThatThrownBy(() -> context.getBean(OracleDescriptorCustomRepositoryImpl.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }

        @Test
        @DisplayName("주입된 EntityManager가 PostgresqlDescriptorCustomRepositoryImpl에 전달된다")
        void whenPostgresqlProfileActive_thenEntityManagerIsInjectedIntoPostgresqlImpl() {
            EntityManager providedEntityManager = context.getBean(EntityManager.class);
            PostgresqlDescriptorCustomRepositoryImpl postgresqlImpl =
                    context.getBean(PostgresqlDescriptorCustomRepositoryImpl.class);

            assertThat(postgresqlImpl).hasFieldOrPropertyWithValue("em", providedEntityManager);
        }

        @Test
        @DisplayName("postgresql 빈은 @Primary가 적용되어 기본 빈으로 선택된다")
        void whenPostgresqlProfileActive_thenPostgresqlImplIsPrimaryBean() {
            // @Primary 어노테이션 덕분에 타입으로 단일 조회 시 PostgresqlImpl이 반환된다
            DescriptorCustomRepository repository = context.getBean(DescriptorCustomRepository.class);

            assertThat(repository).isInstanceOf(PostgresqlDescriptorCustomRepositoryImpl.class);
        }
    }

    // -------------------------------------------------------------------------
    // 두 프로필 동시 활성화 시 (@Primary 우선순위 검증)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("oracle + postgresql 두 프로필 동시 활성화 시")
    @SpringJUnitConfig(TestConfig.class)
    @ActiveProfiles({"oracle", "postgresql"})
    class BothProfilesActiveTest {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("@Primary가 적용된 PostgresqlDescriptorCustomRepositoryImpl이 기본 빈으로 선택된다")
        void whenBothProfilesActive_thenPrimaryPostgresqlImplIsSelected() {
            DescriptorCustomRepository repository = context.getBean(DescriptorCustomRepository.class);

            assertThat(repository).isInstanceOf(PostgresqlDescriptorCustomRepositoryImpl.class);
        }

        @Test
        @DisplayName("두 구현체 빈이 모두 컨텍스트에 등록된다")
        void whenBothProfilesActive_thenBothImplsAreRegistered() {
            assertThat(context.getBean(OracleDescriptorCustomRepositoryImpl.class)).isNotNull();
            assertThat(context.getBean(PostgresqlDescriptorCustomRepositoryImpl.class)).isNotNull();
        }
    }
}
