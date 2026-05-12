package ai.univs.gate.modules.user.api.dto;

import ai.univs.gate.modules.user.application.result.UserResult;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

import static ai.univs.gate.shared.utils.DateTimeUtil.fromUtc;

public record UserResponseDTO(
        @Schema(description = SwaggerDescriptions.USER_ID)
        Long userId,

        @Schema(description = SwaggerDescriptions.USER_DESCRIPTION)
        String userDescription,

        @Schema(description = "사용자 이름")
        String username,

        @Schema(description = SwaggerDescriptions.FACE_ID)
        String faceId,

        @JsonIgnore
        @Hidden
        @Schema(description = SwaggerDescriptions.FACE_IMAGE_PATH)
        String faceImagePath,

        @Schema(description = SwaggerDescriptions.CHECK_LIVENESS)
        Boolean checkLiveness,

        @Schema(description = SwaggerDescriptions.CREATED_AT)
        LocalDateTime createdAt,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid
) {

    public static UserResponseDTO from(UserResult result, String timezone) {
        return new UserResponseDTO(
                result.userId(),
                result.description(),
                result.username(),
                result.faceId(),
                result.faceImagePath(),
                result.checkLiveness(),
                fromUtc(result.createdAt(), timezone),
                result.transactionUuid());
    }
}
