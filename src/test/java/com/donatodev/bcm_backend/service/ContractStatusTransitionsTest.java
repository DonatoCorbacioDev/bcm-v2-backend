/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.donatodev.bcm_backend.entity.ContractStatus;

/** The edit-time status rules of docs/BUSINESS_RULES.md, every pair of them. */
class ContractStatusTransitionsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "DRAFT,     DRAFT,     true",
            "DRAFT,     ACTIVE,    false",
            "DRAFT,     EXPIRED,   false",
            "DRAFT,     CANCELLED, true",
            "ACTIVE,    DRAFT,     false",
            "ACTIVE,    ACTIVE,    true",
            "ACTIVE,    EXPIRED,   true",
            "ACTIVE,    CANCELLED, true",
            "EXPIRED,   DRAFT,     false",
            "EXPIRED,   ACTIVE,    true",
            "EXPIRED,   EXPIRED,   true",
            "EXPIRED,   CANCELLED, true",
            "CANCELLED, DRAFT,     false",
            "CANCELLED, ACTIVE,    false",
            "CANCELLED, EXPIRED,   false",
            "CANCELLED, CANCELLED, true"
    })
    @DisplayName("every move between two statuses is allowed or refused as the table says")
    void table(ContractStatus from, ContractStatus to, boolean allowed) {
        LocalDate futureEnd = TODAY.plusYears(1);
        if (allowed) {
            assertThatCode(() -> ContractStatusTransitions.requireAllowed(from, to, futureEnd, TODAY))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> ContractStatusTransitions.requireAllowed(from, to, futureEnd, TODAY))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(ContractStatusTransitions.allowedFrom(from).contains(to)).isEqualTo(allowed);
    }

    @Test
    @DisplayName("a draft is activated only by approval, and is told so")
    void draftNeedsApproval() {
        assertThatThrownBy(() -> ContractStatusTransitions.requireAllowed(
                ContractStatus.DRAFT, ContractStatus.ACTIVE, null, TODAY))
                .hasMessageContaining("approvazione");
    }

    @Test
    @DisplayName("an expired contract becomes active again only with an end date not already past")
    void reactivationIsARenewal() {
        assertThatThrownBy(() -> ContractStatusTransitions.requireAllowed(
                ContractStatus.EXPIRED, ContractStatus.ACTIVE, TODAY.minusDays(1), TODAY))
                .hasMessageContaining("nuova data di fine");
        assertThatCode(() -> ContractStatusTransitions.requireAllowed(
                ContractStatus.EXPIRED, ContractStatus.ACTIVE, TODAY, TODAY)).doesNotThrowAnyException();
        assertThatCode(() -> ContractStatusTransitions.requireAllowed(
                ContractStatus.EXPIRED, ContractStatus.ACTIVE, null, TODAY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a contract with no status yet may take any")
    void noStatusYet() {
        assertThat(ContractStatusTransitions.allowedFrom(null)).containsExactlyInAnyOrder(ContractStatus.values());
        assertThatCode(() -> ContractStatusTransitions.requireAllowed(null, ContractStatus.ACTIVE, null, TODAY))
                .doesNotThrowAnyException();
    }
}
