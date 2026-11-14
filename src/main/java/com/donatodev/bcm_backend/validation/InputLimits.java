package com.donatodev.bcm_backend.validation;

/**
 * Bounds shared by the request DTOs (OWASP Input Validation Cheat Sheet):
 * a value outside them is refused with 400 before it reaches a service or
 * the database. Text limits follow the column each field is stored in.
 */
public final class InputLimits {

    /** A TEXT column holds 65,535 bytes: 10,000 characters fit even at 4 bytes each. */
    public static final int LONG_TEXT = 10_000;
    /** A comment on a workflow step or a rejection. */
    public static final int COMMENT = 2_000;
    /** A question to the assistant or a semantic search: what a person types, not a document. */
    public static final int QUESTION = 2_000;
    /** A token from a link or a previous step (JWT, reset, invite): longer is not one of ours. */
    public static final int TOKEN = 4_096;
    /** A password being checked, not set: BCrypt reads 72 bytes, a few hundred characters is plenty. */
    public static final int PASSWORD_CHECKED = 256;

    /** One thousand billion euro: above any contract, budget or value a company records. */
    public static final String MAX_AMOUNT = "1000000000000";
    public static final String MIN_AMOUNT = "-1000000000000";
    public static final int MIN_YEAR = 2000;
    public static final int MAX_YEAR = 2100;
    /** About a hundred years, the longest duration or notice that still makes sense. */
    public static final int MAX_DAYS = 36_500;

    private InputLimits() {
    }
}
