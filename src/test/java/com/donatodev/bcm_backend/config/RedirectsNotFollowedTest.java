package com.donatodev.bcm_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * OWASP API7: no HTTP client of the backend follows a redirect, so a service
 * answering 302 can't send the request to another destination. A real local
 * server, because the redirect is followed (or not) inside the JDK connection.
 */
@SpringBootTest
@ActiveProfiles("test")
class RedirectsNotFollowedTest {

    @Autowired private RestTemplate mlRestTemplate;
    @Autowired private RestClient.Builder restClientBuilder;

    private HttpServer server;
    private final AtomicInteger targetHits = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/start", exchange -> {
            exchange.getResponseHeaders().add("Location", "/target");
            reply(exchange, 302, "moved");
        });
        server.createContext("/target", exchange -> {
            targetHits.incrementAndGet();
            reply(exchange, 200, "internal secret");
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/start";
    }

    @Test
    @DisplayName("the ML service client stops at the 302, even on a GET (the JDK default would follow)")
    void mlClientStopsAtRedirect() {
        var response = mlRestTemplate.getForEntity(url(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(302));
        assertThat(targetHits).hasValue(0);
    }

    @Test
    @DisplayName("Spring Boot's clients (Spring AI's, for Ollama) stop at the 302 too")
    void bootClientsStopAtRedirect() {
        HttpStatusCode status = restClientBuilder.build().get().uri(url())
                .exchange((request, response) -> response.getStatusCode());

        assertThat(status).isEqualTo(HttpStatusCode.valueOf(302));
        assertThat(targetHits).hasValue(0);
    }
}
