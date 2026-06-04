package ai.univs.gate.facade.media.application.result;

import java.time.LocalDateTime;

public record MediaItemResult(
        String mediaType,
        Long mediaId,
        String description,
        String imageUrl,
        String fid,
        LocalDateTime createdAt
) {}
