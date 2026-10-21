/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.exception.OrganizationNotFoundException;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

/**
 * Stripe subscription billing: FREE → PRO via Checkout, back to FREE when the
 * subscription ends. Every tier change goes through
 * {@link OrganizationService#changeTier}, the same audited path as the manual
 * internal endpoint.
 *
 * <p>Webhook payloads are read as raw JSON for the handful of fields used
 * here, not through stripe-java's typed deserializer: that one silently
 * returns empty when the account's API version is newer than the library's
 * pinned one — a new account always runs the latest version — and the
 * upgrade would then never happen, with no error anywhere. The signature is
 * still verified by the library ({@link Webhook#constructEvent}) before any
 * of the payload is trusted.
 */
@Service
public class BillingService {

    static final String ORG_ID_METADATA_KEY = "orgId";

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StripeCheckoutGateway checkoutGateway;
    private final OrganizationService organizationService;
    private final OrganizationRepository organizationRepository;
    private final String secretKey;
    private final String proPriceId;
    private final String webhookSecret;
    private final String frontendBaseUrl;

    public BillingService(
            StripeCheckoutGateway checkoutGateway,
            OrganizationService organizationService,
            OrganizationRepository organizationRepository,
            @Value("${stripe.secret-key:}") String secretKey,
            @Value("${stripe.pro-price-id:}") String proPriceId,
            @Value("${stripe.webhook-secret:}") String webhookSecret,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.checkoutGateway = checkoutGateway;
        this.organizationService = organizationService;
        this.organizationRepository = organizationRepository;
        this.secretKey = secretKey;
        this.proPriceId = proPriceId;
        this.webhookSecret = webhookSecret;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Starts a PRO subscription checkout for the caller's organization.
     *
     * @return the Stripe-hosted checkout URL the frontend redirects to
     */
    public String createProCheckoutUrl() {
        if (proPriceId.isBlank()) {
            throw notConfigured();
        }
        Organization org = currentOrganization();
        if (org.getSubscriptionTier() != SubscriptionTier.FREE) {
            throw new IllegalArgumentException("L'organizzazione ha già un piano a pagamento");
        }
        try {
            return checkoutGateway.createSubscriptionCheckout(org.getId(), proPriceId, org.getStripeCustomerId(),
                    frontendBaseUrl + "/organization?checkout=success",
                    frontendBaseUrl + "/organization?checkout=cancel");
        } catch (StripeException ex) {
            throw stripeUnavailable("checkout", org.getId(), ex);
        }
    }

    /**
     * Opens the Stripe Customer Portal for the caller's organization, where
     * its admin cancels the subscription, changes card or downloads invoices.
     *
     * @return the Stripe-hosted portal URL the frontend redirects to
     */
    public String createPortalUrl() {
        Organization org = currentOrganization();
        if (org.getStripeCustomerId() == null) {
            throw new IllegalArgumentException("Nessun abbonamento Stripe da gestire per questa organizzazione");
        }
        try {
            return checkoutGateway.createPortalSession(org.getStripeCustomerId(), frontendBaseUrl + "/organization");
        } catch (StripeException ex) {
            throw stripeUnavailable("portal", org.getId(), ex);
        }
    }

    private Organization currentOrganization() {
        if (secretKey.isBlank()) {
            throw notConfigured();
        }
        Long orgId = TenantContext.get();
        Organization org = orgId == null ? null : organizationRepository.findById(orgId).orElse(null);
        if (org == null) {
            throw new OrganizationNotFoundException("Organizzazione non trovata");
        }
        return org;
    }

    private static ResponseStatusException notConfigured() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Pagamenti non configurati");
    }

    private static ResponseStatusException stripeUnavailable(String operation, Long orgId, StripeException ex) {
        log.warn("Stripe {} session creation failed for org {}: {}", operation, orgId, ex.getClass().getSimpleName());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Servizio di pagamento non disponibile");
    }

    /**
     * Verifies and applies one Stripe webhook event. Returning normally means
     * "acknowledged" (HTTP 200): events this service can never act on — an
     * unknown org, a missing org id — are logged and acknowledged too, since
     * Stripe would otherwise retry them for days to no effect. Redelivery of
     * an event already applied is harmless: {@code changeTier} is a no-op when
     * the tier is unchanged.
     *
     * @throws ResponseStatusException 404 when no webhook secret is configured,
     *         400 on a missing or invalid signature
     */
    public void handleWebhook(String payload, String signatureHeader) {
        if (webhookSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Firma webhook mancante");
        }
        // Parsed once, up front: nothing is acted on until the signature
        // check below passes, so reading untrusted JSON here is harmless,
        // and a malformed body is rejected the same way as a bad signature.
        JsonNode object;
        try {
            object = OBJECT_MAPPER.readTree(payload).path("data").path("object");
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload webhook non valido");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Firma webhook non valida");
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> onCheckoutCompleted(object);
            // The stored customer id is kept on purpose: the portal still
            // shows past invoices, and a later checkout reuses the customer.
            case "customer.subscription.deleted" -> applyTier(
                    readOrgId(object.path("metadata"), ORG_ID_METADATA_KEY), SubscriptionTier.FREE, null);
            default -> {
                // Not subscribed to anything else; acknowledged and ignored.
            }
        }
    }

    private void onCheckoutCompleted(JsonNode session) {
        String mode = stringOrNull(session, "mode");
        String paymentStatus = stringOrNull(session, "payment_status");
        boolean paid = "paid".equals(paymentStatus) || "no_payment_required".equals(paymentStatus);
        if (!"subscription".equals(mode) || !paid) {
            return;
        }
        applyTier(readOrgId(session, "client_reference_id"), SubscriptionTier.PRO, stringOrNull(session, "customer"));
    }

    /** @param stripeCustomerId linked to the org first when present (completed checkouts only) */
    private void applyTier(Long orgId, SubscriptionTier tier, String stripeCustomerId) {
        if (orgId == null) {
            log.warn("Stripe webhook without a usable org id, ignored (target tier {})", tier);
            return;
        }
        try {
            if (stripeCustomerId != null) {
                organizationService.linkStripeCustomer(orgId, stripeCustomerId);
            }
            organizationService.changeTier(orgId, tier, "stripe");
        } catch (OrganizationNotFoundException ex) {
            log.warn("Stripe webhook for unknown org {}, ignored (target tier {})", orgId, tier);
        }
    }

    private static Long readOrgId(JsonNode source, String key) {
        String value = stringOrNull(source, key);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stringOrNull(JsonNode source, String key) {
        JsonNode node = source.path(key);
        return node.isTextual() ? node.asText() : null;
    }
}
