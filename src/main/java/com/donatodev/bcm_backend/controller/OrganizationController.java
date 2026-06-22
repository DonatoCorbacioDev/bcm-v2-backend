package com.donatodev.bcm_backend.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.donatodev.bcm_backend.auth.AccessTokenResponse;
import com.donatodev.bcm_backend.auth.AuthResponseDTO;
import com.donatodev.bcm_backend.auth.RefreshCookieFactory;
import com.donatodev.bcm_backend.dto.OrganizationDTO;
import com.donatodev.bcm_backend.dto.OrganizationRegistrationRequest;
import com.donatodev.bcm_backend.dto.UpdateOrganizationRequest;
import com.donatodev.bcm_backend.service.OrganizationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final RefreshCookieFactory refreshCookieFactory;

    public OrganizationController(OrganizationService organizationService, RefreshCookieFactory refreshCookieFactory) {
        this.organizationService = organizationService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/register")
    public ResponseEntity<AccessTokenResponse> register(
            @Valid @RequestBody OrganizationRegistrationRequest request) {
        AuthResponseDTO response = organizationService.registerOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(response.refreshToken()).toString())
                .body(new AccessTokenResponse(response.token()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrganizationDTO> getMyOrganization() {
        return ResponseEntity.ok(organizationService.getMyOrganization());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrganizationDTO> updateMyOrganization(
            @Valid @RequestBody UpdateOrganizationRequest request) {
        return ResponseEntity.ok(organizationService.updateMyOrganization(request));
    }
}
