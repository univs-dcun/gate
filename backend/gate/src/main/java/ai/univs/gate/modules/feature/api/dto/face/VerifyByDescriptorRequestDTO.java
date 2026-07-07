package ai.univs.gate.modules.feature.api.dto.face;

import ai.univs.gate.modules.feature.application.input.face.VerifyByDescriptorInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.TransactionUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record VerifyByDescriptorRequestDTO(
        @Schema(description = SwaggerDescriptions.DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "INVALID_INPUT")
        String descriptor,

        @Schema(description = SwaggerDescriptions.TARGET_DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "INVALID_INPUT")
        String targetDescriptor,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid
) {

    public VerifyByDescriptorInput toVerifyByDescriptorInput(String apiKey, Long accountId) {
        return new VerifyByDescriptorInput(
                apiKey,
                accountId,
                descriptor,
                targetDescriptor,
                TransactionUtil.useOrCreate(transactionUuid));
    }
}
