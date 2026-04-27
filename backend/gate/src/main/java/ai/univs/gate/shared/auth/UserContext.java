package ai.univs.gate.shared.auth;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserContext {

    private String apiKey;
    private String accountId;
    private String timezone;

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    public static void set(UserContext context) {
        CONTEXT.set(context);
    }

    public static UserContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public Long getAccountIdAsLong() {
        if (accountId == null) {
            return null;
        }

        try {
            return Long.parseLong(accountId);
        } catch (NumberFormatException e) {
            throw new CustomGateException(ErrorType.INTERNAL_SERVER_ERROR);
        }
    }
}
