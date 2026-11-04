package com.donatodev.bcm_backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The test profile switches springdoc off, so every other test would start
 * fine even with a springdoc release that doesn't fit the Spring Boot in
 * use — which is how springdoc 2.x on Spring Boot 4 crashed the real app on
 * startup while all tests were green. This one starts the context with the
 * API docs on, as production does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
})
@DisplayName("API docs start with the application")
class ApiDocsAvailableTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api-docs serves the OpenAPI document")
    void servesOpenApiDocument() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }
}
