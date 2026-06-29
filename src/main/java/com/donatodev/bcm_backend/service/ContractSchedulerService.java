package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ContractHistoryRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Scheduled service for automatic contract status updates and email
 * notifications.
 */
@Service
public class ContractSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(ContractSchedulerService.class);
    private static final String CRLF_REGEX = "[\r\n]";
    private static final int[] NOTIFICATION_THRESHOLDS = {30, 14, 7, 1};

    private final ContractsRepository contractsRepository;
    private final ContractHistoryRepository contractHistoryRepository;
    private final UsersRepository usersRepository;
    private final IEmailService emailService;
    private final AgentNotificationService agentNotificationService;

    public ContractSchedulerService(
            ContractsRepository contractsRepository,
            ContractHistoryRepository contractHistoryRepository,
            UsersRepository usersRepository,
            IEmailService emailService,
            AgentNotificationService agentNotificationService) {
        this.contractsRepository = contractsRepository;
        this.contractHistoryRepository = contractHistoryRepository;
        this.usersRepository = usersRepository;
        this.emailService = emailService;
        this.agentNotificationService = agentNotificationService;
    }

    /**
     * Automatically expires contracts that have passed their end date. Runs
     * every day at 1:00 AM.
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void expireOverdueContracts() {
        performExpirationCheck();
    }

    /**
     * Sends threshold-based expiration notification emails to managers.
     * Notifies exactly at 30, 14, 7 and 1 day(s) before expiry — never more
     * than once per threshold, preventing daily spam.
     * Runs every day at 9:00 AM.
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendExpirationNotifications() {
        logger.info("Starting contract expiration notifications check...");

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        int notificationsSent = 0;

        for (int days : NOTIFICATION_THRESHOLDS) {
            LocalDate targetDate = today.plusDays(days);
            List<Contracts> contracts = contractsRepository.findByStatusAndEndDate(
                    ContractStatus.ACTIVE, targetDate);

            for (Contracts contract : contracts) {
                notificationsSent += processContractForThreshold(contract, days);
            }
        }

        logger.info("Expiration notifications completed. {} notifications sent.", notificationsSent);
    }

    /**
     * Run contract expiration check once on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void runOnStartup() {
        logger.info("Running contract expiration check on application startup...");
        performExpirationCheck();
    }

    private int processContractForThreshold(Contracts contract, int days) {
        Managers manager = contract.getManager();
        String safeContractNumber = contract.getContractNumber().replaceAll(CRLF_REGEX, "_");
        if (manager == null || manager.getEmail() == null) {
            logger.warn("Contract {} has no manager assigned or manager has no email", safeContractNumber);
            return 0;
        }
        int sent = 0;
        try {
            sendExpirationEmail(contract, manager, days);
            sent = 1;
            String safeEmail = manager.getEmail().replaceAll(CRLF_REGEX, "_");
            logger.info("Expiration notification sent for contract: {} ({}d) to manager: {}",
                    safeContractNumber, days, safeEmail);
        } catch (Exception e) {
            logger.error("Failed to send expiration notification for contract: {}", safeContractNumber, e);
        }
        try {
            agentNotificationService.notifyExpiringContract(contract);
        } catch (Exception e) {
            logger.error("Failed to create in-app notification for contract: {}", safeContractNumber, e);
        }
        return sent;
    }

    /**
     * Core logic for expiring overdue contracts.
     */
    private void performExpirationCheck() {
        logger.info("Starting automatic contract expiration check...");

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<Contracts> overdueContracts = contractsRepository.findByStatusAndEndDateBefore(
                ContractStatus.ACTIVE, today);

        int expiredCount = 0;

        for (Contracts contract : overdueContracts) {
            String safeContractNumber = contract.getContractNumber().replaceAll(CRLF_REGEX, "_");
            logger.info("Expiring contract: {} (ID: {}) - End date: {}",
                    safeContractNumber,
                    contract.getId(),
                    contract.getEndDate());

            ContractStatus previousStatus = contract.getStatus();
            contract.setStatus(ContractStatus.EXPIRED);
            contractsRepository.save(contract);

            createHistoryRecord(contract, previousStatus);
            expiredCount++;
        }

        logger.info("Contract expiration check completed. {} contracts expired.", expiredCount);
    }

    /**
     * Sends a threshold-based expiration notification email to the manager.
     *
     * @param daysLeft exact days remaining until expiry (one of: 30, 14, 7, 1)
     */
    private void sendExpirationEmail(Contracts contract, Managers manager, int daysLeft) {
        String subject;
        String headerColor;
        String bannerBg;
        String bannerBorder;
        String urgencyText;

        if (daysLeft <= 1) {
            subject = String.format("🚨 Final Notice – Contract Expiring Tomorrow: %s", contract.getContractNumber());
            headerColor = "#dc2626";
            bannerBg = "#fee2e2";
            bannerBorder = "#dc2626";
            urgencyText = "🚨 Final Notice";
        } else if (daysLeft <= 7) {
            subject = String.format("🔔 Urgent: Contract Expiring in %d Days: %s", daysLeft, contract.getContractNumber());
            headerColor = "#ea580c";
            bannerBg = "#ffedd5";
            bannerBorder = "#ea580c";
            urgencyText = "🔔 Urgent";
        } else {
            subject = String.format("⚠️ Contract Expiring in %d Days: %s", daysLeft, contract.getContractNumber());
            headerColor = "#f59e0b";
            bannerBg = "#fef3c7";
            bannerBorder = "#f59e0b";
            urgencyText = "⚠️ Reminder";
        }

        String firstName = HtmlUtils.htmlEscape(manager.getFirstName());
        String lastName = HtmlUtils.htmlEscape(manager.getLastName());
        String contractNumber = HtmlUtils.htmlEscape(contract.getContractNumber());
        String customerName = HtmlUtils.htmlEscape(contract.getCustomerName());
        String projectName = contract.getProjectName() != null
                ? HtmlUtils.htmlEscape(contract.getProjectName())
                : "";
        String expirationDate = contract.getEndDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        String body = "<!DOCTYPE html><html><head><style>"
                + "body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }"
                + ".container { max-width: 600px; margin: 0 auto; padding: 20px; }"
                + ".header { background-color: " + headerColor + "; color: white; padding: 20px; border-radius: 5px; }"
                + ".content { background-color: #f9fafb; padding: 20px; border-radius: 5px; margin-top: 20px; }"
                + ".detail { margin: 10px 0; }"
                + ".label { font-weight: bold; color: #555; }"
                + ".value { color: #111; }"
                + ".banner { background-color: " + bannerBg + "; border-left: 4px solid " + bannerBorder + "; padding: 10px; margin: 20px 0; }"
                + ".footer { margin-top: 20px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 12px; color: #666; }"
                + "</style></head><body>"
                + "<div class=\"container\">"
                + "<div class=\"header\"><h2>Contract Expiration Notification</h2></div>"
                + "<div class=\"content\">"
                + "<p>Dear " + firstName + " " + lastName + ",</p>"
                + "<div class=\"banner\"><strong>" + urgencyText + " – Action Required</strong><br>"
                + "The following contract will expire in <strong>" + daysLeft + " day" + (daysLeft == 1 ? "" : "s") + "</strong>.</div>"
                + "<h3>Contract Details:</h3>"
                + "<div class=\"detail\"><span class=\"label\">Contract Number:</span>"
                + " <span class=\"value\">" + contractNumber + "</span></div>"
                + "<div class=\"detail\"><span class=\"label\">Customer:</span>"
                + " <span class=\"value\">" + customerName + "</span></div>"
                + "<div class=\"detail\"><span class=\"label\">Project:</span>"
                + " <span class=\"value\">" + projectName + "</span></div>"
                + "<div class=\"detail\"><span class=\"label\">Expiration Date:</span>"
                + " <span class=\"value\">" + expirationDate + "</span></div>"
                + "<p style=\"margin-top: 20px;\">Please review this contract and take necessary actions"
                + " before the expiration date.</p>"
                + "</div>"
                + "<div class=\"footer\">"
                + "<p>This is an automated notification from Business Contracts Manager.</p>"
                + "<p>&#169; 2025 BCM - Business Contracts Manager</p>"
                + "</div></div></body></html>";

        emailService.sendEmail(manager.getEmail(), subject, body);
    }

    private void createHistoryRecord(Contracts contract, ContractStatus previousStatus) {
        Users systemUser = usersRepository.findFirstByRoleRole("ADMIN")
                .orElse(null);

        if (systemUser != null) {
            ContractHistory history = new ContractHistory();
            history.setContract(contract);
            history.setModifiedBy(systemUser);
            history.setModificationDate(LocalDateTime.now(ZoneId.systemDefault()));
            history.setPreviousStatus(previousStatus);
            history.setNewStatus(ContractStatus.EXPIRED);

            contractHistoryRepository.save(history);
        } else {
            logger.warn("No admin user found. History record not created for contract ID: {}",
                    contract.getId());
        }
    }
}
