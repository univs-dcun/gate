package ai.univs.gate.modules.palm_feature.api.dto;

import ai.univs.gate.modules.palm_feature.application.input.PalmFeatureQuery;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;

@ParameterObject
public record PalmFeatureSelectCondition(
        @Schema(description = SwaggerDescriptions.PAGE, defaultValue = "1")
        int page,

        @Schema(description = SwaggerDescriptions.PAGE_SIZE, defaultValue = "20")
        int pageSize,

        @Schema(description = SwaggerDescriptions.PALM_KEYWORD)
        String userKeyword,

        @Schema(description = SwaggerDescriptions.IS_DELETED)
        Boolean isDeleted,

        @Schema(description = SwaggerDescriptions.SELECT_START_DATE)
        String startDate,

        @Schema(description = SwaggerDescriptions.SELECT_END_DATE)
        String endDate
) {

    public PalmFeatureQuery toQuery(Long accountId, String apiKey) {
        return new PalmFeatureQuery(
                accountId,
                apiKey,
                userKeyword,
                page,
                pageSize,
                isDeleted,
                startDate,
                endDate,
                "DESC",
                "palmFeatureId");
    }
}
