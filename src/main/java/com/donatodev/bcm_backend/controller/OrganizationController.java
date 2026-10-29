/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.controller;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.donatodev.bcm_backend.auth.AccessTokenResponse;
import com.donatodev.bcm_backend.auth.AuthResponseDTO;
import com.donatodev.bcm_backend.auth.RefreshCookieFactory;
import com.donatodev.bcm_backend.dto.ConfirmOrganizationDeletionRequest;
import com.donatodev.bcm_backend.dto.ConfirmRegistrationRequest;
import com.donatodev.bcm_backend.dto.DeletionCodeSentDTO;
import com.donatodev.bcm_backend.dto.OrganizationDTO;
import com.donatodev.bcm_backend.dto.OrganizationDeletionDTO;
import com.donatodev.bcm_backend.dto.OrganizationRegistrationRequest;
import com.donatodev.bcm_backend.dto.OrganizationUsageDTO;
import com.donatodev.bcm_backend.dto.RegistrationStartedDTO;
import com.donatodev.bcm_backend.dto.RequestOrganizationDeletionRequest;
import com.donatodev.bcm_backend.dto.RequestOrigin;
import com.donatodev.bcm_backend.dto.ResendRegistrationCodeRequest;
import com.donatodev.bcm_backend.dto.UpdateOrganizationRequest;
import com.donatodev.bcm_backend.service.OrganizationRegistrationService;
import com.donatodev.bcm_backend.service.OrganizationDeletionService;
import com.donatodev.bcm_backend.service.OrganizationExportService;
import com.donatodev.bcm_backend.service.OrganizationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrganizationRegistrationService registrationService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final OrganizationExportService exportService;
    private final OrganizationDeletionService deletionService;

    public OrganizationController(OrganizationService organizationService,
            OrganizationRegistrationService registrationService,
            RefreshCookieFactory refreshCookieFactory,
            OrganizationExportService exportService,
            OrganizationDeletionService deletionService) {
        this.organizationService = organizationService;
        this.registrationService = registrationService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.exportService = exportService;
        this.deletionService = deletionService;
    }

    /** Step 1: validates the data and emails a confirmation code. Nothing is created yet. */
    @PostMapping("/register")
    public ResponseEntity<RegistrationStartedDTO> register(
            @Valid @RequestBody OrganizationRegistrationRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(registrationService.start(request, RequestOrigin.from(httpRequest)));
    }

    @PostMapping("/register/resend")
    public ResponseEntity<RegistrationStartedDTO> resendRegistrationCode(
            @Valid @RequestBody ResendRegistrationCodeRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(registrationService.resend(request.registrationId()));
    }

    /** Step 2: the emailed code creates the organization and signs the Terms acceptance. */
    @PostMapping("/register/confirm")
    public ResponseEntity<AccessTokenResponse> confirmRegistration(
            @Valid @RequestBody ConfirmRegistrationRequest request, HttpServletRequest httpRequest) {
        AuthResponseDTO response = registrationService.confirm(
                request.registrationId(), request.code(), RequestOrigin.from(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(response.refreshToken()).toString())
                .body(new AccessTokenResponse(response.token()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrganizationDTO> getMyOrganization() {
        return ResponseEntity.ok(organizationService.getMyOrganization());
    }

    @GetMapping("/me/usage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrganizationUsageDTO> getMyOrganizationUsage() {
        return ResponseEntity.ok(organizationService.getUsage());
    }

    /** All of the organization's data as a ZIP (Terms sez. 13, GDPR art. 20). */
    @GetMapping("/me/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StreamingResponseBody> exportMyOrganization() {
        // Resolved here, on the request thread: the body below runs later on an
        // async thread, where TenantContext is no longer set.
        OrganizationDTO org = organizationService.getMyOrganization();
        exportService.recordExport(org.id());
        String fileName = "bcm-export-%s-%s.zip".formatted(org.slug(), LocalDate.now(ZoneId.systemDefault()));
        StreamingResponseBody body = out -> exportService.writeExport(org.id(), out);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    /** Deletion step 1: password + organization name; emails a confirmation code. */
    @PostMapping("/me/deletion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeletionCodeSentDTO> requestDeletion(
            @Valid @RequestBody RequestOrganizationDeletionRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(deletionService.request(request.password(), request.organizationName()));
    }

    /** Deletion step 2: the emailed code schedules the deletion 30 days ahead. */
    @PostMapping("/me/deletion/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrganizationDeletionDTO> confirmDeletion(
            @Valid @RequestBody ConfirmOrganizationDeletionRequest request) {
        return ResponseEntity.ok(deletionService.confirm(request.code()));
    }

    /** Calls off a scheduled deletion. */
    @DeleteMapping("/me/deletion")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cancelDeletion() {
        deletionService.cancel();
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrganizationDTO> updateMyOrganization(
            @Valid @RequestBody UpdateOrganizationRequest request) {
        return ResponseEntity.ok(organizationService.updateMyOrganization(request));
    }
}
