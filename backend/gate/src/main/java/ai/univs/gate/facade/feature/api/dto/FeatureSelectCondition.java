package ai.univs.gate.facade.feature.api.dto;

import ai.univs.gate.facade.feature.application.input.FeatureListQuery;
import ai.univs.gate.facade.feature.domain.enums.FeatureQueryType;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;

@ParameterObject
public record FeatureSelectCondition(
        @Schema(description = SwaggerDescriptions.FEATURE_TYPE_ALL, defaultValue = "ALL")
        FeatureQueryType featureType,

        @Schema(description = SwaggerDescriptions.FEATURE_KEYWORD)
        String keyword,

        // UG-268: 상한이 없으면 featureType=ALL 경로가 face/palm 양쪽에서 offset+pageSize 행을
        // 모두 읽어 메모리에서 병합한다(GetFeatureListUseCase#buildAllResult). page 하한도 필요하다
        // — page=0 이면 offset 이 음수가 되어 그대로 querydsl 로 넘어간다.
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
