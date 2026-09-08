/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

/**
 * Lifecycle of an {@link ElectronicInvoice}'s link to a {@link FinancialValues}
 * row, computed and applied by {@code InvoiceMatchingService}.
 * <p>
 * {@code UNMATCHED}: no candidate row scored high enough to suggest.
 * {@code SUGGESTED}: a candidate was found automatically, awaiting human
 * confirmation — not yet authoritative. {@code CONFIRMED}: a human confirmed
 * the match (or a manual override of it); the invoice is considered proof of
 * that financial value. {@code REJECTED}: a human explicitly dismissed a
 * suggestion — final, never reconsidered by a later recompute.
 * {@code COUNTERPARTY_MISMATCH}: the invoice's supplier doesn't correspond to
 * the contract's counterparty at all, so no candidate was even scored — a
 * likely sign the invoice was uploaded to the wrong contract.
 */
public enum InvoiceMatchStatus {
    UNMATCHED,
    SUGGESTED,
    CONFIRMED,
    REJECTED,
    COUNTERPARTY_MISMATCH
}
