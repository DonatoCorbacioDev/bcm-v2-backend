/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.upload;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Finds what in a PDF could run or open something when a reader views it
 * (OWASP File Upload Cheat Sheet, malware scanning): a contract needs text and
 * images, never code. Looks at every object reachable from the document's
 * trailer, as PDFBox parsed it, so names written in hex escapes ({@code /J#53})
 * or hidden in compressed object streams are seen like any other.
 * <p>
 * Refused: JavaScript anywhere ({@code /JS}, {@code /JavaScript}), actions that
 * launch a program, open another file, send or import form data, embedded
 * files, XFA forms and rich media. Links to a web page ({@code /URI}) and jumps
 * inside the document are what ordinary contracts contain, and pass.
 */
public final class PdfActiveContent {

    /** A PDF with more objects than this is refused as too complex to check. */
    static final int MAX_OBJECTS = 1_000_000;

    private static final Set<COSName> FORBIDDEN_KEYS = Set.of(
            COSName.getPDFName("JS"),
            COSName.getPDFName("JavaScript"),
            COSName.getPDFName("EmbeddedFiles"),
            COSName.getPDFName("EF"),
            COSName.getPDFName("XFA"),
            COSName.getPDFName("RichMediaContent"));

    private static final Set<COSName> FORBIDDEN_ACTIONS = Set.of(
            COSName.getPDFName("JavaScript"),
            COSName.getPDFName("Launch"),
            COSName.getPDFName("GoToR"),
            COSName.getPDFName("GoToE"),
            COSName.getPDFName("ImportData"),
            COSName.getPDFName("SubmitForm"),
            COSName.getPDFName("RichMediaExecute"));

    private static final Set<COSName> FORBIDDEN_TYPES = Set.of(
            COSName.getPDFName("EmbeddedFile"),
            COSName.getPDFName("RichMedia"));

    private static final COSName ACTION = COSName.getPDFName("S");
    private static final COSName TYPE = COSName.getPDFName("Type");
    private static final COSName SUBTYPE = COSName.getPDFName("Subtype");

    private PdfActiveContent() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** What active content the document carries ("JavaScript", "Launch"...), or empty if none. */
    public static Optional<String> find(PDDocument document) {
        Set<COSBase> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<COSBase> pending = new ArrayDeque<>();
        pending.push(document.getDocument().getTrailer());
        while (!pending.isEmpty()) {
            COSBase node = pending.pop();
            if (node instanceof COSObject reference) {
                node = reference.getObject();
            }
            if (node == null || !seen.add(node)) {
                continue;
            }
            if (seen.size() > MAX_OBJECTS) {
                return Optional.of("too many objects");
            }
            if (node instanceof COSDictionary dictionary) {
                Optional<String> found = forbidden(dictionary);
                if (found.isPresent()) {
                    return found;
                }
                dictionary.getValues().forEach(pending::push);
            } else if (node instanceof COSArray array) {
                array.forEach(pending::push);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> forbidden(COSDictionary dictionary) {
        for (COSName key : dictionary.keySet()) {
            if (FORBIDDEN_KEYS.contains(key)) {
                return Optional.of(key.getName());
            }
        }
        for (COSName key : new COSName[] {ACTION, TYPE, SUBTYPE}) {
            COSBase value = dictionary.getDictionaryObject(key);
            if (value instanceof COSName name
                    && (key == ACTION ? FORBIDDEN_ACTIONS : FORBIDDEN_TYPES).contains(name)) {
                return Optional.of(name.getName());
            }
        }
        return Optional.empty();
    }
}
