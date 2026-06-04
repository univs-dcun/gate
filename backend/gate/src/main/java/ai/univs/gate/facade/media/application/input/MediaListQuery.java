package ai.univs.gate.facade.media.application.input;

import ai.univs.gate.facade.media.domain.enums.MediaQueryType;
import ai.univs.gate.shared.utils.DateTimeUtil;

import java.time.LocalDateTime;

public record MediaListQuery(
        Long accountId,
        String apiKey,
        MediaQueryType mediaType,
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
