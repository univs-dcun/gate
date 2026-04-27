package ai.univs.face.api.v1.dto;

import ai.univs.face.application.input.DeleteInput;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;

import java.util.UUID;

public record DeleteRequestDTO(
        @Schema(description = SwaggerDescriptions.BRANCH_NAME, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_BRANCH_NAME")
        @Length(max = 255, message = "INVALID_BRANCH_NAME_LENGTH")
        String branchName,

        @Schema(description = SwaggerDescriptions.FACE_ID, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_FACE_ID")
        @Length(max = 255, message = "INVALID_FACE_ID_LENGTH")
        String faceId,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.CLIENT_ID)
        String clientId
) {

    public DeleteInput toDeleteInput() {
        return new DeleteInput(
                branchName,
                faceId,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString(),
                StringUtils.hasText(clientId) ? clientId : "SYSTEM");
    }
}
