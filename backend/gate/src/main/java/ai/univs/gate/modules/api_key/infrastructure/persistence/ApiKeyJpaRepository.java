package ai.univs.gate.modules.api_key.infrastructure.persistence;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiKeyJpaRepository extends JpaRepository<ApiKey, Long> {

    ApiKey save(ApiKey apiKey);

    Optional<ApiKey> findByProjectIdAndIsActive(Long projectId, boolean isActive);

    Optional<ApiKey> findByApiKeyAndIsActive(String apiKey, boolean isActive);

    boolean existsByApiKey(String apiKey);
}
