package ai.univs.gate.facade.feature.application.result;

import java.time.LocalDateTime;

public record FeatureItemResult(
        String featureType,
        Long featureSeq,
        String description,
        String imageUrl,
        String featureId,
        LocalDateTime createdAt
) {}
