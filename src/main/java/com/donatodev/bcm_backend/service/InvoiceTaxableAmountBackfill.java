/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.dto.FatturaPaInvoiceData;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;

/**
 * Fills in the taxable amount and VAT (V50) of invoices stored before they
 * were read from the FatturaPA, by re-parsing their XML once. Idempotent:
 * only invoices still without a taxable amount are touched, so after the
 * first start it does nothing. An unreadable file is logged and skipped;
 * such an invoice keeps being compared on its total, as before.
 */
@Service
public class InvoiceTaxableAmountBackfill {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceTaxableAmountBackfill.class);

    private final ElectronicInvoiceRepository invoiceRepository;
    private final LocalStorageService localStorageService;
    private final FatturaPaXmlParserService parser;

    public InvoiceTaxableAmountBackfill(ElectronicInvoiceRepository invoiceRepository,
                                        LocalStorageService localStorageService,
                                        FatturaPaXmlParserService parser) {
        this.invoiceRepository = invoiceRepository;
        this.localStorageService = localStorageService;
        this.parser = parser;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        int filled = 0;
        int skipped = 0;
        for (ElectronicInvoice invoice : invoiceRepository.findByTaxableAmountIsNull()) {
            try {
                FatturaPaInvoiceData parsed = parser.parse(localStorageService.readDocument(invoice.getStoragePath()));
                if (parsed.taxableAmount() == null) {
                    skipped++;
                    continue;
                }
                invoice.setTaxableAmount(parsed.taxableAmount());
                invoice.setVatAmount(parsed.vatAmount());
                invoiceRepository.save(invoice);
                filled++;
            } catch (RuntimeException e) {
                skipped++;
                logger.warn("Could not read the taxable amount of invoice {}: {}", invoice.getId(), e.getClass().getSimpleName());
            }
        }
        if (filled > 0 || skipped > 0) {
            logger.info("Invoice taxable amount backfill: {} filled, {} skipped", filled, skipped);
        }
    }
}
