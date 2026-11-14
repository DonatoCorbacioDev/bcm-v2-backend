package com.donatodev.bcm_backend.controller;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.service.BusinessAreaService;
import com.donatodev.bcm_backend.service.CustomUserDetailsService;

/**
 * OWASP REST Security Cheat Sheet, over a real HTTP server: only the methods
 * and formats an endpoint declares are accepted, a request the server cannot
 * read is refused with a fixed message that says nothing about the parser or
 * the code behind it, and every API answer carries the headers that keep it
 * out of caches, frames and content sniffing.
 * <p>
 * A real server, not MockMvc: TRACE is refused by the servlet container
 * before Spring sees it, and MockMvc, which calls the DispatcherServlet
 * directly, would echo the request back with 200. The token checks are
 * {@code JwtOverHttpTest}'s subject; here a signed-in caller is simulated.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RestSecurityOverHttpTest {

    private static final String TOKEN = "signed-in";
    /** What a refused body must never reveal: the parser, the Java types, the input echoed back. */
    private static final String[] INTERNALS = {"Jackson", "JSON parse", "com.donatodev", "java.", "LocalDate", "<a/>"};

    @Autowired private TestRestTemplate restTemplate;

    @MockitoBean private JwtUtils jwtUtils;
    @MockitoBean private CustomUserDetailsService userDetailsService;
    @MockitoBean private BusinessAreaService businessAreaService;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void signIn() {
        User admin = new User("admin", "unused", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(jwtUtils.getUsernameFromToken(TOKEN)).thenReturn("admin");
        when(jwtUtils.getOrganizationIdFromToken(TOKEN)).thenReturn(uid(1));
        when(userDetailsService.loadUserByUsername(anyString(), any())).thenReturn(admin);
        when(jwtUtils.validateToken(TOKEN, admin)).thenReturn(true);
        when(businessAreaService.getAllAreas()).thenReturn(List.of());
    }

    @Test
    @DisplayName("TRACE is refused: 405, the request is not echoed back")
    void traceIsRefused() throws Exception {
        HttpResponse<String> response = send(request("/business-areas").method("TRACE", BodyPublishers.noBody()));

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.body()).doesNotContain("Bearer").doesNotContain(TOKEN);
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("a body in a format the endpoint doesn't take: 415, fixed message")
    @CsvSource({"application/xml, <a/>", "text/plain, plain text"})
    void unsupportedBodyFormat(String contentType, String body) throws Exception {
        HttpResponse<String> response = send(request("/contracts")
                .header("Content-Type", contentType).POST(BodyPublishers.ofString(body)));

        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.body()).contains("Formato della richiesta non supportato").doesNotContain(INTERNALS);
    }

    @Test
    @DisplayName("an answer in a format the API doesn't produce: 406, empty body")
    void unsupportedAnswerFormat() throws Exception {
        HttpResponse<String> response = send(request("/business-areas").header("Accept", "application/xml").GET());

        assertThat(response.statusCode()).isEqualTo(406);
        assertThat(response.body()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("a body the server cannot read: 400 with a fixed message, no parser detail")
    @CsvSource(delimiter = '|', value = {
            "{\"title\":",
            "{\"startDate\":\"ieri\",\"endDate\":\"domani\"}",
            "{\"autoRenew\":{\"nested\":true}}",
    })
    void unreadableBody(String body) throws Exception {
        HttpResponse<String> response = send(request("/contracts")
                .header("Content-Type", "application/json").POST(BodyPublishers.ofString(body)));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Corpo della richiesta malformato o illeggibile").doesNotContain(INTERNALS);
    }

    @Test
    @DisplayName("every API answer is JSON, never cached, never framed, never sniffed")
    void securityHeaders() throws Exception {
        HttpResponse<String> response = send(request("/business-areas").GET());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/json"));
        assertThat(response.headers().firstValue("Cache-Control")).hasValueSatisfying(
                value -> assertThat(value).contains("no-store"));
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(response.headers().firstValue("X-Powered-By")).isEmpty();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(restTemplate.getRootUri() + path))
                .header("Authorization", "Bearer " + TOKEN);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return http.send(request.build(), BodyHandlers.ofString());
    }
}
