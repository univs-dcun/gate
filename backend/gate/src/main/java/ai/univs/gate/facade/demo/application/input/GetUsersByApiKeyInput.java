package ai.univs.gate.facade.demo.application.input;

import ai.univs.gate.modules.user.application.input.UserQuery;
import ai.univs.gate.shared.utils.CustomPageable;
import org.springframework.util.StringUtils;

public record GetUsersByApiKeyInput(
        String apiKey,
        String userKeyword,
        int page,
        int pageSize,
        Boolean isDeleted,
        String startDate,
        String endDate,
        String timezone
) {

    public UserQuery toUserQuery() {
        return new UserQuery(
                null,
                apiKey,
                userKeyword,
                page,
                pageSize,
                isDeleted,
                startDate,
                endDate,
                "DESC",
                "userId"
        );
    }
}
