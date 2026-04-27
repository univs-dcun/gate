package ai.univs.gate.modules.match.api.dto;

import ai.univs.gate.modules.match.application.result.IdentifyResult;
import ai.univs.gate.modules.match.domain.enums.MatchType;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record IdentifyResponseDTO(
        @Schema(description = SwaggerDescriptions.MATCHING_HISTORY_ID)
        Long matchingHistoryId,

        @Schema(description = SwaggerDescriptions.PROJECT_ID)
        Long projectId,

        @Schema(description = SwaggerDescriptions.MATCHING_TYPE)
        MatchType matchType,

        @Schema(description = SwaggerDescriptions.MATCHING_TIME)
        LocalDateTime matchingTime,

        @Schema(description = SwaggerDescriptions.CHECK_LIVENESS)
        Boolean checkLiveness,

        @Schema(description = SwaggerDescriptions.MATCHING_SUCCESS)
        Boolean success,

        @Schema(description = SwaggerDescriptions.FACE_ID)
        String faceId,

        @Schema(description = SwaggerDescriptions.USER_ID)
        Long userId,

        @Schema(description = SwaggerDescriptions.USER_DESCRIPTION)
        String userDescription,

        @Schema(description = SwaggerDescriptions.SIMILARITY)
        BigDecimal similarity,

        @JsonIgnore
        @Hidden
        @Schema(description = SwaggerDescriptions.FACE_IMAGE_PATH)
        String faceImagePath,

        @JsonIgnore
        @Hidden
        @Schema(description = SwaggerDescriptions.MATCHING_FACE_IMAGE_PATH)
        String matchingFaceImagePath,

        @Schema(description = SwaggerDescriptions.MATCHING_FAILURE_TYPE)
        String failureType,

        @Schema(description = SwaggerDescriptions.MATCHING_FAILURE_REASON)
        String failureReason,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid
) {

        public static IdentifyResponseDTO from(IdentifyResult result,
                                               String failureReason,
                                               String timezone
        ) {
                return new IdentifyResponseDTO(
                        result.matchingHistoryId(),
                        result.projectId(),
                        result.matchType(),
                        fromUtc(result.matchingTime(), timezone),
                        result.checkLiveness(),
                        result.success(),
                        result.faceId(),
                        result.userId(),
                        result.userDescription(),
                        result.similarity(),
                        result.faceImagePath(),
                        result.matchingFaceImagePath(),
                        result.failureType(),
                        failureReason,
                        result.transactionUuid());
        }
}
