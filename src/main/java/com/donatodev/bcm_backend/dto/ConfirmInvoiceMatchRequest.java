/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

/**
 * Body of the invoice-match confirmation endpoint. {@code financialValueId}
 * is optional: omit it (or send {@code null}/no body) to confirm whatever
 * {@code InvoiceMatchingService} already suggested, or provide it to
 * manually override the suggestion with a different financial-value row.
 */
public record ConfirmInvoiceMatchRequest(Long financialValueId) {}
