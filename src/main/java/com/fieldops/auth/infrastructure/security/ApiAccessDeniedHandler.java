package com.fieldops.auth.infrastructure.security;
import tools.jackson.databind.json.JsonMapper;
import com.fieldops.shared.infrastructure.filter.RequestIdFilter;
import com.fieldops.shared.presentation.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {
    private final JsonMapper objectMapper;
    public ApiAccessDeniedHandler(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        ApiError body = new ApiError(
                Instant.now(),
                HttpServletResponse.SC_FORBIDDEN,
                "AUTH_FORBIDDEN",
                "You do not have permission to access this resource",
                request.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY),
                List.of()
        );
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
