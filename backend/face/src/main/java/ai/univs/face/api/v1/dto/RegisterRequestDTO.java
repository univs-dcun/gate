package ai.univs.face.api.v1.dto;

import ai.univs.face.application.input.RegisterInput;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import ai.univs.face.shared.utils.ValidImageFile;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record RegisterRequestDTO(
        @Schema(description = SwaggerDescriptions.BRANCH_NAME, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_BRANCH_NAME")
        @Length(max = 255, message = "INVALID_BRANCH_NAME_LENGTH")
        String branchName,

        @Schema(description = SwaggerDescriptions.FACE_ID, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_FACE_ID")
        @Length(max = 255, message = "INVALID_FACE_ID_LENGTH")
        String faceId,

        @Schema(description = SwaggerDescriptions.FACE_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile faceImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.CLIENT_ID)
        String clientId,

        @Schema(description = SwaggerDescriptions.CHECK_LIVENESS)
        Boolean checkLiveness,

        @Schema(description = SwaggerDescriptions.CHECK_MULTI_FACE)
        Boolean checkMultiFace
) {

    public RegisterInput toV1RegisterInput() {
        return new RegisterInput(
                faceId,
                faceImage,
                branchName,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString(),
                StringUtils.hasText(clientId) ? clientId : "SYSTEM",
                checkLiveness != null ? checkLiveness : true,
                checkMultiFace != null ? checkMultiFace : true);
    }
}
