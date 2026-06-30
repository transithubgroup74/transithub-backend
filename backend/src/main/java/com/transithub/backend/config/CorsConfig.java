package com.transithub.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.*;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Open CORS so the dashboard works from anywhere — the hosted GitHub
        // Pages link AND when opened as a local file (file:// origin = "null").
        // allowCredentials=false lets us return Access-Control-Allow-Origin: *,
        // which any origin (including null/file://) accepts. The mobile app is
        // native and isn't subject to CORS, and admin endpoints are open, so
        // nothing here relies on credentials/cookies.
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}