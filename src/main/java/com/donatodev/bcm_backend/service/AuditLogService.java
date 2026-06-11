package com.donatodev.bcm_backend.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.AuditLogDTO;
import com.donatodev.bcm_backend.entity.AuditLog;
import com.donatodev.bcm_backend.repository.AuditLogRepository;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void save(String action, String entityType, Long entityId, String username, Long orgId, String details) {
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

    public Page<AuditLogDTO> findAll(Pageable pageable) {
        Long orgId = TenantContext.get();
        Page<AuditLog> logs = (orgId != null)
                ? auditLogRepository.findAllByOrgIdOrderByTimestampDesc(orgId, pageable)
                : auditLogRepository.findAllByOrderByTimestampDesc(pageable);
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
