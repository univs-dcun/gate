package ai.univs.gate.modules.feature.api.dto.face;

import ai.univs.gate.modules.feature.application.input.face.VerifyByDescriptorInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.utils.DescriptorValidator;
import ai.univs.gate.shared.utils.ValidDescriptor;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;

/**
 * descriptor 기반 1:1 확인 요청.
 *
 * <p>UG-279: 원래 {@code @NotBlank} 만 있었다. 그런데 이 엔드포인트도 값이 그대로
 * match-server 로 흘러가므로, {@code "zz"} 같은 입력이 {@code DescriptorDetail.from} 에서
 * {@code IllegalArgumentException} → 500 이 됐다. 신규 descriptor API 에 도입한
 * {@link ValidDescriptor} 를 같은 이유로 여기에도 적용한다 — 이 티켓이 해당 UseCase 를
 * (이력 저장 추가로) 이미 수정하고 있고, "descriptor 를 게이트에서 끊는다" 는 목표가
 * 정작 기존 유일한 descriptor 엔드포인트에서만 안 지켜지는 상태를 남길 이유가 없다.
 *
 * <p>{@code transactionUuid} 의 {@code @Length} 도 이때 함께 채웠다. match_history 에 쓰는
 * 다른 모든 요청 DTO 에는 있는데 이 DTO 에만 없어서, 37자 이상이면 400 이 아니라
 * {@code VARCHAR(36)} INSERT 실패로 500 이 났다.
 */
public record VerifyByDescriptorRequestDTO(
        @Schema(description = SwaggerDescriptions.DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = DescriptorValidator.ENCODED_LENGTH, maxLength = DescriptorValidator.ENCODED_LENGTH)
        @ValidDescriptor(message = "INVALID_DESCRIPTOR")
        String descriptor,

        @Schema(description = SwaggerDescriptions.TARGET_DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = DescriptorValidator.ENCODED_LENGTH, maxLength = DescriptorValidator.ENCODED_LENGTH)
        @ValidDescriptor(message = "INVALID_DESCRIPTOR")
        String targetDescriptor,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    // UG-278: 인자 순서를 (accountId, apiKey) 로 통일한다. 다른 to*Input 은 모두 이 순서다.
    public VerifyByDescriptorInput toVerifyByDescriptorInput(Long accountId, String apiKey) {
        return new VerifyByDescriptorInput(
                accountId,
                apiKey,
                descriptor,
                targetDescriptor,
                TransactionUtil.useOrCreate(transactionUuid));
    }
}
