/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.donatodev.bcm_backend.entity.ContractStatus;

/**
 * Which status a contract may move to when someone edits it (OWASP ASVS 2.3.1:
 * business logic flows in order, no step skipped). Documented in
 * docs/BUSINESS_RULES.md; the edit form offers the same choices.
 * <ul>
 * <li>A draft becomes active only through approval
 * ({@link ContractWorkflowService#approve}), never by editing it.</li>
 * <li>Nothing goes back to draft: the workflow's rejection does that, for
 * contracts in review.</li>
 * <li>An expired contract becomes active again only as a renewal: with an end
 * date that has not passed already, or none.</li>
 * <li>A cancelled contract stays cancelled.</li>
 * </ul>
 * Creating a contract is not a transition: any status, so that contracts
 * already expired or cancelled can be imported. The nightly expiry and tacit
 * renewal ({@link ContractSchedulerService}) are the system's own moves.
 */
public final class ContractStatusTransitions {

    private static final Map<ContractStatus, Set<ContractStatus>> ALLOWED = new EnumMap<>(ContractStatus.class);

    static {
        ALLOWED.put(ContractStatus.DRAFT, EnumSet.of(ContractStatus.DRAFT, ContractStatus.CANCELLED));
        ALLOWED.put(ContractStatus.ACTIVE,
                EnumSet.of(ContractStatus.ACTIVE, ContractStatus.EXPIRED, ContractStatus.CANCELLED));
        ALLOWED.put(ContractStatus.EXPIRED,
                EnumSet.of(ContractStatus.EXPIRED, ContractStatus.ACTIVE, ContractStatus.CANCELLED));
        ALLOWED.put(ContractStatus.CANCELLED, EnumSet.of(ContractStatus.CANCELLED));
    }

    private ContractStatusTransitions() {
    }

    /** The statuses an edit may set on a contract now in {@code from} (all of them for a contract without one). */
    public static Set<ContractStatus> allowedFrom(ContractStatus from) {
        return from == null ? EnumSet.allOf(ContractStatus.class) : EnumSet.copyOf(ALLOWED.get(from));
    }

    /**
     * Refuses a move the rules above do not allow, with the reason in Italian
     * (it reaches the form as a 400).
     *
     * @param newEndDate the end date the same edit sets
     * @param today      the date it is checked against
     */
    public static void requireAllowed(ContractStatus from, ContractStatus to, LocalDate newEndDate, LocalDate today) {
        if (from == null || to == null || from == to) {
            return;
        }
        if (!ALLOWED.get(from).contains(to)) {
            throw new IllegalArgumentException(reason(from, to));
        }
        if (from == ContractStatus.EXPIRED && to == ContractStatus.ACTIVE
                && newEndDate != null && newEndDate.isBefore(today)) {
            throw new IllegalArgumentException(
                    "Per riattivare un contratto scaduto indica una nuova data di fine non già passata");
        }
    }

    private static String reason(ContractStatus from, ContractStatus to) {
        if (from == ContractStatus.DRAFT && to == ContractStatus.ACTIVE) {
            return "Una bozza diventa attiva solo con l'approvazione: inviala in revisione";
        }
        if (to == ContractStatus.DRAFT) {
            return "Un contratto non può tornare in bozza";
        }
        if (from == ContractStatus.CANCELLED) {
            return "Un contratto annullato non può cambiare stato";
        }
        return "Cambio di stato non consentito";
    }
}
