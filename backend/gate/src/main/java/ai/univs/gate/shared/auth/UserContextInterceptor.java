package ai.univs.gate.shared.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserContextInterceptor implements HandlerInterceptor {

    private static final String HEADER_ACCOUNT_ID = "X-Account-Id";
    private static final String HEADER_ACCOUNT_EMAIL = "X-Account-Email";
    private static final String HEADER_API_KEY = "X-Api-Key";
    private static final String HEADER_TIMEZONE = "Accept-TimeZone";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler
    ) {
        String accountId = request.getHeader(HEADER_ACCOUNT_ID);
        String email = request.getHeader(HEADER_ACCOUNT_EMAIL);
        String apiKey = request.getHeader(HEADER_API_KEY);
        String timezone = request.getHeader(HEADER_TIMEZONE);

        log.info("#### Header info accountId: {}, apiKey: {}, timezone: {}",
                accountId, maskApiKey(apiKey), timezone);

        if (StringUtils.hasText(accountId)) {
            MDC.put("accountId", accountId);
        }

        UserContext context = UserContext.builder()
                .accountId(accountId)
                .email(email)
                .apiKey(apiKey)
                .timezone(StringUtils.hasText(timezone) ? timezone : "Asia/Seoul")
                .build();
        UserContext.set(context);

        return true;
    }

    /**
     * API 키는 특징점 등록·매칭 전 기능의 인증 수단이다. 원문을 로그에 남기면 로그 열람 권한만으로
     * 남의 생체 API 를 호출할 수 있고, 온프레미스 납품 시 로그 묶음이 그대로 밖으로 나간다.
     * 요청 추적에는 앞자리만 있어도 충분하므로 뒤를 가린다. (UG-274 반박 리뷰)
     */
    private static String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return apiKey;
        }
        return apiKey.length() <= 8 ? "****" : apiKey.substring(0, 8) + "****";
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex
    ) {
        MDC.remove("accountId");
        UserContext.clear();
    }
}
