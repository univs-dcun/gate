package ai.univs.gate.shared.web.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.usecase.result.CustomPageResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record CustomPage(
        @Schema(description = SwaggerDescriptions.PAGE_SIZE)
        int pageSize,
        @Schema(description = SwaggerDescriptions.PAGE)
        int page,
        @Schema(description = SwaggerDescriptions.TOTAL_ELEMENTS)
        Long totalElements,
        @Schema(description = SwaggerDescriptions.TOTAL_PAGES)
        int totalPages,
        @Schema(description = SwaggerDescriptions.TOTAL_COUNT)
        Long totalCount
) {

    public static CustomPage from(CustomPageResult result) {
        return new CustomPage(
                result.pageSize(),
                result.page(),
                result.totalElements(),
                result.totalPages(),
                result.totalCount());
    }
}
