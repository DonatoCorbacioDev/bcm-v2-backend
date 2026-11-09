/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import java.util.List;

/**
 * Contract terms read from a document's text by fixed rules (no AI model):
 * only what the text states explicitly, each with the sentence it comes from
 * so a person can check it before using it.
 *
 * @param documentId the document the text was read from
 * @param terms      the terms found; a term the text doesn't state, or
 *                   states in contradictory ways, is simply absent
 */
public record ContractTermsDTO(UUID documentId, List<Term> terms) {

    /** Which contract field a term fills. */
    public enum Field { AUTO_RENEW, NOTICE_DAYS, RENEWAL_MONTHS, START_DATE, END_DATE }

    /**
     * @param field     the contract field
     * @param value     machine value: "true"/"false", a number of days or
     *                  months, or an ISO date (yyyy-MM-dd)
     * @param asWritten how the text says it, e.g. "tre mesi" for 90 days
     * @param sentence  the sentence the value was read from
     */
    public record Term(Field field, String value, String asWritten, String sentence) {
    }
}
