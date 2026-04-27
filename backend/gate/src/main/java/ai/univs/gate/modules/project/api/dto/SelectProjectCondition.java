package ai.univs.gate.modules.project.api.dto;

import ai.univs.gate.modules.project.application.input.ProjectQuery;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.util.StringUtils;

public record SelectProjectCondition(
        @Schema(description = SwaggerDescriptions.PROJECT_KEYWORD)
        String projectKeyword,

        @Schema(description = SwaggerDescriptions.PAGE, defaultValue = "1")
        @Min(value = 1, message = "INVALID_PAGE_COUNT")
        @Max(value = 1000, message = "INVALID_PAGE_COUNT")
        Integer page,

        @Schema(description = SwaggerDescriptions.PAGE_SIZE, defaultValue = "10")
        @Min(value = 1, message = "INVALID_PAGE_COUNT")
        @Max(value = 1000, message = "INVALID_PAGE_COUNT")
        Integer pageSize,

        @Hidden
        String direction,

        @Hidden
        String sortBy
) {

    public ProjectQuery toProjectQuery(Long accountId) {
        return new ProjectQuery(
                accountId,
                projectKeyword,
                page != null ? page : 1,
                pageSize != null ? pageSize : 10,
                !StringUtils.hasText(direction) ? "DESC" : direction,
                !StringUtils.hasText(sortBy) ? "createdAt" : sortBy);
    }
}
