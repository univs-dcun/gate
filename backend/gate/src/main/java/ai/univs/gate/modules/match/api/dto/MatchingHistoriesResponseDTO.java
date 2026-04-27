package ai.univs.gate.modules.match.api.dto;

import ai.univs.gate.shared.web.dto.CustomPage;

import java.util.List;

public record MatchingHistoriesResponseDTO(
        List<MatchingHistoryResponseDTO> contents,
        CustomPage page
) {
}
