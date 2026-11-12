/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that costs far more than an ordinary request (OWASP API4):
 * a question to the language model, an embedding, a whole-organization export,
 * a file to parse. {@link CostlyOperationLimiter} lets each user call it only
 * so many times a minute, so one account (the public demo's included) can't
 * use up the CPU everyone shares.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CostlyOperation {

    Cost value();

    enum Cost {
        /** Runs the language model: the most expensive call there is. */
        LLM,
        /** Embeddings, exports, imports, file parsing, matching recomputation. */
        HEAVY
    }
}
