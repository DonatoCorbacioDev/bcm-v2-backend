/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Bulk-deletes audit log entries older than the given cutoff, backing the
     * retention/purge policy in {@code AuditLogRetentionService}.
     *
     * @param cutoff entries with a timestamp before this instant are deleted
     * @return the number of deleted rows
     */
    @Modifying
    @Transactional
    long deleteByTimestampBefore(Instant cutoff);
}
