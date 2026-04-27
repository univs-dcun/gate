package ai.univs.face.api.v1.dto;

import ai.univs.face.application.input.VerifyByDescriptorInput;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;

import java.util.UUID;

public record VerifyByDescriptorRequestDTO(
        @Schema(description = SwaggerDescriptions.DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED)
        String descriptor,

        @Schema(description = SwaggerDescriptions.TARGET_DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED)
        String targetDescriptor,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.CLIENT_ID)
        String clientId
) {

    public VerifyByDescriptorInput toVerifyByDescriptorInput() {
        return new VerifyByDescriptorInput(
                descriptor,
                targetDescriptor,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString(),
                StringUtils.hasText(clientId) ? clientId : "SYSTEM");
    }
}
