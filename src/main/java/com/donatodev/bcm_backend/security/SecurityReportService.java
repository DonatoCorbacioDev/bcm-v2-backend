/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import com.donatodev.bcm_backend.entity.AuditLog;
import com.donatodev.bcm_backend.repository.AuditLogRepository;
import com.donatodev.bcm_backend.service.IEmailService;

/**
 * Weekly email to whoever runs BCM with the abuse refused in the last seven
 * days (OWASP API6), from the {@link SecurityEvents} kept in the audit log.
 * It covers every organization, so it goes to the operator's address
 * ({@code bcm.ops.report-email}), never to a customer; empty sends nothing.
 */
@Service
public class SecurityReportService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityReportService.class);

    static final Duration PERIOD = Duration.ofDays(7);
    static final int TOP = 5;
    static final String SUBJECT = "BCM: segnali di abuso della settimana";

    private static final Map<SecurityEvents.Type, String> LABELS = Map.of(
            SecurityEvents.Type.RATE_LIMITED, "Troppi tentativi di accesso, reset o registrazione da un indirizzo",
            SecurityEvents.Type.COST_LIMITED, "Budget delle operazioni costose superato",
            SecurityEvents.Type.DEMO_BLOCKED, "Azioni disattivate tentate dalla demo pubblica",
            SecurityEvents.Type.REGISTRATION_CODE_REFUSED, "Codici di registrazione rifiutati",
            SecurityEvents.Type.DUPLICATE_PAYMENT_REFUSED, "Pagamenti SEPA duplicati rifiutati",
            SecurityEvents.Type.SEPA_IBAN_MISMATCH_BLOCKED, "Pagamenti SEPA bloccati per IBAN cambiato");

    private final AuditLogRepository auditLogRepository;
    private final IEmailService emailService;
    private final String reportEmail;
    private final Clock clock;

    @Autowired
    public SecurityReportService(AuditLogRepository auditLogRepository, IEmailService emailService,
            @Value("${bcm.ops.report-email:}") String reportEmail) {
        this(auditLogRepository, emailService, reportEmail, Clock.systemUTC());
    }

    SecurityReportService(AuditLogRepository auditLogRepository, IEmailService emailService,
            String reportEmail, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.emailService = emailService;
        this.reportEmail = reportEmail == null ? "" : reportEmail.trim();
        this.clock = clock;
    }

    /** Mondays at 08:05, just after the customers' weekly digest. */
    @Scheduled(cron = "0 5 8 * * MON")
    @Transactional(readOnly = true)
    public void sendWeeklyReport() {
        if (reportEmail.isEmpty()) {
            logger.info("Security report skipped: no bcm.ops.report-email configured");
            return;
        }
        Map<String, SecurityEvents.Type> byAction = Arrays.stream(SecurityEvents.Type.values())
                .collect(Collectors.toMap(SecurityEvents.Type::action, Function.identity()));
        List<AuditLog> events = auditLogRepository.findByActionInAndTimestampAfter(
                byAction.keySet(), clock.instant().minus(PERIOD));
        emailService.sendEmail(reportEmail, SUBJECT, body(events, byAction));
        logger.info("Security report sent: {} event(s) in the last {} days", events.size(), PERIOD.toDays());
    }

    static String body(List<AuditLog> events, Map<String, SecurityEvents.Type> byAction) {
        StringBuilder html = new StringBuilder("<h2>Segnali di abuso degli ultimi 7 giorni</h2>");
        if (events.isEmpty()) {
            return html.append("<p>Nessun segnale: nessuna richiesta è stata rifiutata.</p>").toString();
        }
        html.append("<p>Episodi registrati. Le raffiche contano uno ogni 10 minuti per utente e indirizzo; ")
                .append("il conteggio esatto è nella metrica <code>bcm.security.events</code>.</p><ul>");
        Map<SecurityEvents.Type, Long> perType = events.stream()
                .collect(Collectors.groupingBy(e -> byAction.get(e.getAction()), LinkedHashMap::new,
                        Collectors.counting()));
        for (SecurityEvents.Type type : SecurityEvents.Type.values()) {
            Long count = perType.get(type);
            if (count != null) {
                html.append("<li>").append(escape(LABELS.get(type))).append(": <b>").append(count)
                        .append("</b></li>");
            }
        }
        html.append("</ul>");
        appendTop(html, "Utenti con più episodi", events.stream().map(AuditLog::getUsername));
        appendTop(html, "Indirizzi IP con più episodi", events.stream().map(e -> ipOf(e.getDetails())));
        return html.toString();
    }

    private static void appendTop(StringBuilder html, String title, Stream<String> values) {
        List<Map.Entry<String, Long>> top = values.filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP)
                .toList();
        if (top.isEmpty()) {
            return;
        }
        html.append("<h3>").append(escape(title)).append("</h3><ul>");
        for (Map.Entry<String, Long> entry : top) {
            html.append("<li>").append(escape(entry.getKey())).append(": ").append(entry.getValue()).append("</li>");
        }
        html.append("</ul>");
    }

    /** The address {@link SecurityEvents#details} put first: "ip=203.0.113.7 | ...". */
    static String ipOf(String details) {
        if (details == null || !details.startsWith("ip=")) {
            return null;
        }
        int end = details.indexOf(' ');
        String ip = details.substring(3, end < 0 ? details.length() : end);
        return ip.isEmpty() || "-".equals(ip) ? null : ip;
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
