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
