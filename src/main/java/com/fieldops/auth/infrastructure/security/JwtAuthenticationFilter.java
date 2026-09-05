package com.fieldops.auth.infrastructure.security;
import com.fieldops.auth.application.JwtTokenService;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String AUTH_FAILURE_CODE_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".failureCode";
    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;
    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, UserRepository userRepository) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
    }
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim()
                : null;
        if (token == null || token.isBlank()) {
            reject(request, "AUTH_INVALID_TOKEN");
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Jwt jwt = jwtTokenService.decode(token);
            UUID userId = UUID.fromString(jwt.getSubject());
            User user = userRepository.findById(userId).orElse(null);
            Number tokenVersion = jwt.getClaim("ver");
            if (user == null || tokenVersion == null || tokenVersion.intValue() != user.getSessionVersion()) {
                reject(request, "AUTH_INVALID_TOKEN");
            } else if (user.getStatus() != UserStatus.ACTIVE) {
                reject(request, "AUTH_USER_INACTIVE");
            } else {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                        )
                );
            }
        } catch (JwtException | IllegalArgumentException ex) {
            reject(request, "AUTH_INVALID_TOKEN");
        }
        filterChain.doFilter(request, response);
    }
    private void reject(HttpServletRequest request, String code) {
        request.setAttribute(AUTH_FAILURE_CODE_ATTRIBUTE, code);
        SecurityContextHolder.clearContext();
    }
}
