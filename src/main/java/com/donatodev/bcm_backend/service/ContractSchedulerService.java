/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.Year;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;

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
    private static final Pattern CRLF_PATTERN = Pattern.compile("[\r\n]");
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
     * Sends threshold-based reminder emails to managers, exactly at 30, 14, 7
     * and 1 day(s) before the date that matters — never more than once per
     * threshold, preventing daily spam. For a contract that renews tacitly
     * with a stated notice period that date is the last day to give notice
     * (by its end date it would already be too late), otherwise the end date.
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
                // Reminded about its notice deadline instead, below.
                if (contract.noticeDeadline() != null) {
                    continue;
                }
                notificationsSent += processContractForThreshold(contract, days, ReminderKind.EXPIRY);
            }
        }

        for (Contracts contract : contractsRepository.findByStatusAndAutoRenewTrueAndNoticeDaysIsNotNull(ContractStatus.ACTIVE)) {
            long daysToDeadline = ChronoUnit.DAYS.between(today, contract.noticeDeadline());
            for (int days : NOTIFICATION_THRESHOLDS) {
                if (daysToDeadline == days) {
                    notificationsSent += processContractForThreshold(contract, days, ReminderKind.NOTICE_DEADLINE);
                }
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

    private int processContractForThreshold(Contracts contract, int days, ReminderKind kind) {
        Managers manager = contract.getManager();
        String safeContractNumber = CRLF_PATTERN.matcher(contract.getContractNumber()).replaceAll("_");
        if (manager == null || manager.getEmail() == null) {
            logger.warn("Contract {} has no manager assigned or manager has no email", safeContractNumber);
            return 0;
        }
        int sent = 0;
        try {
            sendReminderEmail(contract, manager, days, kind);
            sent = 1;
            String safeEmail = CRLF_PATTERN.matcher(manager.getEmail()).replaceAll("_");
            logger.info("{} notification sent for contract: {} ({}d) to manager: {}",
                    kind, safeContractNumber, days, safeEmail);
        } catch (Exception e) {
            logger.error("Failed to send expiration notification for contract: {}", safeContractNumber, e);
        }
        try {
            if (kind == ReminderKind.NOTICE_DEADLINE) {
                agentNotificationService.notifyNoticeDeadline(contract);
            } else {
                agentNotificationService.notifyExpiringContract(contract);
            }
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
        int renewedCount = 0;

        for (Contracts contract : overdueContracts) {
            String safeContractNumber = CRLF_PATTERN.matcher(contract.getContractNumber()).replaceAll("_");
            // Not given notice in time: it renewed, as the contract says.
            if (contract.renewsTacitly()) {
                renewTacitly(contract, today, safeContractNumber);
                renewedCount++;
                continue;
            }
            // Renews tacitly, but for how long isn't stated: marking it
            // EXPIRED would be false, extending it a guess. Left ACTIVE for
            // the user to update.
            if (Boolean.TRUE.equals(contract.getAutoRenew())) {
                logger.warn("Contract {} (ID: {}) renews tacitly but has no renewal length: left ACTIVE",
                        safeContractNumber, contract.getId());
                continue;
            }
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

        logger.info("Contract expiration check completed. {} contracts expired, {} renewed tacitly.",
                expiredCount, renewedCount);
    }

    /**
     * Moves the end date forward by the renewal length, as many times as
     * needed to reach today (the job may not have run for a while). The
     * status stays ACTIVE, so no status-history row: the manager gets an
     * in-app notification instead. Financial values are not generated for
     * the new period here; the user does it from the contract.
     */
    private void renewTacitly(Contracts contract, LocalDate today, String safeContractNumber) {
        LocalDate previousEndDate = contract.getEndDate();
        LocalDate newEndDate = previousEndDate;
        while (newEndDate.isBefore(today)) {
            newEndDate = newEndDate.plusMonths(contract.getRenewalMonths());
        }
        contract.setEndDate(newEndDate);
        contractsRepository.save(contract);
        logger.info("Contract {} (ID: {}) renewed tacitly: end date {} -> {}",
                safeContractNumber, contract.getId(), previousEndDate, newEndDate);
        try {
            agentNotificationService.notifyTacitRenewal(contract, previousEndDate);
        } catch (Exception e) {
            logger.error("Failed to create in-app notification for renewed contract: {}", safeContractNumber, e);
        }
    }

    /** Which date a reminder is about. */
    enum ReminderKind { EXPIRY, NOTICE_DEADLINE }

    /**
     * Sends a threshold-based reminder email to the manager, about the end
     * date or, for a contract renewing tacitly, the last day to give notice.
     *
     * @param daysLeft exact days remaining until that date (one of: 30, 14, 7, 1)
     */
    private void sendReminderEmail(Contracts contract, Managers manager, int daysLeft, ReminderKind kind) {
        boolean notice = kind == ReminderKind.NOTICE_DEADLINE;
        String number = contract.getContractNumber();
        String headerColor;
        String bannerBg;
        String bannerBorder;
        String urgencyText;
        String subjectPrefix;

        if (daysLeft <= 1) {
            subjectPrefix = "[BCM] Ultimo avviso — ";
            headerColor = "#dc2626";
            bannerBg = "#fee2e2";
            bannerBorder = "#dc2626";
            urgencyText = "Ultimo avviso";
        } else if (daysLeft <= 7) {
            subjectPrefix = "[BCM] Urgente — ";
            headerColor = "#ea580c";
            bannerBg = "#ffedd5";
            bannerBorder = "#ea580c";
            urgencyText = "Urgente";
        } else {
            subjectPrefix = "[BCM] Promemoria — ";
            headerColor = "#f59e0b";
            bannerBg = "#fef3c7";
            bannerBorder = "#f59e0b";
            urgencyText = "Promemoria";
        }

        String subject;
        if (notice) {
            subject = subjectPrefix + (daysLeft <= 1
                    ? String.format("domani è l'ultimo giorno per disdire il contratto %s", number)
                    : String.format("restano %d giorni per disdire il contratto %s", daysLeft, number));
        } else {
            subject = subjectPrefix + (daysLeft <= 1
                    ? String.format("il contratto %s scade domani", number)
                    : String.format("il contratto %s scade tra %d giorni", number, daysLeft));
        }

        DateTimeFormatter italianDate = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String firstName = HtmlUtils.htmlEscape(manager.getFirstName());
        String lastName = HtmlUtils.htmlEscape(manager.getLastName());
        String contractNumber = HtmlUtils.htmlEscape(number);
        String customerName = HtmlUtils.htmlEscape(contract.getCounterparty().getName());
        String projectName = contract.getProjectName() != null
                ? HtmlUtils.htmlEscape(contract.getProjectName())
                : "";
        String expirationDate = contract.getEndDate().format(italianDate);
        String dayWord = daysLeft == 1 ? " giorno" : " giorni";

        String title;
        String bannerText;
        String dateRows;
        String closing;
        if (notice) {
            String renewal = contract.getRenewalMonths() != null
                    ? "si rinnova automaticamente per altri " + contract.getRenewalMonths() + " mesi"
                    : "si rinnova automaticamente";
            title = "Termine di disdetta in arrivo";
            bannerText = "Restano <strong>" + daysLeft + dayWord + "</strong> per disdire il contratto seguente: "
                    + "dopo, " + renewal + ".";
            dateRows = "<div class=\"detail\"><span class=\"label\">Ultimo giorno per disdire:</span>"
                    + " <span class=\"value\">" + contract.noticeDeadline().format(italianDate) + "</span></div>"
                    + "<div class=\"detail\"><span class=\"label\">Data di scadenza:</span>"
                    + " <span class=\"value\">" + expirationDate + "</span></div>"
                    + "<div class=\"detail\"><span class=\"label\">Preavviso:</span>"
                    + " <span class=\"value\">" + contract.getNoticeDays() + " giorni</span></div>";
            closing = "Decidi se rinnovarlo. Per disdirlo, verifica le modalità previste dal contratto "
                    + "e invia la disdetta entro il termine.";
        } else {
            title = "Contratto in scadenza";
            bannerText = "Il contratto seguente scade tra <strong>" + daysLeft + dayWord + "</strong>.";
            dateRows = "<div class=\"detail\"><span class=\"label\">Data di scadenza:</span>"
                    + " <span class=\"value\">" + expirationDate + "</span></div>";
            closing = "Verifica il contratto e intervieni prima della data di scadenza.";
        }

        String body = "<!DOCTYPE html><html lang=\"it\"><head><style>"
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
                + "<div class=\"header\"><h2>" + title + "</h2></div>"
                + "<div class=\"content\">"
                + "<p>Gentile " + firstName + " " + lastName + ",</p>"
                + "<div class=\"banner\"><strong>" + urgencyText + " – azione richiesta</strong><br>"
                + bannerText + "</div>"
                + "<h3>Dettagli del contratto:</h3>"
                + "<div class=\"detail\"><span class=\"label\">Numero contratto:</span>"
                + " <span class=\"value\">" + contractNumber + "</span></div>"
                + "<div class=\"detail\"><span class=\"label\">Controparte:</span>"
                + " <span class=\"value\">" + customerName + "</span></div>"
                + "<div class=\"detail\"><span class=\"label\">Progetto:</span>"
                + " <span class=\"value\">" + projectName + "</span></div>"
                + dateRows
                + "<p style=\"margin-top: 20px;\">" + closing + "</p>"
                + "</div>"
                + "<div class=\"footer\">"
                + "<p>Notifica automatica di Business Contracts Manager.</p>"
                + "<p>&#169; " + Year.now(ZoneId.systemDefault()) + " BCM - Business Contracts Manager</p>"
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
