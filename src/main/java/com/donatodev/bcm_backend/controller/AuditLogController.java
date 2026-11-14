/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.donatodev.bcm_backend.dto.AuditLogDTO;
import com.donatodev.bcm_backend.service.AuditLogService;
import java.util.Set;
import com.donatodev.bcm_backend.util.PageLimits;

@RestController
@RequestMapping("/audit-logs")
public class AuditLogController {

    /**
     * The columns a client may sort the audit by (OWASP Input Validation Cheat
     * Sheet): any other name is refused with 400 instead of reaching the query.
     */
    static final Set<String> SORTABLE = Set.of("timestamp", "action", "entityType", "username");

    private final AuditLogService auditLogService;
    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogDTO>> getAuditLogs(
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new IllegalArgumentException("Ordinamento non consentito: " + String.join(", ", SORTABLE.stream().sorted().toList()));
            }
        }
        if (pageable.getPageSize() > PageLimits.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("La dimensione della pagina deve essere tra 1 e " + PageLimits.MAX_PAGE_SIZE);
        }
        return ResponseEntity.ok(auditLogService.findAll(pageable));
    }
}
