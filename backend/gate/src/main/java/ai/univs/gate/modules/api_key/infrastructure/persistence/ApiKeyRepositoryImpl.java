package ai.univs.gate.modules.api_key.infrastructure.persistence;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.api_key.domain.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ApiKeyRepositoryImpl implements ApiKeyRepository {

    private final ApiKeyJpaRepository apiKeyJpaRepository;

    @Override
    public ApiKey save(ApiKey apiKey) {
        return apiKeyJpaRepository.save(apiKey);
    }

    @Override
    public Optional<ApiKey> findActiveByProjectId(Long projectId) {
        return apiKeyJpaRepository.findByProjectIdAndIsActive(projectId, true);
    }

    @Override
    public Optional<ApiKey> findByApiKeyAndIsActiveTrue(String apiKey) {
        return apiKeyJpaRepository.findByApiKeyAndIsActive(apiKey, true);
    }

    @Override
    public boolean existsByApiKey(String apiKey) {
        return apiKeyJpaRepository.existsByApiKey(apiKey);
    }
}
