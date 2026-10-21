/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.donatodev.bcm_backend.dto.BillingRedirectDTO;
import com.donatodev.bcm_backend.service.BillingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/billing")
@Tag(name = "Billing", description = "Stripe subscription checkout and webhook")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/checkout-session")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Start a PRO subscription checkout for the caller's organization")
    public ResponseEntity<BillingRedirectDTO> createCheckoutSession() {
        return ResponseEntity.ok(new BillingRedirectDTO(billingService.createProCheckoutUrl()));
    }

    @PostMapping("/portal-session")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Open the Stripe Customer Portal (cancel, change card, invoices) for the caller's organization")
    public ResponseEntity<BillingRedirectDTO> createPortalSession() {
        return ResponseEntity.ok(new BillingRedirectDTO(billingService.createPortalUrl()));
    }

    /**
     * Called by Stripe, not by a user — no JWT. The body must stay the exact
     * raw string Stripe sent: the signature is computed over those bytes, so
     * it is bound as a String, never deserialized into a DTO first.
     */
    @PostMapping("/webhook")
    @Operation(summary = "Stripe webhook receiver (signature-verified)")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        billingService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
