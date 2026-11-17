/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.upload;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.donatodev.bcm_backend.exception.UnsafeFileException;
import com.donatodev.bcm_backend.security.SecurityEvents;

/**
 * The one way into the application for an uploaded file (OWASP File Upload
 * Cheat Sheet). Each kind of file the app accepts has its own method, and
 * nothing else is accepted (allowlist); each checks, in order:
 * <ol>
 * <li>not empty, and not over that kind's size limit;</li>
 * <li>the extension of the name it was sent with;</li>
 * <li>the first bytes: what the file really is, whatever it is called or
 *     whatever type the browser declared (never trusted);</li>
 * <li>the antivirus ({@link MalwareScanner}), before anything parses it;</li>
 * <li>for a PDF, its structure: PDFBox must open it, it must have a page and
 *     no active content ({@link PdfActiveContent}).</li>
 * </ol>
 * The FatturaPA XML is then validated against the official schema by its
 * parser, and the spreadsheet opened as .xlsx only, by the import. A file
 * refused as dangerous is recorded as a security event.
 */
@Component
public class UploadChecks {

    /** Contract documents. */
    public static final long MAX_PDF_BYTES = 10L * 1024 * 1024;
    /** FatturaPA invoices: a few KB in practice, 5 MB is the SdI's own limit. */
    public static final long MAX_XML_BYTES = 5L * 1024 * 1024;
    /** Contract import spreadsheets: 5000 rows are well under 1 MB. */
    public static final long MAX_XLSX_BYTES = 5L * 1024 * 1024;

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    /** A ZIP archive's local file header: an .xlsx is one. */
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    private static final int XML_SNIFF = 1024;

    private final MalwareScanner scanner;
    private final SecurityEvents securityEvents;

    public UploadChecks(MalwareScanner scanner, SecurityEvents securityEvents) {
        this.scanner = scanner;
        this.securityEvents = securityEvents;
    }

    /** A contract document: a real, readable PDF with no active content. Returns its bytes. */
    public byte[] pdf(MultipartFile file) throws IOException {
        byte[] bytes = common(file, MAX_PDF_BYTES, "10 MB", ".pdf", "Sono supportati solo file PDF");
        if (!startsWith(bytes, PDF_MAGIC)) {
            throw new IllegalArgumentException("Sono supportati solo file PDF");
        }
        scan(bytes, "pdf");
        Optional<PdfProblem> problem = pdfStructure(bytes);
        if (problem.isPresent()) {
            if (problem.get().unsafe()) {
                securityEvents.record(SecurityEvents.Type.UNSAFE_UPLOAD, "pdf " + problem.get().reason());
                throw new UnsafeFileException(problem.get().message());
            }
            throw new IllegalArgumentException(problem.get().message());
        }
        return bytes;
    }

    /**
     * Why a PDF already stored would be refused if it were uploaded today
     * ("active content: JS", "malware: Eicar-Test-Signature", ...), or empty
     * when it passes: the same checks as {@link #pdf}, on the content alone.
     * Records nothing: the caller decides what a finding means.
     *
     * @throws com.donatodev.bcm_backend.exception.MalwareScanUnavailableException when ClamAV gives no clear answer
     */
    public Optional<String> recheckPdf(byte[] bytes) {
        if (bytes.length == 0 || !startsWith(bytes, PDF_MAGIC)) {
            return Optional.of("not a pdf");
        }
        Optional<String> signature = scanner.scan(bytes);
        if (signature.isPresent()) {
            return Optional.of("malware: " + signature.get());
        }
        return pdfStructure(bytes).map(PdfProblem::reason);
    }

    /**
     * As {@link #recheckPdf}, for a FatturaPA invoice already stored.
     *
     * @throws com.donatodev.bcm_backend.exception.MalwareScanUnavailableException when ClamAV gives no clear answer
     */
    public Optional<String> recheckFatturaPa(byte[] bytes) {
        if (!looksLikeXml(bytes)) {
            return Optional.of("not xml");
        }
        return scanner.scan(bytes).map(signature -> "malware: " + signature);
    }

    /** Whether the antivirus really runs: a recheck with it off would call everything clean. */
    public boolean scanning() {
        return scanner.enabled();
    }

    /** What is wrong with a PDF's structure: a reason for the record, a message for the user. */
    private record PdfProblem(String reason, String message, boolean unsafe) {
    }

    private static Optional<PdfProblem> pdfStructure(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() < 1) {
                return Optional.of(new PdfProblem("no pages", "Il PDF non contiene pagine", false));
            }
            return PdfActiveContent.find(document).map(active -> new PdfProblem("active content: " + active,
                    "Il PDF contiene contenuti attivi (JavaScript, azioni automatiche "
                    + "o file incorporati) e non può essere caricato", true));
        } catch (InvalidPasswordException e) {
            return Optional.of(new PdfProblem("password protected",
                    "I PDF protetti da password non sono supportati", false));
        } catch (IOException e) {
            return Optional.of(new PdfProblem("unreadable", "Il file non è un PDF valido", false));
        }
    }

    /** A FatturaPA invoice: an XML file (the schema is checked by its parser). Returns its bytes. */
    public byte[] fatturaPa(MultipartFile file) throws IOException {
        byte[] bytes = common(file, MAX_XML_BYTES, "5 MB", ".xml", "Sono supportati solo file XML");
        if (!looksLikeXml(bytes)) {
            throw new IllegalArgumentException("Sono supportati solo file XML");
        }
        scan(bytes, "xml");
        return bytes;
    }

    /** A contract import spreadsheet: an .xlsx archive (opened as .xlsx only by the import). Returns its bytes. */
    public byte[] xlsx(MultipartFile file) throws IOException {
        byte[] bytes = common(file, MAX_XLSX_BYTES, "5 MB", ".xlsx", "Sono supportati solo file .xlsx");
        if (!startsWith(bytes, ZIP_MAGIC)) {
            throw new IllegalArgumentException("Sono supportati solo file .xlsx");
        }
        scan(bytes, "xlsx");
        return bytes;
    }

    private static byte[] common(MultipartFile file, long maxBytes, String limit, String extension,
                                 String wrongType) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Il file è vuoto");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Il file supera il limite di " + limit);
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(extension)) {
            throw new IllegalArgumentException(wrongType);
        }
        byte[] bytes = file.getBytes();
        // The declared size is the client's word: the bytes are what counts.
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Il file è vuoto");
        }
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("Il file supera il limite di " + limit);
        }
        return bytes;
    }

    private void scan(byte[] bytes, String kind) {
        Optional<String> signature = scanner.scan(bytes);
        if (signature.isPresent()) {
            securityEvents.record(SecurityEvents.Type.UNSAFE_UPLOAD, kind + " malware: " + signature.get());
            throw new UnsafeFileException("Il file è stato rifiutato dall'antivirus");
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** The first non-blank character, after an optional UTF-8 byte order mark, is "<". */
    private static boolean looksLikeXml(byte[] bytes) {
        int i = startsWith(bytes, new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}) ? 3 : 0;
        int end = Math.min(bytes.length, XML_SNIFF);
        while (i < end && (bytes[i] == ' ' || bytes[i] == '\t' || bytes[i] == '\r' || bytes[i] == '\n')) {
            i++;
        }
        return i < end && bytes[i] == '<';
    }
}
