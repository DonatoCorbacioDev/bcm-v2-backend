package com.donatodev.bcm_backend.controller;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.service.BusinessAreaService;
import com.donatodev.bcm_backend.service.CustomUserDetailsService;

/**
 * OWASP API8, over a real HTTP server: MockMvc never performs the container's
 * error dispatch to /error, where every error of a signed-in user — an unknown
 * path, a wrong method, a server bug — used to become a 401 (found 2026-11-13
 * against the deployed app), and the frontend logs out on any 401.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErrorsOverHttpTest {

    private static final String TOKEN = "signed-in";

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private BusinessAreaService businessAreaService;

    @BeforeEach
    void signIn() {
        User admin = new User("admin", "unused", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(jwtUtils.getUsernameFromToken(TOKEN)).thenReturn("admin");
        when(jwtUtils.getOrganizationIdFromToken(TOKEN)).thenReturn(uid(1));
        when(userDetailsService.loadUserByUsername(anyString(), any())).thenReturn(admin);
        when(jwtUtils.validateToken(TOKEN, admin)).thenReturn(true);
    }

    private ResponseEntity<String> call(HttpMethod method, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TOKEN);
        return restTemplate.exchange(path, method, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("an unknown path answers 404, not 401")
    void unknownPathIs404() {
        ResponseEntity<String> response = call(HttpMethod.GET, "/nonexistent");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a method the endpoint doesn't support answers 405, not 401")
    void wrongMethodIs405() {
        ResponseEntity<String> response = call(HttpMethod.DELETE, "/business-areas");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("a server bug answers 500 with a fixed message, not 401 nor the bug's detail")
    void serverBugIs500WithoutDetail() {
        when(businessAreaService.getAllAreas()).thenThrow(new IllegalStateException("secret internal detail"));

        ResponseEntity<String> response = call(HttpMethod.GET, "/business-areas");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Errore interno del server").doesNotContain("secret internal detail");
    }

    @Test
    @DisplayName("without a token a protected path still answers 401")
    void anonymousStill401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/business-areas", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
