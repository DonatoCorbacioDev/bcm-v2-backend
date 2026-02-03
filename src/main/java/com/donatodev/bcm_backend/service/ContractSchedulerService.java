package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ContractHistoryRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Scheduled service for automatic contract status updates.
 */
@Service
public class ContractSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(ContractSchedulerService.class);

    private final ContractsRepository contractsRepository;
    private final ContractHistoryRepository contractHistoryRepository;
    private final UsersRepository usersRepository;

    public ContractSchedulerService(
            ContractsRepository contractsRepository,
            ContractHistoryRepository contractHistoryRepository,
            UsersRepository usersRepository) {
        this.contractsRepository = contractsRepository;
        this.contractHistoryRepository = contractHistoryRepository;
        this.usersRepository = usersRepository;
    }

    /**
     * Automatically expires contracts that have passed their end date. Runs
     * every day at 1:00 AM.
     */
    @Scheduled(cron = "0 0 1 * * *") // Every day at 1:00 AM
    @Transactional
    public void expireOverdueContracts() {
        performExpirationCheck();
    }

    /**
     * Run contract expiration check once on application startup. This ensures
     * expired contracts are processed even if the app wasn't running at the
     * scheduled time (1:00 AM).
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

                // Create history record (using system user)
                createHistoryRecord(contract, previousStatus);

                expiredCount++;
            }
        }

        logger.info("Contract expiration check completed. {} contracts expired.", expiredCount);
    }

    private void createHistoryRecord(Contracts contract, ContractStatus previousStatus) {
        // Try to find a system admin user, or create a system record
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
