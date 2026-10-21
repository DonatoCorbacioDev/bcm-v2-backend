/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

/**
 * The one outbound Stripe API call, kept apart from {@link BillingService} so
 * tests can mock it — {@code Session.create} is static and talks to the
 * network. Passes the API key per request ({@link RequestOptions}) instead of
 * setting the global {@code Stripe.apiKey}, so no static state is mutated.
 */
@Component
public class StripeCheckoutGateway {

    private final String secretKey;

    public StripeCheckoutGateway(@Value("${stripe.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Creates a subscription Checkout Session and returns the Stripe-hosted
     * URL to redirect the user to. The org id is attached twice on purpose:
     * {@code client_reference_id} reaches {@code checkout.session.completed},
     * the subscription metadata reaches {@code customer.subscription.deleted}
     * later — a different event that never carries the session.
     *
     * @param existingCustomerId the org's Stripe customer from an earlier
     *        subscription, reused so a resubscribing org keeps one customer
     *        (and its invoice history); null to let Checkout create one
     */
    public String createSubscriptionCheckout(Long orgId, String priceId, String existingCustomerId,
            String successUrl, String cancelUrl) throws StripeException {
        String orgIdValue = String.valueOf(orgId);
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
                .setClientReferenceId(orgIdValue)
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .putMetadata(BillingService.ORG_ID_METADATA_KEY, orgIdValue)
                        .build())
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl);
        if (existingCustomerId != null) {
            params.setCustomer(existingCustomerId);
        }
        return Session.create(params.build(), requestOptions()).getUrl();
    }

    /**
     * Opens a Stripe Customer Portal session (cancel, change card, invoices)
     * for an existing customer and returns its URL. Cancelling there fires
     * customer.subscription.deleted, which BillingService already handles.
     */
    public String createPortalSession(String customerId, String returnUrl) throws StripeException {
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(returnUrl)
                        .build();
        return com.stripe.model.billingportal.Session.create(params, requestOptions()).getUrl();
    }

    private RequestOptions requestOptions() {
        return RequestOptions.builder().setApiKey(secretKey).build();
    }
}
