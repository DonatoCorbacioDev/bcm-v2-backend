/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The name an uploaded file is shown and downloaded under, made from the one
 * the browser sent (OWASP File Upload Cheat Sheet: never trust the original
 * filename). The file itself is always stored under a random name; this only
 * cleans the label kept next to it.
 * <p>
 * Keeps the last segment of a path ("C:\fakepath\a.pdf" becomes "a.pdf"),
 * drops control and invisible formatting characters (a right-to-left override
 * can make "fdp.exe" read as "exe.pdf") and the characters Windows refuses in
 * a name, strips leading and trailing dots and spaces, and shortens the result
 * to {@link #MAX_LENGTH} keeping the extension: the column holds 255.
 */
public final class SafeFileName {

    /** Longest name kept, extension included. */
    public static final int MAX_LENGTH = 200;

    private static final String FORBIDDEN = "<>:\"/\\|?*";

    private SafeFileName() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The cleaned name, ending in {@code extension} (".pdf"); {@code fallback}
     * when nothing usable is left.
     */
    public static String of(String original, String extension, String fallback) {
        if (original == null) {
            return fallback;
        }
        String name = Normalizer.normalize(original, Normalizer.Form.NFC);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1);

        StringBuilder kept = new StringBuilder(name.length());
        name.codePoints().filter(SafeFileName::allowed).forEach(kept::appendCodePoint);
        name = kept.toString().strip();

        // The extension as it was written ("CONTRATTO.PDF" stays so), or the expected one added.
        boolean hasExtension = name.toLowerCase(Locale.ROOT).endsWith(extension);
        String suffix = hasExtension ? name.substring(name.length() - extension.length()) : extension;
        String base = trimDotsAndSpaces(hasExtension ? name.substring(0, name.length() - extension.length()) : name);
        if (base.isEmpty()) {
            return fallback;
        }
        int room = MAX_LENGTH - extension.length();
        if (base.codePointCount(0, base.length()) > room) {
            base = trimDotsAndSpaces(base.substring(0, base.offsetByCodePoints(0, room)));
        }
        return base + suffix;
    }

    private static boolean allowed(int codePoint) {
        return !Character.isISOControl(codePoint)
                && Character.getType(codePoint) != Character.FORMAT
                && FORBIDDEN.indexOf(codePoint) < 0;
    }

    private static String trimDotsAndSpaces(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isDotOrSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isDotOrSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isDotOrSpace(char c) {
        return c == '.' || Character.isWhitespace(c) || Character.isSpaceChar(c);
    }
}
