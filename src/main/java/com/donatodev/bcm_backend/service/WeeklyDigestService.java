package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@Service
public class WeeklyDigestService {

    private static final Logger logger = LoggerFactory.getLogger(WeeklyDigestService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int EXPIRY_WINDOW_DAYS = 30;
    private static final int MAX_ROWS = 5;
    private static final String CLOSE_DIV = "</div>";
    private static final String CLOSE_TD = "</td>";

    private final OrganizationRepository organizationRepository;
    private final ContractsRepository contractsRepository;
    private final UsersRepository usersRepository;
    private final IEmailService emailService;

    public WeeklyDigestService(
            OrganizationRepository organizationRepository,
            ContractsRepository contractsRepository,
            UsersRepository usersRepository,
            IEmailService emailService) {
        this.organizationRepository = organizationRepository;
        this.contractsRepository = contractsRepository;
        this.usersRepository = usersRepository;
        this.emailService = emailService;
    }

    @Scheduled(cron = "0 0 8 * * MON")
    public void sendWeeklyDigests() {
        List<Organization> orgs = organizationRepository.findAll();
        logger.info("Weekly digest: processing {} organization(s)", orgs.size());
        int sent = 0;
        for (Organization org : orgs) {
            try {
                sent += sendDigestForOrg(org);
            } catch (Exception e) {
                logger.warn("Weekly digest failed for org {} (id={}): {}", org.getName(), org.getId(), e.getMessage());
            }
        }
        logger.info("Weekly digest complete: {} email(s) sent", sent);
    }

    int sendDigestForOrg(Organization org) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate window = today.plusDays(EXPIRY_WINDOW_DAYS);

        List<Contracts> expiring = contractsRepository
                .findExpiringContractsByOrg(today, window, org.getId());

        List<Users> admins = usersRepository.findByOrganizationIdAndRoleRole(org.getId(), "ADMIN");
        if (admins.isEmpty()) {
            return 0;
        }

        String subject = buildSubject(expiring.size());
        String body = buildHtmlBody(org.getName(), today, expiring);

        int count = 0;
        for (Users admin : admins) {
            if (admin.getManager() != null && admin.getManager().getEmail() != null
                    && !admin.getManager().getEmail().isBlank()) {
                emailService.sendEmail(admin.getManager().getEmail(), subject, body);
                count++;
            }
        }
        return count;
    }

    private String buildSubject(int expiringCount) {
        if (expiringCount == 0) {
            return "BCM Weekly Digest — No expiring contracts this week";
        }
        return String.format("BCM Weekly Digest — %d contract%s expiring soon",
                expiringCount, expiringCount == 1 ? "" : "s");
    }

    String buildHtmlBody(String orgName, LocalDate today, List<Contracts> expiring) {
        String generatedAt = LocalDateTime.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='it'><head>")
          .append("<meta charset='UTF-8'>")
          .append("<style>")
          .append("body{font-family:Arial,sans-serif;color:#333;max-width:680px;margin:0 auto;padding:0}")
          .append(".header{background:#1e3a5f;color:#fff;padding:24px 32px}")
          .append(".header h1{margin:0;font-size:22px}")
          .append(".header p{margin:6px 0 0;font-size:13px;opacity:.8}")
          .append(".content{padding:24px 32px}")
          .append(".section-title{font-size:16px;font-weight:bold;color:#1e3a5f;border-bottom:2px solid #e8edf3;padding-bottom:8px;margin:24px 0 12px}")
          .append("table{width:100%;border-collapse:collapse;font-size:13px}")
          .append("th{background:#f0f4f8;color:#1e3a5f;text-align:left;padding:8px 10px;font-weight:600}")
          .append("td{padding:8px 10px;border-bottom:1px solid #e8edf3}")
          .append("tr:last-child td{border-bottom:none}")
          .append(".badge-urgent{background:#fee2e2;color:#991b1b;padding:2px 8px;border-radius:12px;font-size:11px;font-weight:600}")
          .append(".badge-warning{background:#fef3c7;color:#92400e;padding:2px 8px;border-radius:12px;font-size:11px;font-weight:600}")
          .append(".badge-normal{background:#dbeafe;color:#1e40af;padding:2px 8px;border-radius:12px;font-size:11px;font-weight:600}")
          .append(".empty{color:#6b7280;font-style:italic;font-size:13px;padding:12px 0}")
          .append(".footer{background:#f8fafc;padding:16px 32px;font-size:11px;color:#9ca3af;border-top:1px solid #e8edf3}")
          .append("</style></head><body>")
          .append("<div class='header'>")
          .append("<h1>BCM Weekly Digest</h1>")
          .append("<p>").append(escapeHtml(orgName)).append(" &bull; ").append(today.format(DATE_FMT)).append("</p>")
          .append(CLOSE_DIV)
          .append("<div class='content'>");

        appendExpiringSection(sb, expiring, today);
        appendCriticalSection(sb, expiring, today);

        sb.append(CLOSE_DIV)
          .append("<div class='footer'>")
          .append("Business Contracts Manager &bull; Generato il ").append(generatedAt)
          .append("<br>Questo messaggio è stato inviato automaticamente. Non rispondere a questa email.")
          .append(CLOSE_DIV)
          .append("</body></html>");

        return sb.toString();
    }

