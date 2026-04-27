package ai.univs.gate.modules.user.application.input;

import ai.univs.gate.shared.utils.CustomPageable;
import ai.univs.gate.shared.utils.DateTimeUtil;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public record UserQuery(
        Long accountId,
        String apiKey,
        String userKeyword,
        int page,
        int pageSize,
        Boolean isDeleted,
        String startDate,
        String endDate,
        String direction,
        String sortBy
) {

    public boolean hasDate() {
        return this.startDate != null && !this.startDate.isEmpty() && this.endDate != null && !this.endDate.isEmpty();
    }

    public LocalDateTime getStartDateTime(String timezone) {
        return DateTimeUtil.toStartOfDay(this.startDate, timezone);
    }

    public LocalDateTime getEndDateTime(String timezone) {
        return DateTimeUtil.toEndOfDay(this.endDate, timezone);
    }

    public Pageable getPageable() {
        return CustomPageable.of(page, pageSize);
    }
}
