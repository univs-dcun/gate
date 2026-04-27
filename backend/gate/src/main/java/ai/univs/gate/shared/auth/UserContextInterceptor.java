package ai.univs.gate.shared.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserContextInterceptor implements HandlerInterceptor {

    private static final String HEADER_ACCOUNT_ID = "X-Account-Id";
    private static final String HEADER_API_KEY = "X-Api-Key";
    private static final String HEADER_TIMEZONE = "Accept-TimeZone";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler
    ) {
        String accountId = request.getHeader(HEADER_ACCOUNT_ID);
        String apiKey = request.getHeader(HEADER_API_KEY);
        String timezone = request.getHeader(HEADER_TIMEZONE);

        log.info("#### Header info accountId: {}, apiKey: {}, timezone: {}", accountId, apiKey, timezone);

        UserContext context = UserContext.builder()
                .accountId(accountId)
                .apiKey(apiKey)
                .timezone(StringUtils.hasText(timezone) ? timezone : "Asia/Seoul")
                .build();
        UserContext.set(context);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex
    ) {
        UserContext.clear();
    }
}
