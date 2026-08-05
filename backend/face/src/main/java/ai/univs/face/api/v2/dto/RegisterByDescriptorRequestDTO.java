package ai.univs.face.api.v2.dto;

import ai.univs.face.application.input.RegisterByDescriptorInput;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * descriptor 기반 얼굴 등록 요청 (UG-279).
 *
 * <p>이미지 기반인 {@link RegisterRequestDTO} 와 달리 {@code faceImage}/{@code checkLiveness}/
 * {@code checkMultiFace} 가 없다. descriptor 의 형식 검증은 호출 측(gate)에서 수행한다.
 */
public record RegisterByDescriptorRequestDTO(
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

    public RegisterByDescriptorInput toRegisterByDescriptorInput() {
        return new RegisterByDescriptorInput(
                branchName,
                descriptor,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString(),
                StringUtils.hasText(clientId) ? clientId : "SYSTEM");
    }
}
