/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

/**
 * Response of {@code POST /billing/checkout-session}.
 *
 * @param url the Stripe-hosted checkout page to redirect the browser to
 */
public record CheckoutSessionDTO(String url) {
}
