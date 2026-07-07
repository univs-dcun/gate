package ai.univs.gate.modules.feature.application.result.match;

import ai.univs.gate.shared.usecase.result.CustomPageResult;
import ai.univs.gate.shared.web.dto.CustomPage;

import java.util.List;

public record MatchHistoriesResult(
        List<MatchHistoryResult> results,
        CustomPageResult page
) {
}
