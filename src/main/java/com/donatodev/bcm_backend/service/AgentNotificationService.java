package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.NotificationType;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.UsersRepository;

@Service
public class AgentNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(AgentNotificationService.class);
    private static final String CRLF_REGEX = "[\r\n]";
    private static final String CONTRACT_PREFIX = "Contratto ";

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
                    "Contratto in scadenza",
                    CONTRACT_PREFIX + contract.getContractNumber() + " scade tra " + daysLeft
                            + " giorni (" + contract.getCustomerName() + ")",
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
                    "Contratto ad alto rischio rilevato",
                    CONTRACT_PREFIX + contract.getContractNumber() + " ha un punteggio di rischio del "
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
                    "Anomalia rilevata",
                    CONTRACT_PREFIX + contract.getContractNumber() + ": " + anomalyMessage,
                    NotificationType.WARNING
            );
        });
    }

    /**
     * Notifies every user in the contract's organization who can approve
     * contracts (ADMINs, plus anyone with the approval permission) that a
     * contract has been submitted for review.
     */
    public void notifySubmittedForReview(Contracts contract) {
        if (contract.getOrganization() == null) return;
        Long orgId = contract.getOrganization().getId();

        for (Users approver : approversForOrg(orgId)) {
            notificationService.createForUser(
                    approver.getId(),
                    orgId,
                    "Contratto in attesa di revisione",
                    CONTRACT_PREFIX + contract.getContractNumber() + " (" + contract.getCustomerName()
                            + ") è stato inviato per la revisione",
                    NotificationType.INFO
            );
        }
    }

    /**
     * Notifies the contract's manager that it was approved and is now active.
     */
    public void notifyWorkflowApproved(Contracts contract) {
        notifySubmitter(contract, "Contratto approvato",
                CONTRACT_PREFIX + contract.getContractNumber() + " è stato approvato ed è ora attivo",
                NotificationType.INFO);
    }

    /**
     * Notifies the contract's manager that it was rejected, with the
     * reviewer's comment.
     */
    public void notifyWorkflowRejected(Contracts contract, String comment) {
        notifySubmitter(contract, "Contratto respinto",
                CONTRACT_PREFIX + contract.getContractNumber() + " è stato riportato in bozza: " + comment,
                NotificationType.WARNING);
    }

    private void notifySubmitter(Contracts contract, String title, String message, NotificationType type) {
        Managers manager = contract.getManager();
        if (manager == null || manager.getEmail() == null) return;

        usersRepository.findByManagerEmailIgnoreCase(manager.getEmail()).ifPresent(user -> {
            if (user.getOrganization() == null) return;
            notificationService.createForUser(user.getId(), user.getOrganization().getId(), title, message, type);
        });
    }

    private List<Users> approversForOrg(Long orgId) {
        Map<Long, Users> byId = new LinkedHashMap<>();
        for (Users u : usersRepository.findByOrganizationIdAndRoleRole(orgId, "ADMIN")) {
            byId.put(u.getId(), u);
        }
        for (Users u : usersRepository.findByOrganizationIdAndCanApproveContractsTrue(orgId)) {
            byId.put(u.getId(), u);
        }
        return List.copyOf(byId.values());
    }
}
