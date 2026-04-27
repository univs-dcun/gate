package ai.univs.auth.shared.auth.security;

import ai.univs.auth.shared.web.dto.Errors;
import ai.univs.auth.shared.web.dto.ResponseApi;
import ai.univs.auth.shared.web.enums.ErrorType;
import ai.univs.auth.support.message.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class DelegatedAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final MessageService messageService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException
    ) throws IOException {
        log.error("AuthenticationException: ", authException);

        Errors errors = new Errors(
                ErrorType.RETRY_USER_AUTHENTICATION.getCode(),
                ErrorType.RETRY_USER_AUTHENTICATION.name(),
                messageService.getMessage(ErrorType.RETRY_USER_AUTHENTICATION));

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("content-type", "application/json; charset=utf-8");
        response.getWriter().write(mapper.writeValueAsString(ResponseApi.fail(errors)));
        response.getWriter().flush();
        response.getWriter().close();
    }
}
