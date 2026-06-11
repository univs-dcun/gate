package ai.univs.gate.facade.feature.infrastructure.persistence;

import java.time.LocalDateTime;

public record FeatureRow(
        String featureType,
        Long featureSeq,
        String description,
        String imagePath,
        String featureId,
        LocalDateTime createdAt
) {}
