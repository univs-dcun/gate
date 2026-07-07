package ai.univs.gate.facade.feature.application.input;

import ai.univs.gate.facade.feature.domain.enums.FeatureQueryType;
import ai.univs.gate.shared.utils.DateTimeUtil;

import java.time.LocalDateTime;

public record FeatureListQuery(
        Long accountId,
        String apiKey,
        FeatureQueryType featureType,
        String keyword,
        int page,
        int pageSize,
        Boolean isDeleted,
        String startDate,
        String endDate,
        String timezone
) {

    public boolean hasDate() {
        return startDate != null && !startDate.isBlank()
                && endDate != null && !endDate.isBlank();
    }

    public LocalDateTime startDateTime() {
        return DateTimeUtil.toStartOfDay(startDate, timezone);
    }

    public LocalDateTime endDateTime() {
        return DateTimeUtil.toEndOfDay(endDate, timezone);
    }
}
