package ai.univs.gate.modules.match.infrastructure.persistence.query;

import java.time.LocalDateTime;

public record MatchHistoryQuery(
        Long accountId,
        String apiKey,
        String matchingHistoryKeyword,
        String matchType,
        String matchResultType,
        Integer page,
        Integer pageSize,
        boolean hasDate,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String direction,
        String sortBy
) {
}
