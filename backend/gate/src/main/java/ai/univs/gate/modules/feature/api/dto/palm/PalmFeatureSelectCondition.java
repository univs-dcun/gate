package ai.univs.gate.modules.feature.api.dto.palm;

import ai.univs.gate.modules.feature.application.input.palm.PalmFeatureQuery;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;

@ParameterObject
public record PalmFeatureSelectCondition(
        // UG-268: primitive 금지. 파라미터를 생략하면 0으로 바인딩돼 PageRequest.of(0, 0)이
        // "Page size must not be less than one"으로 터져 500이 난다. 문서는 OPTIONAL(기본값
        // 1/20)로 공표하고 있으므로 다른 조건 DTO와 동일하게 Integer + null 기본값으로 맞춘다.
        @Schema(description = SwaggerDescriptions.PAGE, defaultValue = "1")
        @Min(value = 1, message = "INVALID_PAGE_COUNT")
        @Max(value = 1000, message = "INVALID_PAGE_COUNT")
        Integer page,

        @Schema(description = SwaggerDescriptions.PAGE_SIZE, defaultValue = "20")
        @Min(value = 1, message = "INVALID_PAGE_COUNT")
        @Max(value = 1000, message = "INVALID_PAGE_COUNT")
        Integer pageSize,

        @Schema(description = SwaggerDescriptions.PALM_KEYWORD)
        String userKeyword,

        @Schema(description = SwaggerDescriptions.IS_DELETED)
        Boolean isDeleted,

        @Schema(description = SwaggerDescriptions.SELECT_START_DATE)
        String startDate,

        @Schema(description = SwaggerDescriptions.SELECT_END_DATE)
        String endDate
) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public PalmFeatureQuery toQuery(Long accountId, String apiKey) {
        return new PalmFeatureQuery(
                accountId,
                apiKey,
                userKeyword,
                page != null ? page : DEFAULT_PAGE,
                pageSize != null ? pageSize : DEFAULT_PAGE_SIZE,
                isDeleted,
                startDate,
                endDate,
                "DESC",
                "palmFeatureId");
    }
}
