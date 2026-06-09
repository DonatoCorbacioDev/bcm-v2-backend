package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.NotificationType;
import com.donatodev.bcm_backend.repository.UsersRepository;

@Service
public class AgentNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(AgentNotificationService.class);
    private static final String CRLF_REGEX = "[\r\n]";
    private static final String CONTRACT_PREFIX = "Contract ";

    private final NotificationService notificationService;
    private final UsersRepository usersRepository;

    public AgentNotificationService(NotificationService notificationService, UsersRepository usersRepository) {
        this.notificationService = notificationService;
        this.usersRepository = usersRepository;
    }

    public void notifyExpiringContract(Contracts contract) {
        Managers manager = contract.getManager();
        if (manager == null || manager.getEmail() == null) return;

        usersRepository.findByManagerEmailIgnoreCase(manager.getEmail()).ifPresent(user -> {
            if (user.getOrganization() == null) return;
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(ZoneId.systemDefault()), contract.getEndDate());
            notificationService.createForUser(
                    user.getId(),
                    user.getOrganization().getId(),
                    "Contract Expiring Soon",
                    CONTRACT_PREFIX + contract.getContractNumber() + " expires in " + daysLeft
                            + " days (" + contract.getCustomerName() + ")",
                    NotificationType.WARNING
            );
        });
    }

    public void notifyHighRisk(Contracts contract, double riskScore) {
        Managers manager = contract.getManager();
        if (manager == null || manager.getEmail() == null) return;

        usersRepository.findByManagerEmailIgnoreCase(manager.getEmail()).ifPresent(user -> {
            if (user.getOrganization() == null) return;
            String safeNumber = contract.getContractNumber().replaceAll(CRLF_REGEX, "_");
            logger.info("Creating high-risk notification for contract: {} (score: {})", safeNumber, riskScore);
            notificationService.createForUser(
                    user.getId(),
                    user.getOrganization().getId(),
                    "High Risk Contract Detected",
                    CONTRACT_PREFIX + contract.getContractNumber() + " has a risk score of "
                            + String.format("%.0f%%", riskScore * 100),
                    NotificationType.ERROR
            );
        });
    }

    public void notifyAnomalyDetected(Contracts contract, String anomalyMessage) {
        Managers manager = contract.getManager();
        if (manager == null || manager.getEmail() == null) return;

        usersRepository.findByManagerEmailIgnoreCase(manager.getEmail()).ifPresent(user -> {
            if (user.getOrganization() == null) return;
            notificationService.createForUser(
                    user.getId(),
                    user.getOrganization().getId(),
                    "Anomaly Detected",
                    CONTRACT_PREFIX + contract.getContractNumber() + ": " + anomalyMessage,
                    NotificationType.WARNING
            );
        });
    }
}
