package com.donatodev.bcm_backend.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.NotificationDTO;
import com.donatodev.bcm_backend.entity.Notification;
import com.donatodev.bcm_backend.entity.NotificationType;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.NotificationNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.repository.NotificationRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UsersRepository usersRepository;

    public NotificationService(NotificationRepository notificationRepository, UsersRepository usersRepository) {
        this.notificationRepository = notificationRepository;
        this.usersRepository = usersRepository;
    }

    @Transactional
    public void createForUser(Long userId, Long orgId, String title, String message, NotificationType type) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        notificationRepository.save(Notification.builder()
                .user(user)
                .orgId(orgId)
                .title(title)
                .message(message)
                .type(type)
                .build());
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        Users user = resolveCurrentUser();
        Long orgId = TenantContext.get();

        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + notificationId));

        if (!n.getUser().getId().equals(user.getId()) || !n.getOrgId().equals(orgId)) {
            throw new AccessDeniedException("Not authorized to access notification: " + notificationId);
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
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    }

    private NotificationDTO toDTO(Notification n) {
        return new NotificationDTO(n.getId(), n.getTitle(), n.getMessage(), n.getType(), n.isRead(), n.getCreatedAt());
    }
}
