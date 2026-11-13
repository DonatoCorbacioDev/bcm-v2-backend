package com.donatodev.bcm_backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

/**
 * OWASP API10: no call to a service the backend depends on waits forever. A
 * real local server that never answers, because the timeout lives inside the
 * HTTP client. The read timeout is shortened here so the test stays fast; the
 * production values are checked as configured.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.http.clients.read-timeout=1s")
class OutboundTimeoutsTest {

    @Autowired private RestClient.Builder restClientBuilder;
    @Autowired private Environment environment;
    @Autowired(required = false) private JavaMailSenderImpl mailSender;

    private HttpServer server;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
    }

    @Test
    @DisplayName("Spring Boot's clients (Spring AI's, for Ollama) give up on a server that never answers")
    void bootClientsTimeOut() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";
        long start = System.nanoTime();

        assertThatThrownBy(() -> restClientBuilder.build().get().uri(url).retrieve().toBodilessEntity())
                .isInstanceOf(ResourceAccessException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("the application gives Ollama five seconds to connect and two minutes to answer")
    void configuredHttpTimeouts() throws IOException {
        Properties configured = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            configured.load(in);
        }
        assertThat(configured.getProperty("spring.http.clients.connect-timeout")).isEqualTo("5s");
        assertThat(configured.getProperty("spring.http.clients.read-timeout")).isEqualTo("120s");
    }

    @Test
    @DisplayName("the mail server gets ten seconds per step, and TLS is required, not just offered")
    void mailTimeoutsAndRequiredTls() {
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.connectiontimeout")).isEqualTo("10000");
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.timeout")).isEqualTo("10000");
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.writetimeout")).isEqualTo("10000");
        assertThat(environment.getProperty("spring.mail.properties.mail.smtp.starttls.required")).isEqualTo("true");
        if (mailSender != null) {
            assertThat(mailSender.getJavaMailProperties())
                    .containsEntry("mail.smtp.timeout", "10000")
                    .containsEntry("mail.smtp.starttls.required", "true");
        }
    }
}
