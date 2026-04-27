package ai.univs.auth.shared.auth.security;

import ai.univs.auth.application.exception.InvalidAccessTokenException;
import ai.univs.auth.application.exception.TokenExpiredException;
import ai.univs.auth.application.service.JwtTokenProvider;
import ai.univs.auth.shared.web.dto.Errors;
import ai.univs.auth.shared.web.dto.ResponseApi;
import ai.univs.auth.shared.web.enums.ErrorType;
import ai.univs.auth.support.message.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String token = extractToken(request);

            if (token != null) {
                jwtTokenProvider.validateAccessToken(token);
                Long accountId = jwtTokenProvider.getAccountIdFromToken(token);

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        accountId.toString(), null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);

        } catch (TokenExpiredException e) {
            writeErrorResponse(response, ErrorType.EXPIRATION_TOKEN);
        } catch (InvalidAccessTokenException e) {
            writeErrorResponse(response, ErrorType.INVALID_ACCESS_TOKEN);
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorType errorType) throws IOException {
        Errors errors = new Errors(
                errorType.getCode(),
                errorType.name(),
                messageService.getMessage(errorType));

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ResponseApi.fail(errors)));
    }
}
