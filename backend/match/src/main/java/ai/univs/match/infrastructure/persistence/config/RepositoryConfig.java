package ai.univs.match.infrastructure.persistence.config;

import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
import ai.univs.match.infrastructure.persistence.OracleDescriptorCustomRepositoryImpl;
import ai.univs.match.infrastructure.persistence.PostgresqlDescriptorCustomRepositoryImpl;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@RequiredArgsConstructor
public class RepositoryConfig {

    private final EntityManager entityManager;

    @Bean
    @Profile("oracle")
    public DescriptorCustomRepository oracleDescriptorCustomRepository() {
        return new OracleDescriptorCustomRepositoryImpl(this.entityManager);
    }

    @Primary
    @Bean
    @Profile("postgresql")
    public DescriptorCustomRepository postgresqlDescriptorCustomRepository() {
        return new PostgresqlDescriptorCustomRepositoryImpl(this.entityManager);
    }
}
