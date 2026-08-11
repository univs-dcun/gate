package ai.univs.gate.modules.feature.api.dto.face;

import ai.univs.gate.modules.feature.application.input.face.IdentifyCandidatesByDescriptorInput;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.utils.DescriptorValidator;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.shared.utils.ValidDescriptor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.hibernate.validator.constraints.Length;

/**
 * 특징점 기반 1:N 후보 목록 매칭 요청 (UG-314).
 *
 * <p>{@link IdentifyByDescriptorRequestDTO} 에 {@code threshold} 와 {@code maxCandidates} 가
 * 붙는다.
 */
public record IdentifyCandidatesByDescriptorRequestDTO(
        @Schema(description = SwaggerDescriptions.DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = DescriptorValidator.ENCODED_LENGTH, maxLength = DescriptorValidator.ENCODED_LENGTH)
        @ValidDescriptor(message = "INVALID_DESCRIPTOR")
        String descriptor,

        @Schema(description = SwaggerDescriptions.MATCH_THRESHOLD, requiredMode = Schema.RequiredMode.REQUIRED,
                example = "85.00")
        // 백분율이다. 응답의 similarity 와 같은 스케일이라야 클라이언트가 둘을 나란히 놓고
        // 판단할 수 있다. 0 은 막는다 — 모든 후보가 통과해 임계치의 의미가 사라진다.
        //
        // 알려진 간극 (반박 리뷰 지적): 생성되는 openapi.json 에는
        // "minimum: 0" 만 남고 배타 여부가 빠진다. 즉 스펙만 보면 0 이 합법으로 보인다.
        // springdoc 이 OpenAPI 3.1 로 낼 때 @DecimalMin 의 inclusive=false 도,
        // @Schema(exclusiveMinimum = true) 도 옮기지 못한다 (@Positive 로 바꾸면 하한 자체가
        // 통째로 사라져 더 나쁘다 — 셋 다 실제로 생성해 확인했다).
        //
        // 런타임 계약을 스펙 생성기의 한계에 맞춰 바꾸지는 않는다. 영향은 생성 클라이언트가
        // 0 을 보냈을 때 클라이언트단이 아니라 서버가 INVALID_THRESHOLD 로 막는다는 것뿐이고,
        // 고객이 실제로 읽는 docs/api/gate-api-docs.html 은 "0 초과" 로 정확하다.
        @NotNull(message = "REQUIRED_THRESHOLD")
        @DecimalMin(value = "0.0", inclusive = false, message = "INVALID_THRESHOLD")
        @DecimalMax(value = "100.0", message = "INVALID_THRESHOLD")
        BigDecimal threshold,

        @Schema(description = SwaggerDescriptions.MAX_CANDIDATES, defaultValue = "1", example = "10",
                minimum = "1", maximum = "100")
        // 안 보내면 1 이다 — 기존 1:N 과 같은 모양이 된다. 상한이 없으면 갤러리 전체를 한 번에
        // 끌어올 수 있어 반드시 막는다 (palm 목록 조회 pageSize 사고와 같은 형태).
        @Min(value = 1, message = "INVALID_MAX_CANDIDATES")
        @Max(value = 100, message = "INVALID_MAX_CANDIDATES")
        Integer maxCandidates,

        @Schema(description = SwaggerDescriptions.TRANSACTION_UUID)
        @Length(max = 36, message = "INVALID_TRANSACTION_UUID_LENGTH")
        String transactionUuid
) {

    private static final int DEFAULT_MAX_CANDIDATES = 1;

    public IdentifyCandidatesByDescriptorInput toIdentifyCandidatesByDescriptorInput(
            Long accountId, String apiKey) {
        return new IdentifyCandidatesByDescriptorInput(
                accountId,
                apiKey,
                descriptor,
                threshold,
                maxCandidates == null ? DEFAULT_MAX_CANDIDATES : maxCandidates,
                TransactionUtil.useOrCreate(transactionUuid));
    }
}
