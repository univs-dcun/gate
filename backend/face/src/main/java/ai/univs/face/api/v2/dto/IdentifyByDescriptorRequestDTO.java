package ai.univs.face.api.v2.dto;

import ai.univs.face.application.input.IdentifyByDescriptorInput;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * descriptor 기반 1:N 매칭 요청 (UG-279).
 *
 * <p>사유는 {@link RegisterByDescriptorRequestDTO} 와 같다.
 */
public record IdentifyByDescriptorRequestDTO(
        @Schema(description = SwaggerDescriptions.BRANCH_NAME, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_BRANCH_NAME")
        @Length(max = 255, message = "INVALID_BRANCH_NAME_LENGTH")
        String branchName,

        @Schema(description = SwaggerDescriptions.DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "INVALID_INPUT")
        String descriptor,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.CLIENT_ID)
        String clientId
) {

    public IdentifyByDescriptorInput toIdentifyByDescriptorInput() {
        return new IdentifyByDescriptorInput(
                branchName,
                descriptor,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString(),
                StringUtils.hasText(clientId) ? clientId : "SYSTEM");
    }
}
