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

        // UG-268: 다른 조건 DTO(Face/Match/Project)와 동일한 1~1000 제약을 맞춘다.
        // 하한이 필요한 이유는 page=0 이면 GetFeatureListUseCase 의 offset 이 음수가 되어
        // 그대로 querydsl 로 넘어가기 때문이다. 상한은 int 오버플로(page*pageSize)를 막는다.
        //
        // 주의: 상한 1000 이 featureType=ALL 경로의 메모리 문제를 해결하지는 못한다.
        // buildAllResult 는 offset+pageSize 행을 face/palm 양쪽에서 각각 읽어 메모리에서
        // 병합하므로, 제약 안에서도 page=1000&pageSize=1000 이면 최대 200만 행을 적재한다.
        // 근본 해결(keyset 페이징 등)은 UG-269 로 분리했다.
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
