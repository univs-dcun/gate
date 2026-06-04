package ai.univs.gate.facade.media.api.dto;

import ai.univs.gate.facade.media.application.result.MediaItemResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record MediaItemResponse(
        @Schema(description = "미디어 타입 [FACE | PALM]")
        String mediaType,

        @Schema(description = "미디어 식별 번호")
        Long mediaId,

        @Schema(description = "메모 (설명)")
        String description,

        @Schema(description = "이미지 URL")
        String imageUrl,

        @Schema(description = "FID (Face ID 또는 Palm ID)")
        String fid,

        @Schema(description = "등록 일시")
        LocalDateTime createdAt
) {

    public static MediaItemResponse from(MediaItemResult result, String timezone) {
        return new MediaItemResponse(
                result.mediaType(),
                result.mediaId(),
                result.description(),
                result.imageUrl(),
                result.fid(),
                fromUtc(result.createdAt(), timezone));
    }
}
