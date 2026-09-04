package com.donatodev.bcm_backend.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.NotificationDTO;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Notification;
import com.donatodev.bcm_backend.entity.NotificationType;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.NotificationNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.NotificationRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@Service
public class NotificationService {

    private static final int TITLE_MAX_LENGTH = 200;

    private final NotificationRepository notificationRepository;
    private final UsersRepository usersRepository;
    private final ContractsRepository contractsRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            UsersRepository usersRepository,
            ContractsRepository contractsRepository) {
        this.notificationRepository = notificationRepository;
        this.usersRepository = usersRepository;
        this.contractsRepository = contractsRepository;
    }

    @Transactional
    public void createForUser(Long userId, Long orgId, String title, String message, NotificationType type) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Utente non trovato: " + userId));

        notificationRepository.save(Notification.builder()
                .user(user)
                .orgId(orgId)
                .title(title)
                .message(message)
                .type(type)
                .build());
    }

    // Re-validates contract ownership against the caller's own org — this is the
    // human-confirmation step for an agent-proposed reminder, so it must never
    // trust the contractId at face value just because the ML service already
    // checked it once; the write only happens here, behind normal auth.
    @Transactional
    public void createReminderForCurrentUser(Long contractId, String message) {
        Users user = resolveCurrentUser();
        Long orgId = TenantContext.get();

        Contracts contract = (orgId != null
                ? contractsRepository.findByIdAndOrganization_Id(contractId, orgId)
                : contractsRepository.findById(contractId))
                .orElseThrow(() -> new ContractNotFoundException("Contratto ID " + contractId + " non trovato"));

        // Org membership alone isn't enough for a MANAGER: the agent's proposal
        // is only scoped by org (see bcm-v2-ml's _propose_reminder), so a manager
        // could otherwise confirm a reminder on a colleague's contract.
        if ("MANAGER".equals(user.getRole().getRole())) {
            Long managerId = user.getManager() != null ? user.getManager().getId() : null;
            Managers contractManager = contract.getManager();
            if (managerId == null || contractManager == null || !managerId.equals(contractManager.getId())) {
                throw new ContractNotFoundException("Contratto ID " + contractId + " non trovato");
            }
        }

        String title = "Promemoria: " + contract.getCustomerName();
        if (title.length() > TITLE_MAX_LENGTH) {
            title = title.substring(0, TITLE_MAX_LENGTH);
        }

        createForUser(user.getId(), orgId, title, message, NotificationType.INFO);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        Users user = resolveCurrentUser();
        Long orgId = TenantContext.get();

        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException("Notifica non trovata: " + notificationId));

        if (!n.getUser().getId().equals(user.getId()) || !n.getOrgId().equals(orgId)) {
            throw new AccessDeniedException("non autorizzato ad accedere alla notifica: " + notificationId);
        }

        n.setRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void markAllAsRead() {
        Users user = resolveCurrentUser();
        Long orgId = TenantContext.get();

        List<Notification> unread = notificationRepository
                .findByUserIdAndOrgIdOrderByCreatedAtDesc(user.getId(), orgId)
                .stream()
                .filter(n -> !n.isRead())
                .toList();

        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> getForCurrentUser() {
        Users user = resolveCurrentUser();
        Long orgId = TenantContext.get();

        return notificationRepository
                .findByUserIdAndOrgIdOrderByCreatedAtDesc(user.getId(), orgId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        Users user = resolveCurrentUser();
        Long orgId = TenantContext.get();
        return notificationRepository.countByUserIdAndOrgIdAndReadFalse(user.getId(), orgId);
    }

    private Users resolveCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return usersRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("Utente non trovato: " + username));
    }

    private NotificationDTO toDTO(Notification n) {
        return new NotificationDTO(n.getId(), n.getTitle(), n.getMessage(), n.getType(), n.isRead(), n.getCreatedAt());
    }
}
