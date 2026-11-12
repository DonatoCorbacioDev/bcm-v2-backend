/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.config;

import java.io.IOException;
import java.net.HttpURLConnection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * The single RestTemplate bean in this app, used only by MlProxyService to
 * call the ML (FastAPI) service. The read timeout must stay above FastAPI's
 * own OLLAMA_TIMEOUT (120s by default, see bcm-v2-ml's config.py) for the
 * endpoints that call Ollama (/agent/insights, /agent/ask, /clause-risk-
 * analysis) — otherwise a slow-but-successful LLM call gets abandoned here
 * with a 503 before FastAPI ever gets a chance to return its own result or
 * its own timeout error.
 * <p>
 * Redirects are never followed (OWASP API7): the JDK follows them on a GET by
 * default, and a redirect is how a request meant for the ML service could be
 * sent somewhere else. {@code MlProxyService} answers a 3xx with a 503.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(
            @Value("${ml.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${ml.read-timeout-ms:130000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new NoRedirectRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    /** A request factory whose connections never follow a redirect, whatever the method. */
    static final class NoRedirectRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }
}
