/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.io.UnsupportedEncodingException;
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

    private static final Set<String> RESERVED_DOMAINS = Set.of("example.com", "example.net", "example.org");
    private static final Set<String> RESERVED_TLDS = Set.of("test", "example", "invalid", "localhost");

    private static final Pattern HEAD = Pattern.compile("(?is)<head.*?</head>");
    private static final Pattern BLOCK_END = Pattern.compile("(?i)</(p|div|h[1-6]|tr|li)>|<br\\s*/?>");
    private static final Pattern CELL_END = Pattern.compile("(?i)</t[dh]>");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\s*\\n\\s*(\\n\\s*)+");

    protected final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender, @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
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

    /**
     * Sends a generic email with custom subject and HTML body.
     *
     * @throws EmailSendingException if the mail server rejects the message
     */
    @Override
    public void sendEmail(String to, String subject, String body) {
        send(to, subject, body, "Error sending email");
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
        String text = HEAD.matcher(html).replaceAll("");
        text = BLOCK_END.matcher(text).replaceAll("\n");
        text = CELL_END.matcher(text).replaceAll("  ");
        text = TAG.matcher(text).replaceAll("");
        text = HtmlUtils.htmlUnescape(text);
        text = SPACES.matcher(text).replaceAll(" ");
        return BLANK_LINES.matcher(text).replaceAll("\n\n").trim();
    }
}
