/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.donatodev.bcm_backend.dto.CounterpartyDTO;
import com.donatodev.bcm_backend.dto.CounterpartyInvoicingSummaryDTO;
import com.donatodev.bcm_backend.service.CounterpartyService;

import jakarta.validation.Valid;

/**
 * REST controller for managing Counterparties (customers/suppliers).
 * <p>
 * Reads are available to admins and managers; writes are admin-only, same
 * split as {@code BusinessAreaController}.
 */
@RestController
@RequestMapping("counterparties")
public class CounterpartyController {

    private final CounterpartyService counterpartyService;

    public CounterpartyController(CounterpartyService counterpartyService) {
        this.counterpartyService = counterpartyService;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping
    public ResponseEntity<List<CounterpartyDTO>> getAllCounterparties() {
        return ResponseEntity.ok(counterpartyService.getAllCounterparties());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/{id}")
    public ResponseEntity<CounterpartyDTO> getCounterpartyById(@PathVariable Long id) {
        return ResponseEntity.ok(counterpartyService.getCounterpartyById(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @GetMapping("/{id}/invoicing-summary")
    public ResponseEntity<CounterpartyInvoicingSummaryDTO> getInvoicingSummary(@PathVariable Long id) {
        return ResponseEntity.ok(counterpartyService.getInvoicingSummary(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<CounterpartyDTO> createCounterparty(@Valid @RequestBody CounterpartyDTO dto) {
        CounterpartyDTO created = counterpartyService.createCounterparty(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<CounterpartyDTO> updateCounterparty(@PathVariable Long id, @Valid @RequestBody CounterpartyDTO dto) {
        return ResponseEntity.ok(counterpartyService.updateCounterparty(id, dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCounterparty(@PathVariable Long id) {
        counterpartyService.deleteCounterparty(id);
        return ResponseEntity.noContent().build();
    }
}
