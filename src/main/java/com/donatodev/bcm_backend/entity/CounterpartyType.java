/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

/**
 * The commercial relationship a {@link Counterparty} has with the organization.
 * {@code BOTH} covers a company that is simultaneously a customer on some
 * contracts and a supplier on others.
 */
public enum CounterpartyType {
    CUSTOMER,
    SUPPLIER,
    BOTH
}
