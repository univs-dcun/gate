package ai.univs.gate.modules.feature.api.dto.face;

import ai.univs.gate.modules.feature.application.input.face.CreateFaceFeatureByDescriptorInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.utils.ValidDescriptor;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;

/**
 * descriptor 기반 특징점 얼굴 등록 요청 (UG-279).
 *
 * <p>이미지 기반인 {@code CreateFeatureRequestDTO} 와 분리했다. description 은 신규 API 에서 받지
 * 않는다.
 */
public record CreateFaceFeatureByDescriptorRequestDTO(
        @Schema(description = SwaggerDescriptions.DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED)
        @ValidDescriptor(message = "INVALID_DESCRIPTOR")
        String descriptor,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    public CreateFaceFeatureByDescriptorInput toInput(Long accountId, String apiKey) {
        return new CreateFaceFeatureByDescriptorInput(
                accountId,
                apiKey,
                descriptor,
                TransactionUtil.useOrCreate(transactionUuid));
    }
}
