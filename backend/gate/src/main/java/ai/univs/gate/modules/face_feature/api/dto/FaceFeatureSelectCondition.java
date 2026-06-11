package ai.univs.gate.modules.face_feature.api.dto;

import ai.univs.gate.modules.face_feature.application.input.FaceFeatureQuery;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.util.StringUtils;

public record FaceFeatureSelectCondition(
        @Schema(description = SwaggerDescriptions.USER_KEYWORD)
        String userKeyword,

        @Schema(description = SwaggerDescriptions.PAGE, defaultValue = "1")
        @Min(value = 1, message = "INVALID_PAGE_COUNT")
        @Max(value = 1000, message = "INVALID_PAGE_COUNT")
        Integer page,

        @Schema(description = SwaggerDescriptions.PAGE_SIZE, defaultValue = "10")
        @Min(value = 1, message = "INVALID_PAGE_COUNT")
        @Max(value = 1000, message = "INVALID_PAGE_COUNT")
        Integer pageSize,

        @Schema(description = SwaggerDescriptions.IS_DELETED)
        Boolean isDeleted,

        @Schema(description = SwaggerDescriptions.SELECT_START_DATE)
        String startDate,
        @Schema(description = SwaggerDescriptions.SELECT_END_DATE)
        String endDate,

        @Hidden
        String direction,

        @Hidden
        String sortBy
) {

    public FaceFeatureQuery toQuery(Long accountId, String apiKey) {
        return new FaceFeatureQuery(
                accountId,
                apiKey,
                userKeyword,
                page != null ? page : 1,
                pageSize != null ? pageSize : 10,
                isDeleted,
                startDate,
                endDate,
                !StringUtils.hasText(direction) ? "DESC" : direction,
                !StringUtils.hasText(sortBy) ? "faceFeatureId" : sortBy);
    }
}
