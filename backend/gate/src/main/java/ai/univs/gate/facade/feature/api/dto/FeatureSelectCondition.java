package ai.univs.gate.facade.feature.api.dto;

import ai.univs.gate.facade.feature.application.input.FeatureListQuery;
import ai.univs.gate.facade.feature.domain.enums.FeatureQueryType;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;

@ParameterObject
public record FeatureSelectCondition(
        @Schema(description = SwaggerDescriptions.FEATURE_TYPE_ALL, defaultValue = "ALL")
        FeatureQueryType featureType,

        @Schema(description = SwaggerDescriptions.FEATURE_KEYWORD)
        String keyword,

        @Schema(description = SwaggerDescriptions.PAGE, defaultValue = "1")
        Integer page,

        @Schema(description = SwaggerDescriptions.PAGE_SIZE, defaultValue = "10")
        Integer pageSize,

        @Schema(description = SwaggerDescriptions.IS_DELETED)
        Boolean isDeleted,

        @Schema(description = SwaggerDescriptions.SELECT_START_DATE)
        String startDate,

        @Schema(description = SwaggerDescriptions.SELECT_END_DATE)
        String endDate
) {

    public FeatureListQuery toQuery(Long accountId, String apiKey, String timezone) {
        return new FeatureListQuery(
                accountId,
                apiKey,
                featureType != null ? featureType : FeatureQueryType.ALL,
                keyword,
                page != null ? page : 1,
                pageSize != null ? pageSize : 10,
                isDeleted,
                startDate,
                endDate,
                timezone);
    }
}
