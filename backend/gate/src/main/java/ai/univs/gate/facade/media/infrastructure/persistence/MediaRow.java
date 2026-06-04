package ai.univs.gate.facade.media.infrastructure.persistence;

import java.time.LocalDateTime;

public record MediaRow(
        String mediaType,
        Long mediaId,
        String description,
        String imagePath,
        String fid,
        LocalDateTime createdAt
) {}
