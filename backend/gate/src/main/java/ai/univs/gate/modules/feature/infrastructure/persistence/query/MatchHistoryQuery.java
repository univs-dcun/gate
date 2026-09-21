package ai.univs.gate.modules.feature.infrastructure.persistence.query;

import java.time.LocalDateTime;

public record MatchHistoryQuery(
        Long accountId,
        String apiKey,
        String matchingHistoryKeyword,
        String matchType,
        String featureType,
        String matchResultType,
        Integer page,
        Integer pageSize,
        boolean hasDate,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        String direction,
        String sortBy,
        /** UG-326: 삭제 이력 포함 여부. matchType=ALL 일 때만 의미가 있다 (DELETE 를 명시하면 항상 포함). */
        boolean includeDeletions
) {}