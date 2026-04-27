package ai.univs.gate.modules.api_key.domain.repository;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;

import java.util.Optional;

public interface ApiKeyRepository {

    ApiKey save(ApiKey apiKey);

    Optional<ApiKey> findActiveByProjectId(Long projectId);

    Optional<ApiKey> findByApiKeyAndIsActiveTrue(String apiKey);

    boolean existsByApiKey(String apiKey);
}
