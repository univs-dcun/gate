package ai.univs.match.api.dto;

import ai.univs.match.application.input.RegisterWithFaceIdInput;
import ai.univs.match.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record RegisterWithFaceIdRequestDTO(
        @Schema(description = SwaggerDescriptions.BRANCH_NAME, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_BRANCH_NAME")
        @Length(max = 255, message = "INVALID_BRANCH_NAME_LENGTH")
        String branchName,

        @Schema(description = SwaggerDescriptions.FACE_ID, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_FACE_ID")
        @Length(max = 255, message = "INVALID_FACE_ID_LENGTH")
        String faceId,

        @Schema(description = SwaggerDescriptions.DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_DESCRIPTOR")
        String descriptor
) {

        public RegisterWithFaceIdInput toRegisterWithFaceIdInput() {
                return new RegisterWithFaceIdInput(branchName, faceId, descriptor);
        }
}
