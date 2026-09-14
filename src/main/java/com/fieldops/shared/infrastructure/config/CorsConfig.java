package com.fieldops.shared.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS configuration driven by the {@code app.cors.allowed-origins} property.
 *
 * <p>In production, this property must list only the actual front-end origins.
 * The wildcard {@code "*"} is intentionally never used in order to comply
 * with the project's security requirements.</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:3000}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
