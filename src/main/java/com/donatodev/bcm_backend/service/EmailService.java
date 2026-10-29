/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.donatodev.bcm_backend.exception.EmailSendingException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Implementation of {@link IEmailService} that sends real emails using Spring's
 * {@link JavaMailSender}.
 * <p>
 * Every message goes out with a named sender ("BCM - Business Contracts
 * Manager &lt;account&gt;") and a plain-text alternative next to the HTML —
 * HTML-only automated mail from a bare address is a common spam signal.
 * Addresses on domains reserved by RFC 2606/6761 (example.com, *.test, ...)
 * are skipped instead of sent: they can never receive mail, and every
 * attempt bounces back to the sending account and erodes its reputation.
 */
@Service
public class EmailService implements IEmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    static final String SENDER_NAME = "BCM - Business Contracts Manager";

    private static final int DELETION_GRACE_DAYS = OrganizationDeletionService.GRACE_DAYS;
    private static final DateTimeFormatter ITALIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern LINE_BREAKS = Pattern.compile("[\r\n]+");

    private static final Set<String> RESERVED_DOMAINS = Set.of("example.com", "example.net", "example.org");
    private static final Set<String> RESERVED_TLDS = Set.of("test", "example", "invalid", "localhost");

    // Single-pass patterns only (no nested or lazy quantifiers spanning the
    // whole input): <head> removal and blank-line collapsing are done with
    // plain string scanning in toPlainText instead, so the cost stays linear.
    private static final Pattern BLOCK_END = Pattern.compile("(?i)</(p|div|h[1-6]|tr|li)>|<br\\s?/?>");
    private static final Pattern CELL_END = Pattern.compile("(?i)</t[dh]>");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f]+");

    protected final JavaMailSender mailSender;
    private final String fromAddress;

    /**
     * @param configuredFrom explicit sender (MAIL_FROM); blank means "not set"
     * @param smtpUsername   fallback sender — on Gmail the SMTP login is the mailbox itself
     */
    public EmailService(JavaMailSender mailSender,
            @Value("${app.mail.from:}") String configuredFrom,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSender = mailSender;
        this.fromAddress = configuredFrom == null || configuredFrom.isBlank() ? smtpUsername : configuredFrom.trim();
    }

    @Override
    public void sendVerificationEmail(String to, String verificationLink) {
        send(to, "Verifica il tuo account BCM",
                "<p>Ciao!</p><p>Clicca sul link qui sotto per verificare il tuo indirizzo email:</p>"
                + "<p><a href=\"" + verificationLink + "\">Verifica email</a></p>",
                "Error sending verification email");
    }

    @Override
    public void sendResetPasswordEmail(String to, String resetLink) {
        send(to, "Reimposta la tua password BCM",
                "<p>Ciao!</p><p>Clicca sul link qui sotto per reimpostare la password:</p>"
                + "<p><a href=\"" + resetLink + "\">Reimposta password</a></p>",
                "Error sending reset password email");
    }

    @Override
    public void sendRegistrationCode(String to, String code, int validityMinutes) {
        send(to, "Il tuo codice di conferma BCM: " + code,
                "<p>Ciao!</p><p>Il tuo codice per completare la registrazione su BCM è:</p>"
                + "<p style=\"font-size:24px;font-weight:bold;letter-spacing:4px\">" + code + "</p>"
                + "<p>Il codice è valido per " + validityMinutes + " minuti. Inserendolo confermi il tuo"
                + " indirizzo email e firmi elettronicamente l'accettazione dei Termini di servizio,"
                + " inclusa l'approvazione specifica delle clausole indicate ai sensi degli artt. 1341"
                + " e 1342 c.c.</p>"
                + "<p>Se non hai richiesto tu la registrazione, ignora questa email: senza il codice"
                + " non viene creato alcun account.</p>",
                "Error sending registration code email");
    }

    @Override
    public void sendOrganizationDeletionCode(String to, String organizationName, String code, int validityMinutes) {
        String org = HtmlUtils.htmlEscape(organizationName);
        send(to, "Codice per cancellare l'organizzazione su BCM: " + code,
                "<p>Ciao!</p><p>Hai chiesto di cancellare l'organizzazione <strong>" + org + "</strong>"
                + " e tutti i suoi dati da BCM. Per confermare inserisci questo codice:</p>"
                + "<p style=\"font-size:24px;font-weight:bold;letter-spacing:4px\">" + code + "</p>"
                + "<p>Il codice è valido per " + validityMinutes + " minuti. Dopo la conferma la"
                + " cancellazione avviene tra " + DELETION_GRACE_DAYS + " giorni: fino ad allora puoi"
                + " annullarla ed esportare i dati.</p>"
                + "<p>Se non sei stato tu, non inserire il codice e cambia subito la password:"
                + " qualcuno conosce la tua.</p>",
                "Error sending organization deletion code email");
    }

    @Override
    public void sendOrganizationDeletionScheduled(String to, String organizationName, String requestedBy,
            LocalDate scheduledFor) {
        String org = HtmlUtils.htmlEscape(organizationName);
        String date = scheduledFor.format(ITALIAN_DATE);
        send(to, "Cancellazione dell'organizzazione " + oneLine(organizationName) + " programmata",
                "<p>Ciao!</p><p>L'amministratore <strong>" + HtmlUtils.htmlEscape(requestedBy) + "</strong>"
                + " ha confermato la cancellazione dell'organizzazione <strong>" + org + "</strong>.</p>"
                + "<p>Il <strong>" + date + "</strong> l'organizzazione e tutti i suoi dati (contratti,"
                + " documenti, fatture, utenti) saranno cancellati definitivamente. Fino ad allora"
                + " BCM funziona normalmente: puoi esportare i dati dalla pagina Organizzazione, e"
                + " qualsiasi amministratore può annullare la cancellazione dalla stessa pagina.</p>"
                + "<p>Le copie di backup vengono eliminate entro 35 giorni dalla loro creazione.</p>",
                "Error sending organization deletion scheduled email");
    }

    @Override
    public void sendOrganizationDeletionCancelled(String to, String organizationName, String cancelledBy) {
        String org = HtmlUtils.htmlEscape(organizationName);
        send(to, "Cancellazione dell'organizzazione " + oneLine(organizationName) + " annullata",
                "<p>Ciao!</p><p>L'amministratore <strong>" + HtmlUtils.htmlEscape(cancelledBy) + "</strong>"
                + " ha annullato la cancellazione dell'organizzazione <strong>" + org + "</strong>."
                + " Nessun dato verrà cancellato.</p>",
                "Error sending organization deletion cancelled email");
    }

    /**
     * Sends a generic email with custom subject and HTML body.
     *
     * @throws EmailSendingException if the mail server rejects the message
     */
    @Override
    public void sendEmail(String to, String subject, String body) {
        send(to, subject, body, "Error sending email");
    }

    /** User-chosen text going into a subject: a line break there would start a new mail header. */
    private static String oneLine(String value) {
        return LINE_BREAKS.matcher(value).replaceAll(" ");
    }

    private void send(String to, String subject, String html, String failureMessage) {
        if (!isDeliverable(to)) {
            logger.info("Skipping email to a reserved, undeliverable domain");
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            if (!fromAddress.isBlank()) {
                helper.setFrom(fromAddress, SENDER_NAME);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(toPlainText(html), html);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new EmailSendingException(failureMessage, e);
        }
    }

    /**
     * False for addresses on domains that, by standard, never accept mail
     * (RFC 2606: example.com/.net/.org; RFC 6761: the .test, .example,
     * .invalid and .localhost TLDs), and for anything without a domain.
     * Says nothing about whether a real-domain mailbox exists.
     */
    static boolean isDeliverable(String address) {
        if (address == null) {
            return false;
        }
        int at = address.lastIndexOf('@');
        if (at < 0 || at == address.length() - 1) {
            return false;
        }
        String domain = address.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        String tld = domain.substring(domain.lastIndexOf('.') + 1);
        boolean reservedDomain = RESERVED_DOMAINS.stream()
                .anyMatch(reserved -> domain.equals(reserved) || domain.endsWith("." + reserved));
        return !reservedDomain && !RESERVED_TLDS.contains(tld);
    }

    /** Readable plain-text rendering of our own generated HTML, for the text/plain part. */
    static String toPlainText(String html) {
        String text = stripHead(html);
        text = BLOCK_END.matcher(text).replaceAll("\n");
        text = CELL_END.matcher(text).replaceAll("  ");
        text = TAG.matcher(text).replaceAll("");
        text = HtmlUtils.htmlUnescape(text);

        // Trim each line and keep at most one blank line between paragraphs.
        StringBuilder out = new StringBuilder();
        boolean previousBlank = true;
        for (String line : text.split("\n", -1)) {
            String clean = SPACES.matcher(line).replaceAll(" ").trim();
            if (clean.isEmpty()) {
                if (!previousBlank) {
                    out.append('\n');
                }
                previousBlank = true;
            } else {
                out.append(clean).append('\n');
                previousBlank = false;
            }
        }
        return out.toString().trim();
    }

    private static String stripHead(String html) {
        int start = indexOfIgnoreCase(html, "<head", 0);
        int end = start < 0 ? -1 : indexOfIgnoreCase(html, "</head>", start);
        return end < 0 ? html : html.substring(0, start) + html.substring(end + "</head>".length());
    }

    /** Case-insensitive indexOf on the original string (lower-casing it first can shift indices). */
    private static int indexOfIgnoreCase(String text, String needle, int from) {
        for (int i = from; i <= text.length() - needle.length(); i++) {
            if (text.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }
}
