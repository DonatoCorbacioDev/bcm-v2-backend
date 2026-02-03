package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final ContractsRepository contractsRepository;
    private final ContractHistoryRepository contractHistoryRepository;
    private final UsersRepository usersRepository;
    private final IEmailService emailService;

    public ContractSchedulerService(
            ContractsRepository contractsRepository,
            ContractHistoryRepository contractHistoryRepository,
            UsersRepository usersRepository,
            IEmailService emailService) {
        this.contractsRepository = contractsRepository;
        this.contractHistoryRepository = contractHistoryRepository;
        this.usersRepository = usersRepository;
        this.emailService = emailService;
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
     * Sends expiration notification emails to managers for contracts expiring
     * soon. Runs every day at 9:00 AM.
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendExpirationNotifications() {
        logger.info("Starting contract expiration notifications check...");

        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysFromNow = today.plusDays(30);

        // Find active contracts expiring in the next 30 days
        List<Contracts> expiringContracts = contractsRepository.findByStatusAndEndDateBetween(
                ContractStatus.ACTIVE,
                today,
                thirtyDaysFromNow);

        int notificationsSent = 0;

        for (Contracts contract : expiringContracts) {
            Managers manager = contract.getManager();

            if (manager != null && manager.getEmail() != null) {
                try {
                    sendExpirationEmail(contract, manager);
                    notificationsSent++;
                    logger.info("Expiration notification sent for contract: {} to manager: {}",
                            contract.getContractNumber(),
                            manager.getEmail());
                } catch (Exception e) {
                    logger.error("Failed to send expiration notification for contract: {}",
                            contract.getContractNumber(), e);
                }
            } else {
                logger.warn("Contract {} has no manager assigned or manager has no email",
                        contract.getContractNumber());
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

    /**
     * Core logic for expiring overdue contracts.
     */
    private void performExpirationCheck() {
        logger.info("Starting automatic contract expiration check...");

        LocalDate today = LocalDate.now();
        List<Contracts> activeContracts = contractsRepository.findByStatus(ContractStatus.ACTIVE);

        int expiredCount = 0;

        for (Contracts contract : activeContracts) {
            if (contract.getEndDate() != null && contract.getEndDate().isBefore(today)) {
                logger.info("Expiring contract: {} (ID: {}) - End date: {}",
                        contract.getContractNumber(),
                        contract.getId(),
                        contract.getEndDate());

                ContractStatus previousStatus = contract.getStatus();
                contract.setStatus(ContractStatus.EXPIRED);
                contractsRepository.save(contract);

                createHistoryRecord(contract, previousStatus);
                expiredCount++;
            }
        }

        logger.info("Contract expiration check completed. {} contracts expired.", expiredCount);
    }

    /**
     * Sends expiration notification email to manager.
     */
    private void sendExpirationEmail(Contracts contract, Managers manager) {
        long daysUntilExpiration = ChronoUnit.DAYS.between(LocalDate.now(), contract.getEndDate());

        String subject = String.format("⚠️ Contract Expiring Soon: %s", contract.getContractNumber());

        String body = String.format(
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background-color: #f59e0b; color: white; padding: 20px; border-radius: 5px; }
                        .content { background-color: #f9fafb; padding: 20px; border-radius: 5px; margin-top: 20px; }
                        .detail { margin: 10px 0; }
                        .label { font-weight: bold; color: #555; }
                        .value { color: #111; }
                        .warning { background-color: #fef3c7; border-left: 4px solid #f59e0b; padding: 10px; margin: 20px 0; }
                        .footer { margin-top: 20px; padding-top: 20px; border-top: 1px solid #ddd; font-size: 12px; color: #666; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h2>Contract Expiration Notification</h2>
                        </div>
                        
                        <div class="content">
                            <p>Dear %s %s,</p>
                            
                            <div class="warning">
                                <strong>⚠️ Action Required</strong><br>
                                The following contract will expire in <strong>%d days</strong>.
                            </div>
                            
                            <h3>Contract Details:</h3>
                            <div class="detail">
                                <span class="label">Contract Number:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="detail">
                                <span class="label">Customer:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="detail">
                                <span class="label">Project:</span>
                                <span class="value">%s</span>
                            </div>
                            <div class="detail">
                                <span class="label">Expiration Date:</span>
                                <span class="value">%s</span>
                            </div>
                            
                            <p style="margin-top: 20px;">
                                Please review this contract and take necessary actions before the expiration date.
                            </p>
                        </div>
                        
                        <div class="footer">
                            <p>This is an automated notification from Business Contracts Manager.</p>
                            <p>© 2025 BCM - Business Contracts Manager</p>
                        </div>
                    </div>
                </body>
                </html>
                """,
                manager.getFirstName(),
                manager.getLastName(),
                daysUntilExpiration,
                contract.getContractNumber(),
                contract.getCustomerName(),
                contract.getProjectName(),
                contract.getEndDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        );

        emailService.sendEmail(manager.getEmail(), subject, body);
    }

    private void createHistoryRecord(Contracts contract, ContractStatus previousStatus) {
        Users systemUser = usersRepository.findByUsername("admin@example.com")
                .orElse(null);

        if (systemUser != null) {
            ContractHistory history = new ContractHistory();
            history.setContract(contract);
            history.setModifiedBy(systemUser);
            history.setModificationDate(LocalDateTime.now());
            history.setPreviousStatus(previousStatus);
            history.setNewStatus(ContractStatus.EXPIRED);

            contractHistoryRepository.save(history);
        } else {
            logger.warn("System user not found. History record not created for contract ID: {}",
                    contract.getId());
        }
    }
}
