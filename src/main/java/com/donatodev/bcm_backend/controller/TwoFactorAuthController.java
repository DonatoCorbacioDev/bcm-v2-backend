/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;

import com.donatodev.bcm_backend.dto.TotpCodeRequest;
import com.donatodev.bcm_backend.dto.TotpConfirmResponse;
import com.donatodev.bcm_backend.dto.TotpSetupResponse;
import com.donatodev.bcm_backend.dto.TotpStatusResponse;
import com.donatodev.bcm_backend.service.TwoFactorAuthService;
import com.donatodev.bcm_backend.security.DisabledInPublicDemo;
import com.donatodev.bcm_backend.auth.CurrentSessionRenewer;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users/me/2fa")
public class TwoFactorAuthController {

    private final TwoFactorAuthService twoFactorAuthService;
    private final CurrentSessionRenewer currentSessionRenewer;

    public TwoFactorAuthController(TwoFactorAuthService twoFactorAuthService,
                                   CurrentSessionRenewer currentSessionRenewer) {
        this.twoFactorAuthService = twoFactorAuthService;
        this.currentSessionRenewer = currentSessionRenewer;
    }

    @GetMapping("/status")
    public ResponseEntity<TotpStatusResponse> status() {
        return ResponseEntity.ok(twoFactorAuthService.status());
    }

    @DisabledInPublicDemo
    @PostMapping("/setup")
    public ResponseEntity<TotpSetupResponse> setup() {
        return ResponseEntity.ok(twoFactorAuthService.setup());
    }

    @DisabledInPublicDemo
    @PostMapping("/confirm")
    public ResponseEntity<TotpConfirmResponse> confirm(@Valid @RequestBody TotpCodeRequest request) {
        TotpConfirmResponse response = twoFactorAuthService.confirm(request.code());
        // Every other session ended with the change; this one goes on.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, currentSessionRenewer.renewedRefreshCookie())
                .body(response);
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@Valid @RequestBody TotpCodeRequest request) {
        twoFactorAuthService.disable(request.code());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, currentSessionRenewer.renewedRefreshCookie())
                .build();
    }
}
