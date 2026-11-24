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
import com.donatodev.bcm_backend.auth.ReauthenticationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users/me/2fa")
public class TwoFactorAuthController {

    private final TwoFactorAuthService twoFactorAuthService;
    private final CurrentSessionRenewer currentSessionRenewer;
    // Here, not in the service: ReauthenticationService already depends on it.
    private final ReauthenticationService reauthenticationService;

    public TwoFactorAuthController(TwoFactorAuthService twoFactorAuthService,
                                   CurrentSessionRenewer currentSessionRenewer,
                                   ReauthenticationService reauthenticationService) {
        this.twoFactorAuthService = twoFactorAuthService;
        this.currentSessionRenewer = currentSessionRenewer;
        this.reauthenticationService = reauthenticationService;
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

    /**
     * Turns 2FA off only after the password and the current code are proved
     * again (POST /auth/reauth, answered 428 without it): there they go through
     * the no-replay rule, the lockout and the per-IP limit of the login, so a
     * stolen session can neither replay the login's code nor guess one here.
     */
    @DisabledInPublicDemo
    @PostMapping("/disable")
    public ResponseEntity<Void> disable() {
        reauthenticationService.require();
        twoFactorAuthService.disable();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, currentSessionRenewer.renewedRefreshCookie())
                .build();
    }
}
