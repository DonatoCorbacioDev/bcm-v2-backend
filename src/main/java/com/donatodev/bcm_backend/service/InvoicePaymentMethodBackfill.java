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
 * Reads the payment method of invoices uploaded before V53 from their stored
 * XML, so an old RiBa or direct-debit invoice isn't offered for a SEPA
 * transfer. An invoice that states no method gets "" (not null): it has been
 * looked at, and isn't parsed again on every start. Same approach as
 * {@link InvoiceTaxableAmountBackfill}.
 */
@Service
public class InvoicePaymentMethodBackfill {

    private static final Logger logger = LoggerFactory.getLogger(InvoicePaymentMethodBackfill.class);

    private final ElectronicInvoiceRepository invoiceRepository;
    private final LocalStorageService localStorageService;
    private final FatturaPaXmlParserService parser;

    public InvoicePaymentMethodBackfill(ElectronicInvoiceRepository invoiceRepository,
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
        for (ElectronicInvoice invoice : invoiceRepository.findByPaymentMethodIsNull()) {
            try {
                FatturaPaInvoiceData parsed = parser.parse(localStorageService.readDocument(invoice.getStoragePath()));
                invoice.setPaymentMethod(ElectronicInvoiceService.storedPaymentMethod(parsed.paymentMethod()));
                invoiceRepository.save(invoice);
                filled++;
            } catch (RuntimeException e) {
                // File gone or unreadable: left null, tried again next start.
                skipped++;
                logger.warn("Could not read the payment method of invoice {}: {}", invoice.getId(), e.getClass().getSimpleName());
            }
        }
        if (filled > 0 || skipped > 0) {
            logger.info("Invoice payment method backfill: {} filled, {} skipped", filled, skipped);
        }
    }
}
