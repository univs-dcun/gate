package ai.univs.match.infrastructure.persistence.config;

import ai.univs.match.infrastructure.persistence.BranchCustomRepository;
import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
import ai.univs.match.infrastructure.persistence.OracleBranchCustomRepositoryImpl;
import ai.univs.match.infrastructure.persistence.OracleDescriptorCustomRepositoryImpl;
import ai.univs.match.infrastructure.persistence.PostgresqlBranchCustomRepositoryImpl;
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

    /** UG-374. 방언별 브랜치 생성(위반 없는 INSERT). 프로필 규칙은 위 특징점 저장소와 같다. */
    @Bean
    @Profile("oracle")
    public BranchCustomRepository oracleBranchCustomRepository() {
        return new OracleBranchCustomRepositoryImpl(this.entityManager);
    }

    @Primary
    @Bean
    @Profile("postgresql")
    public BranchCustomRepository postgresqlBranchCustomRepository() {
        return new PostgresqlBranchCustomRepositoryImpl(this.entityManager);
    }
}
