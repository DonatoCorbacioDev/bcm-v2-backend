/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * How the API answers a request that is not what it expects (OWASP ASVS 5.0
 * V4): a method an endpoint does not take, a body of another type, a body that
 * can be read two ways, a malformed one. Each gets its own 4xx with a fixed
 * Italian message, never a 500 and never the framework's own text; and every
 * error written by hand declares UTF-8.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiRequestHandlingTest {

    private static final String MALFORMED = "Corpo della richiesta malformato o illeggibile";

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("a method the endpoint does not take is refused with 405")
    void methodNotAllowed() throws Exception {
        mvc.perform(delete("/actuator/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Metodo non consentito"));
        mvc.perform(patch("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("a body of another type than JSON is refused with 415")
    void unsupportedContentType() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.TEXT_PLAIN).content("username=demo"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Formato della richiesta non supportato"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("username=demo&password=not-a-password"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("a body with the same key twice is refused with 400, never read as its last value")
    void duplicateKeys() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"someone\",\"username\":\"demo\",\"password\":\"not-a-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(MALFORMED));
    }

    @Test
    @DisplayName("a malformed body is refused with 400")
    void malformedBody() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(MALFORMED));
    }

    @Test
    @DisplayName("the 401 written by the entry point declares UTF-8, not the servlet's ISO-8859-1")
    void unauthorizedIsUtf8() throws Exception {
        String type = mvc.perform(get("/contracts")).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentType();
        assertThat(type.replace(" ", "")).isEqualToIgnoringCase("application/json;charset=UTF-8");
    }
}
