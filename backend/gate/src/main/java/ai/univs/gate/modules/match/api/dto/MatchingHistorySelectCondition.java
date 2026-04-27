package ai.univs.gate.modules.match.api.dto;

import ai.univs.gate.modules.match.infrastructure.persistence.query.MatchHistoryQuery;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.util.StringUtils;

import static ai.univs.gate.shared.utils.DateTimeUtil.toEndOfDay;
import static ai.univs.gate.shared.utils.DateTimeUtil.toStartOfDay;

public record MatchingHistorySelectCondition(
        @Schema(description = SwaggerDescriptions.MATCHING_KEYWORD)
        String matchingKeyword,

        @Schema(description = SwaggerDescriptions.MATCHING_HISTORY_TYPE, defaultValue = "ALL")
        @Pattern(regexp = "^(REGISTER|VERIFY|IDENTIFY|LIVENESS|ALL)$", message = "INVALID_MATCH_TYPE_CONDITION")
        String matchType,

        @Schema(description = SwaggerDescriptions.MATCHING_HISTORY_RESULT_TYPE, defaultValue = "SUCCESS")
        @Pattern(regexp = "^(SUCCESS|FAILURE|ALL)$", message = "INVALID_MATCHING_RESULT_SEARCH_CONDITION")
        String matchResultType,

        @Schema(description = SwaggerDescriptions.PAGE, defaultValue = "1")
        @Min(value = 1, message = "INVALID_PAGE_COUNT")
        @Max(value = 1000, message = "INVALID_PAGE_COUNT")
        Integer page,

        @Schema(description = SwaggerDescriptions.PAGE_SIZE, defaultValue = "10")
        @Min(value = 1, message = "INVALID_PAGE_COUNT")
        @Max(value = 1000, message = "INVALID_PAGE_COUNT")
        Integer pageSize,

        @Schema(description = SwaggerDescriptions.SELECT_START_DATE)
        String startDate,
        @Schema(description = SwaggerDescriptions.SELECT_END_DATE)
        String endDate,

        @Hidden
        String direction,
        @Hidden
        String sortBy
) {

        public MatchHistoryQuery toMatchingHistoryQuery(Long accountId, String apiKey) {
                UserContext userContext = UserContext.get();

                return new MatchHistoryQuery(
                        accountId,
                        apiKey,
                        matchingKeyword,
                        StringUtils.hasText(matchType) ? matchType : "ALL",
                        StringUtils.hasText(matchResultType) ? matchResultType : "SUCCESS",
                        page != null ? page : 1,
                        pageSize != null ? pageSize : 10,
                        StringUtils.hasText(startDate) && StringUtils.hasText(endDate),
                        StringUtils.hasText(startDate) ? toStartOfDay(startDate, userContext.getTimezone()) : null,
                        StringUtils.hasText(endDate) ? toEndOfDay(endDate, userContext.getTimezone()) : null,
                        !StringUtils.hasText(direction) ? "DESC" : direction,
                        !StringUtils.hasText(sortBy) ? "identifyTime" : sortBy);
        }
}
