/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.UUID;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.AuditLogDTO;
import com.donatodev.bcm_backend.entity.AuditLog;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.AuditLogRepository;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void save(String action, String entityType, UUID entityId, String username, UUID orgId, String details) {
        AuditLog log = AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .username(username)
                .orgId(orgId)
                .timestamp(Instant.now())
                .details(details)
                .build();
        auditLogRepository.save(log);
    }

    /**
     * A change to an account's credentials or access (OWASP Logging Cheat
     * Sheet: sensitive operations), recorded under the account's own name and
     * organization: a password reset has no signed-in user to take them from.
     * Never the secret itself, only what happened.
     */
    @Transactional
    public void saveAboutUser(String action, Users user, String details) {
        UUID orgId = user.getOrganization() == null ? null : user.getOrganization().getId();
        save(action, "User", user.getId(), user.getUsername(), orgId, details);
    }

    public Page<AuditLogDTO> findAll(Pageable pageable) {
        UUID orgId = TenantContext.require();
        Page<AuditLog> logs = auditLogRepository.findAllByOrgIdOrderByTimestampDesc(orgId, pageable);
        return logs
                .map(log -> new AuditLogDTO(
                        log.getId(),
                        log.getAction(),
                        log.getEntityType(),
                        log.getEntityId(),
                        log.getUsername(),
                        log.getOrgId(),
                        log.getTimestamp(),
                        log.getDetails()
                ));
    }
}
