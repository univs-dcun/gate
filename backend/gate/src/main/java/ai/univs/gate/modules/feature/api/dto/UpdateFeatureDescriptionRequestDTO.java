package ai.univs.gate.modules.feature.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

/**
 * 특징점 메모(description) 수정 요청 (UG-376). 메모만 바꾼다 — 특징점(사진)은 바꿀 수 없다(UG-332, 바꾸려면 삭제 후 재등록).
 * 길이 상한은 등록과 같다.
 *
 * <p>{@code description} 키는 필수다. 지우려면 빈 문자열을 보낸다. 생략을 「지우기」로 받으면 필드 이름 오타({@code desc}·{@code memo})가
 * 조용히 메모를 지운다 — 알 수 없는 키는 무시되기 때문이다 (반박 리뷰 M).
 */
public record UpdateFeatureDescriptionRequestDTO(
        @Schema(description = SwaggerDescriptions.FEATURE_DESCRIPTION_UPDATE, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "REQUIRED_DESCRIPTION")
        @Length(max = 1000, message = "INVALID_DESCRIPTION_LENGTH")
        String description
) {
}
