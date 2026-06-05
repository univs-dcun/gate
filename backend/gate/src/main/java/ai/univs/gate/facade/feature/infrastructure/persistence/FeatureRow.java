package ai.univs.gate.facade.feature.infrastructure.persistence;

import java.time.LocalDateTime;

public record FeatureRow(
        String featureType,
        Long featureId,
        String description,
        String imagePath,
        String fid,
        LocalDateTime createdAt
) {}
