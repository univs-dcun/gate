package ai.univs.palm.api.dto;

import ai.univs.palm.application.input.RegisterInput;
import ai.univs.palm.shared.swagger.SwaggerDescriptions;
import ai.univs.palm.shared.utils.ValidImageFile;
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

        @Schema(description = SwaggerDescriptions.PALM_IMAGE, requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidImageFile(message = "INVALID_FILE")
        MultipartFile palmImage,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.CLIENT_ID)
        String clientId,

        @Schema(description = SwaggerDescriptions.CHECK_LIVENESS)
        Boolean checkLiveness
) {

    public RegisterInput toV2RegisterInput() {
        return new RegisterInput(
                "",
                palmImage,
                branchName,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString(),
                StringUtils.hasText(clientId) ? clientId : "SYSTEM",
                checkLiveness != null ? checkLiveness : true);
    }
}
