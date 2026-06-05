package ai.univs.gate.facade.feature.application.result;

import java.time.LocalDateTime;

public record FeatureItemResult(
        String featureType,
        Long featureId,
        String description,
        String imageUrl,
        String fid,
        LocalDateTime createdAt
) {}
