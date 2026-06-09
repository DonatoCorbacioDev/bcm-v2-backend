package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@Service
public class MonthlyReporter {

    private static final Logger logger = LoggerFactory.getLogger(MonthlyReporter.class);
    private static final String CRLF_REGEX = "[\r\n]";

    private final OrganizationRepository organizationRepository;
    private final ContractsRepository contractsRepository;
    private final FinancialValuesRepository financialValuesRepository;
    private final UsersRepository usersRepository;
    private final IEmailService emailService;

    public MonthlyReporter(
            OrganizationRepository organizationRepository,
            ContractsRepository contractsRepository,
            FinancialValuesRepository financialValuesRepository,
            UsersRepository usersRepository,
            IEmailService emailService) {
        this.organizationRepository = organizationRepository;
        this.contractsRepository = contractsRepository;
        this.financialValuesRepository = financialValuesRepository;
        this.usersRepository = usersRepository;
        this.emailService = emailService;
    }

    @Scheduled(cron = "0 0 8 1 * *")
    @Transactional(readOnly = true)
    public void sendMonthlyReports() {
        LocalDate lastMonth = LocalDate.now(ZoneId.systemDefault()).minusMonths(1);
        int year = lastMonth.getYear();
        int month = lastMonth.getMonthValue();

        logger.info("Starting monthly report generation for {}/{}", month, year);

        List<Organization> organizations = organizationRepository.findAll();
        int sent = 0;

        for (Organization org : organizations) {
            try {
                sendReportForOrg(org, year, month);
                sent++;
            } catch (Exception e) {
                String safeName = org.getName().replaceAll(CRLF_REGEX, "_");
                logger.error("Failed to send monthly report for org {}: {}", safeName, e.getMessage());
            }
        }

        logger.info("Monthly report completed. Reports sent for {} organizations.", sent);
    }

    private void sendReportForOrg(Organization org, int year, int month) {
        int total = contractsRepository.countAllContractsByOrg(org.getId());
        int active = contractsRepository.countActiveContractsByOrg(org.getId());
        int expired = contractsRepository.countExpiredContractsByOrg(org.getId());
        long newContracts = contractsRepository.countNewContractsByOrgAndYearMonth(org.getId(), year, month);
        double totalValue = financialValuesRepository.sumFinancialAmountByOrgAndYearMonth(org.getId(), year, month);

        List<Users> admins = usersRepository.findByOrganizationIdAndRoleRole(org.getId(), "ADMIN");
        if (admins.isEmpty()) {
            logger.warn("No admin found for org {} — skipping monthly report", org.getId());
            return;
        }

        String monthLabel = LocalDate.of(year, month, 1).getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        String subject = String.format("[BCM] Monthly Report — %s %d", monthLabel, year);
        ReportStats stats = new ReportStats(total, active, expired, newContracts, totalValue);
        String body = buildReportEmail(org.getName(), monthLabel, year, stats);

        for (Users admin : admins) {
            if (admin.getManager() != null && admin.getManager().getEmail() != null) {
                try {
                    emailService.sendEmail(admin.getManager().getEmail(), subject, body);
                    if (logger.isInfoEnabled()) {
                        logger.info("Monthly report sent to {} for org {}",
                                admin.getUsername().replaceAll(CRLF_REGEX, "_"), org.getId());
                    }
                } catch (Exception e) {
                    logger.error("Failed to send report email to {}: {}",
                            admin.getUsername().replaceAll(CRLF_REGEX, "_"), e.getMessage());
                }
            }
        }
    }

    private record ReportStats(int total, int active, int expired, long newContracts, double totalValue) {}

    private String buildReportEmail(String orgName, String monthLabel, int year, ReportStats stats) {
        int total = stats.total();
        int active = stats.active();
        int expired = stats.expired();
        long newContracts = stats.newContracts();
        double totalValue = stats.totalValue();
        String escapedOrg = HtmlUtils.htmlEscape(orgName);
        String escapedPeriod = HtmlUtils.htmlEscape(monthLabel + " " + year);

        return "<!DOCTYPE html><html><head><style>"
                + "body{font-family:Arial,sans-serif;color:#333;margin:0;padding:0;}"
                + ".container{max-width:600px;margin:0 auto;padding:20px;}"
                + ".header{background-color:#1e40af;color:white;padding:20px;border-radius:5px;}"
                + ".stats{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:20px 0;}"
                + ".stat{background:#f3f4f6;padding:15px;border-radius:5px;text-align:center;}"
                + ".stat-value{font-size:24px;font-weight:bold;color:#1e40af;}"
                + ".footer{margin-top:20px;padding-top:20px;border-top:1px solid #ddd;font-size:12px;color:#666;}"
                + "</style></head><body>"
                + "<div class=\"container\">"
                + "<div class=\"header\"><h2>BCM Monthly Report — " + escapedPeriod + "</h2>"
                + "<p>Organization: " + escapedOrg + "</p></div>"
                + "<div class=\"stats\">"
                + "<div class=\"stat\"><div class=\"stat-value\">" + total + "</div><div>Total Contracts</div></div>"
                + "<div class=\"stat\"><div class=\"stat-value\">" + active + "</div><div>Active</div></div>"
                + "<div class=\"stat\"><div class=\"stat-value\">" + expired + "</div><div>Expired</div></div>"
                + "<div class=\"stat\"><div class=\"stat-value\">" + newContracts + "</div><div>New This Month</div></div>"
                + "<div class=\"stat\"><div class=\"stat-value\">&euro;" + String.format("%.0f", totalValue)
                + "</div><div>Financial Value This Month</div></div>"
                + "</div>"
                + "<div class=\"footer\"><p>This is an automated monthly report from Business Contracts Manager.</p>"
                + "<p>&#169; 2025 BCM - Business Contracts Manager</p></div>"
                + "</div></body></html>";
    }
}
