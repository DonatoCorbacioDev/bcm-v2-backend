package com.donatodev.bcm_backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.donatodev.bcm_backend.jwt.JwtAuthEntryPoint;
import com.donatodev.bcm_backend.jwt.JwtAuthenticationFilter;
import com.donatodev.bcm_backend.service.CustomUserDetailsService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String AUTH_WHITELIST = "/auth/**";

    private static final String[] SWAGGER_WHITELIST = {
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**",
        "/api-docs/**",
        "/api-docs",
        "/swagger-resources/**",
        "/webjars/**"
    };

    @Bean
    @SuppressWarnings("java:S4502") // see CSRF rationale below — no exploitable cross-site risk
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthEntryPoint jwtAuthEntryPoint,
            CustomUserDetailsService userDetailsService,
            AuthenticationManager authenticationManager) throws Exception {

        http
                .cors(Customizer.withDefaults())
                // CSRF disabled: every authenticated request still relies on the
                // Authorization header (Bearer access token), which the browser
                // never attaches automatically — that's what actually rules out CSRF.
                // The only cookie in play is the HttpOnly refresh_token (see
                // RefreshCookieFactory), scoped to /auth and SameSite=Lax, so it is
                // not sent on cross-site requests; a forged cross-site call could at
                // worst trigger a token refresh or a logout, neither state-changing
                // nor able to leak the new token back to the attacker (blocked by CORS).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(AUTH_WHITELIST, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/organizations/register").permitAll()
                .requestMatchers(SWAGGER_WHITELIST).permitAll()
                .requestMatchers(HttpMethod.POST, "/users/invite").hasRole("ADMIN")
                .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                .authenticationManager(authenticationManager)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
