package com.donatodev.bcm_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    /**
     * Finds all audit log entries for the given organization, ordered by
     * timestamp descending. Used to prevent cross-tenant access to audit logs.
     *
     * @param orgId the organization ID
     * @param pageable pagination information
     * @return a page of audit log entries belonging to the given organization
     */
    Page<AuditLog> findAllByOrgIdOrderByTimestampDesc(Long orgId, Pageable pageable);
}
