/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.util;

/**
 * Makes what a user typed match only itself inside a {@code LIKE} (OWASP SQL
 * Injection Prevention Cheat Sheet): the term is already a bound parameter,
 * but {@code %} and {@code _} in it would still be wildcards, so "%" would
 * list everything and "_%_%_%" make the search slow.
 * <p>
 * The escape character is {@code !}, written {@code ESCAPE '!'} in the query:
 * a backslash means something different in MySQL's string literals than in
 * H2's, while {@code !} means nothing special in either.
 */
public final class LikePattern {

    /** The character the queries declare with {@code ESCAPE '!'}. */
    public static final char ESCAPE = '!';

    private LikePattern() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** The term with the escape character, {@code %} and {@code _} escaped: each matches only itself. */
    public static String literal(String term) {
        if (term == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(term.length() + 8);
        for (char c : term.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                out.append(ESCAPE);
            }
            out.append(c);
        }
        return out.toString();
    }
}
