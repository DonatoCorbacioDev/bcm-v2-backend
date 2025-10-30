package com.donatodev.bcm_backend.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS (Cross-Origin Resource Sharing) configuration for the Business Contracts Manager API.
 * <p>
 * This configuration allows cross-origin requests from the frontend application
 * and is environment-aware for enhanced security in production.
 * 
 * @author Donato Corbacio
 * @version 1.0
 * @since 1.0.0
 */
@Configuration
public class CorsConfig {

    private final Environment environment;
    
    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * Configures CORS settings based on the active Spring profile.
     * <p>
     * <strong>Development:</strong> Allows localhost:3000 and common development ports<br>
     * <strong>Test:</strong> Allows all origins for testing flexibility<br>
     * <strong>Production:</strong> Restricts to configured frontend URL only
     *
     * @return CORS configuration source with environment-specific settings
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Configure allowed origins based on environment
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            // Test environment: Allow all origins for testing flexibility
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else if (environment.acceptsProfiles(Profiles.of("dev"))) {
            // Development environment: Allow common development URLs
            configuration.setAllowedOrigins(Arrays.asList(
                frontendBaseUrl,
                "http://localhost:3000",
                "http://localhost:3001",
                "http://127.0.0.1:3000"
            ));
        } else {
            // Production environment: Strict origin control
            configuration.setAllowedOrigins(List.of(frontendBaseUrl));
        }
        
        // Configure allowed methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        
        // Configure allowed headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
        ));
        
        // Configure exposed headers
        configuration.setExposedHeaders(Arrays.asList(
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials"
        ));
        
        // Allow credentials for authentication
        configuration.setAllowCredentials(true);
        
        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}