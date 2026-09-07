package com.donatodev.bcm_backend.entity;

/**
 * How often a {@link Contracts} with financial terms invoices its
 * {@link Contracts#getAnnualValue()} — used to derive both the per-period
 * amount ({@code annualValue / periodsPerYear}) and the step size (in
 * months) between generated {@link FinancialValues} rows.
 */
public enum BillingFrequency {
    MONTHLY(12),
    QUARTERLY(4),
    SEMIANNUAL(2),
    ANNUAL(1);

    private final int periodsPerYear;

    BillingFrequency(int periodsPerYear) {
        this.periodsPerYear = periodsPerYear;
    }

    public int getPeriodsPerYear() {
        return periodsPerYear;
    }
}
