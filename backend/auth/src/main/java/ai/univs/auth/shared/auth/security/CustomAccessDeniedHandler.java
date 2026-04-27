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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final MessageService messageService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException
    ) throws IOException {
        log.error("AccessDeniedException: ", accessDeniedException);

        Errors errors = new Errors(
                ErrorType.INSUFFICIENT_PERMISSION.getCode(),
                ErrorType.INSUFFICIENT_PERMISSION.name(),
                messageService.getMessage(ErrorType.INSUFFICIENT_PERMISSION));

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("content-type", "application/json; charset=utf-8");
        response.getWriter().write(mapper.writeValueAsString(ResponseApi.fail(errors)));
        response.getWriter().flush();
        response.getWriter().close();
    }
}
