/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.donatodev.bcm_backend.dto.HomeAttentionDTO;
import com.donatodev.bcm_backend.service.HomeAttentionService;

/** The home page: what needs the user's attention in the next days. */
@RestController
@RequestMapping("/home")
public class HomeController {

    private final HomeAttentionService homeAttentionService;

    public HomeController(HomeAttentionService homeAttentionService) {
        this.homeAttentionService = homeAttentionService;
    }

    /**
     * Notice deadlines, expiries, invoices to match and changed IBANs, most
     * urgent first, scoped to the caller's organization (and, for a manager,
     * to their own contracts).
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/attention")
    public ResponseEntity<HomeAttentionDTO> getAttention() {
        return ResponseEntity.ok(homeAttentionService.getAttention());
    }
}
