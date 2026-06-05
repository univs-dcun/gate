package ai.univs.gate.modules.match.api.dto;

import ai.univs.gate.shared.swagger.SwaggerDescriptions;
import ai.univs.gate.shared.web.dto.CustomPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MatchingHistoriesResponseDTO(
        @Schema(description = SwaggerDescriptions.MATCHING_HISTORY_LIST)
        List<MatchingHistoryResponseDTO> contents,
        @Schema(description = SwaggerDescriptions.PAGE_INFO)
        CustomPage page
) {
}
