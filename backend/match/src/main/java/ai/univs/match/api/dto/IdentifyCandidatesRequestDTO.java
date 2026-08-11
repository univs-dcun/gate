package ai.univs.match.api.dto;

import ai.univs.match.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

/**
 * 1:N 후보 목록 매칭 요청 (UG-314).
 *
 * <p><b>임계치를 받지 않는다.</b> 이 서비스는 상위 k건을 유사도와 함께 돌려줄 뿐이고, 임계치
 * 판정은 face-service 가 한다.
 */
public record IdentifyCandidatesRequestDTO(
        @Schema(description = SwaggerDescriptions.BRANCH_NAME, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_BRANCH_NAME")
        @Length(max = 255, message = "INVALID_BRANCH_NAME_LENGTH")
        String branchName,

        @Schema(description = SwaggerDescriptions.DESCRIPTOR, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "REQUIRED_DESCRIPTOR")
        String descriptor,

        @Schema(description = SwaggerDescriptions.MAX_CANDIDATES, requiredMode = Schema.RequiredMode.REQUIRED)
        // 래퍼 타입이다. int 로 두면 값이 없을 때 0 이 되어 @Min 을 먼저 만나고, 클라이언트는
        // "안 보냈다" 가 아니라 "0 을 보냈다" 는 오류를 받는다 (palm pageSize 사고와 같은 형태).
        @NotNull(message = "REQUIRED_MAX_CANDIDATES")
        @Min(value = 1, message = "INVALID_MAX_CANDIDATES")
        @Max(value = 100, message = "INVALID_MAX_CANDIDATES")
        Integer maxCandidates
) {
}