    private void appendExpiringSection(StringBuilder sb, List<Contracts> expiring, LocalDate today) {
        sb.append("<div class='section-title'>Scadenze imminenti (prossimi ").append(EXPIRY_WINDOW_DAYS).append(" giorni)</div>");
        if (expiring.isEmpty()) {
            sb.append("<p class='empty'>Nessun contratto in scadenza nei prossimi ").append(EXPIRY_WINDOW_DAYS).append(" giorni.</p>");
            return;
        }
        sb.append("<table><thead><tr>")
          .append("<th>Contratto</th><th>Cliente</th><th>Scadenza</th><th>Urgenza</th>")
          .append("</tr></thead><tbody>");
        for (Contracts c : expiring.stream().limit(MAX_ROWS).toList()) {
            long daysLeft = today.until(c.getEndDate()).getDays();
            sb.append("<tr>")
              .append("<td>").append(escapeHtml(c.getContractNumber())).append(CLOSE_TD)
              .append("<td>").append(escapeHtml(c.getCustomerName())).append(CLOSE_TD)
              .append("<td>").append(c.getEndDate().format(DATE_FMT)).append(CLOSE_TD)
              .append("<td><span class='").append(urgencyBadge(daysLeft)).append("'>").append(urgencyLabel(daysLeft)).append("</span>").append(CLOSE_TD)
              .append("</tr>");
        }
        sb.append("</tbody></table>");
        if (expiring.size() > MAX_ROWS) {
            sb.append("<p style='font-size:12px;color:#6b7280;margin-top:8px'>")
              .append("+ ").append(expiring.size() - MAX_ROWS)
              .append(" altri contratti in scadenza. Accedi a BCM per la lista completa.</p>");
        }
    }

    private void appendCriticalSection(StringBuilder sb, List<Contracts> expiring, LocalDate today) {
        List<Contracts> critical = expiring.stream()
                .filter(c -> c.getEndDate() != null && today.until(c.getEndDate()).getDays() <= 7)
                .limit(MAX_ROWS)
                .toList();
        if (critical.isEmpty()) {
            return;
        }
        sb.append("<div class='section-title'>Contratti critici (&le;7 giorni alla scadenza)</div>")
          .append("<table><thead><tr>")
          .append("<th>Contratto</th><th>Cliente</th><th>Progetto</th><th>Scadenza</th>")
          .append("</tr></thead><tbody>");
        for (Contracts c : critical) {
            String project = c.getProjectName() != null ? c.getProjectName() : "—";
            sb.append("<tr>")
              .append("<td>").append(escapeHtml(c.getContractNumber())).append(CLOSE_TD)
              .append("<td>").append(escapeHtml(c.getCustomerName())).append(CLOSE_TD)
              .append("<td>").append(escapeHtml(project)).append(CLOSE_TD)
              .append("<td style='color:#991b1b;font-weight:600'>").append(c.getEndDate().format(DATE_FMT)).append(CLOSE_TD)
              .append("</tr>");
        }
        sb.append("</tbody></table>");
    }

    private static String urgencyBadge(long daysLeft) {
        if (daysLeft <= 7) return "badge-urgent";
        if (daysLeft <= 14) return "badge-warning";
        return "badge-normal";
    }

    private static String urgencyLabel(long daysLeft) {
        if (daysLeft <= 7) return "Critico";
        if (daysLeft <= 14) return "Urgente";
        return "In scadenza";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "—";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
