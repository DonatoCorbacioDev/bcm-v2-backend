/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.StreamReadFeature;

/**
 * JSON read one way only (OWASP ASVS 4.2, ambiguous requests): a body with the
 * same key twice, {"username":"a","username":"b"}, is refused (400) instead of
 * Jackson keeping the last value silently. Two readers of the same request, a
 * proxy or a log that keeps the first, the application that keeps the last,
 * would otherwise see two different requests.
 */
@Configuration
public class JsonStrictnessConfig {

    @Bean
    JsonMapperBuilderCustomizer rejectDuplicateKeys() {
        return builder -> builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
    }
}
