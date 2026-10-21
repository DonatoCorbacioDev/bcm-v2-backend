/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

/**
 * Response of {@code POST /billing/checkout-session} and {@code POST /billing/portal-session}.
 *
 * @param url the Stripe-hosted page (checkout or customer portal) to redirect the browser to
 */
public record BillingRedirectDTO(String url) {
}
