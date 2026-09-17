package com.fieldops.auth.infrastructure.security;
import tools.jackson.databind.json.JsonMapper;
import com.fieldops.shared.infrastructure.filter.RequestIdFilter;
import com.fieldops.shared.presentation.dto.ApiError;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final JsonMapper objectMapper;
    public ApiAuthenticationEntryPoint(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        String code = (String) request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_CODE_ATTRIBUTE);
        if (code == null) {
            code = "AUTH_AUTHENTICATION_REQUIRED";
        }
        String message = switch (code) {
            case "AUTH_USER_INACTIVE" -> "Your account is inactive or blocked. Contact an administrator";
            case "AUTH_INVALID_TOKEN" -> "Authentication token is invalid or expired";
            default -> "Authentication is required";
        };
        ApiError body = new ApiError(
                Instant.now(),
                HttpServletResponse.SC_UNAUTHORIZED,
                code,
                message,
                request.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY),
                List.of()
        );
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
