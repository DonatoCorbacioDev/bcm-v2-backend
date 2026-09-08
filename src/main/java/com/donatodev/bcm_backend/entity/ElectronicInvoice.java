/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "electronic_invoices")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ElectronicInvoice extends StoredFile {

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "supplier_vat_number", length = 30)
    private String supplierVatNumber;

    @Column(name = "document_type", length = 10)
    private String documentType;

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "line_items_json", columnDefinition = "LONGTEXT")
    private String lineItemsJson;

    @Column(name = "supplier_iban", length = 34)
    private String supplierIban;

    @Column(name = "supplier_bic", length = 11)
    private String supplierBic;

    @Column(name = "payment_due_date")
    private LocalDate paymentDueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sepa_batch_id")
    private SepaPaymentBatch sepaBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_financial_value_id")
    private FinancialValues matchedFinancialValue;

    @Column(name = "match_confidence")
    private Double matchConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false)
    @Builder.Default
    private InvoiceMatchStatus matchStatus = InvoiceMatchStatus.UNMATCHED;

    @Column(name = "matched_at")
    private Instant matchedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_by_user_id")
    private Users matchedByUser;
}
