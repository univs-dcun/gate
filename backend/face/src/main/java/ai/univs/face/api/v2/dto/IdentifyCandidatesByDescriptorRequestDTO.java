package ai.univs.face.api.v2.dto;

import ai.univs.face.application.input.IdentifyCandidatesByDescriptorInput;
import ai.univs.face.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.hibernate.validator.constraints.Length;
import org.springframework.util.StringUtils;

/**
 * 특징점 기반 1:N 후보 목록 매칭 요청 (UG-314).
 *
 * <p>{@code threshold} 는 <b>0.0 ~ 1.0 도메인 스케일</b>이다. 클라이언트 대면 백분율을 이
 * 스케일로 바꾸는 것은 gate 의 몫이라, 이 서비스는 백분율을 모른다.
 */
public record IdentifyCandidatesByDescriptorRequestDTO(
        @Schema(description = SwaggerDescriptions.BRANCH_NAME, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_BRANCH_NAME")
        @Length(max = 255, message = "INVALID_BRANCH_NAME_LENGTH")
        String branchName,

        @Schema(description = SwaggerDescriptions.DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "INVALID_INPUT")
        String descriptor,

        @Schema(description = SwaggerDescriptions.MATCH_THRESHOLD, requiredMode = Schema.RequiredMode.REQUIRED)
        // 0 을 막는다. 0 이면 모든 후보가 통과해 임계치의 의미가 사라진다.
        @NotNull(message = "INVALID_INPUT")
        @DecimalMin(value = "0.0", inclusive = false, message = "INVALID_INPUT")
        @DecimalMax(value = "1.0", message = "INVALID_INPUT")
        Double threshold,

        @Schema(description = SwaggerDescriptions.MAX_CANDIDATES, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "INVALID_INPUT")
        @Min(value = 1, message = "INVALID_INPUT")
        @Max(value = 100, message = "INVALID_INPUT")
        Integer maxCandidates,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        String transactionUuid,

        @Schema(description = SwaggerDescriptions.CLIENT_ID)
        String clientId
) {

    public IdentifyCandidatesByDescriptorInput toIdentifyCandidatesByDescriptorInput() {
        return new IdentifyCandidatesByDescriptorInput(
                branchName,
                descriptor,
                threshold,
                maxCandidates,
                StringUtils.hasText(transactionUuid) ? transactionUuid : UUID.randomUUID().toString(),
                StringUtils.hasText(clientId) ? clientId : "SYSTEM");
    }
}
