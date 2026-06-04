package ai.univs.gate.modules.palm_media.api.dto;

import ai.univs.gate.modules.palm_media.application.result.PalmMediaResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record PalmMediaResponseDTO(
        @Schema(description = "팜 미디어 ID")
        Long palmMediaId,

        @Schema(description = "팜 설명")
        String description,

        @Schema(description = "사용자 이름")
        String username,

        @Schema(description = "팜 ID (AI 서비스 발급)")
        String palmId,

        @Schema(description = "팜 이미지 경로")
        String palmImagePath,

        @Schema(description = "라이브니스 검사 여부")
        Boolean checkLiveness,

        @Schema(description = "등록일시")
        LocalDateTime createdAt,

        @Schema(description = "트랜잭션 UUID")
        String transactionUuid
) {

    public static PalmMediaResponseDTO from(PalmMediaResult result, String timezone) {
        return new PalmMediaResponseDTO(
                result.palmMediaId(),
                result.description(),
                result.username(),
                result.palmId(),
                result.palmImagePath(),
                result.checkLiveness(),
                fromUtc(result.createdAt(), timezone),
                result.transactionUuid());
    }
}
