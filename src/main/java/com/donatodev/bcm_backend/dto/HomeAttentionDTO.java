/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What needs the user's attention, for the home page: one list, already in
 * order, and the counters computed from that same list so the two can never
 * disagree.
 *
 * @param dueSoonCount         contract items (notice deadline, expiry, renewal to check)
 * @param invoicesToMatchCount invoices still waiting to be matched to their contract
 * @param ibanAlertsCount      unpaid invoices asking to be paid on an IBAN other than the supplier's verified one
 * @param windowDays           how far ahead contract dates are looked at
 * @param items                the items, most urgent first
 */
public record HomeAttentionDTO(
        int dueSoonCount,
        int invoicesToMatchCount,
        int ibanAlertsCount,
        int windowDays,
        List<Item> items) {

    /** Kind of item; the frontend picks the sentence and the icon from it. */
    public enum Type {
        /** Tacit renewal: last day to give notice within the window. */
        NOTICE_DEADLINE,
        /** Contract that doesn't renew tacitly, ending within the window. */
        EXPIRY,
        /** Renews tacitly but notice or renewal length isn't stated, and the end date is near or passed. */
        RENEWAL_TO_CHECK,
        /** Invoice not matched to the contract yet (unmatched, suggested or supplier mismatch). */
        INVOICE_TO_MATCH,
        /** Unpaid invoice with an IBAN different from the supplier's verified one. Never carries the IBAN. */
        IBAN_CHANGED
    }

    /**
     * One thing to do.
     *
     * @param type             what kind of item it is
     * @param contractId       the contract it belongs to (the link target)
     * @param contractNumber   the contract number
     * @param counterpartyName the customer or supplier
     * @param projectName      the contract's project, if any
     * @param dueDate          the date that matters: notice deadline, end date, or invoice date
     * @param daysLeft         days from today to {@code dueDate} (negative when passed)
     * @param managerName      who follows the contract, or null if nobody
     * @param renewalMonths    for NOTICE_DEADLINE: how long it renews for if not cancelled
     * @param invoiceId        for invoice items: the invoice
     * @param invoiceNumber    for invoice items: its number
     * @param invoiceAmount    for invoice items: its taxable amount (IVA esclusa)
     */
    public record Item(
            Type type,
            UUID contractId,
            String contractNumber,
            String counterpartyName,
            String projectName,
            LocalDate dueDate,
            Integer daysLeft,
            String managerName,
            Integer renewalMonths,
            UUID invoiceId,
            String invoiceNumber,
            BigDecimal invoiceAmount) {
    }
}
