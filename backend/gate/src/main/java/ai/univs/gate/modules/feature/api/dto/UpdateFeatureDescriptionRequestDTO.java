package ai.univs.gate.modules.feature.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;

/**
 * 특징점 메모(description) 수정 요청 (UG-376). 메모만 바꾼다 — 특징점(사진)은 바꿀 수 없다(UG-332, 바꾸려면 삭제 후 재등록).
 * 길이 상한은 등록과 같다.
 */
public record UpdateFeatureDescriptionRequestDTO(
        @Schema(description = SwaggerDescriptions.FEATURE_DESCRIPTION_UPDATE)
        @Length(max = 1000, message = "INVALID_DESCRIPTION_LENGTH")
        String description
) {
}
