package com.donatodev.bcm_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.donatodev.bcm_backend.dto.TotpCodeRequest;
import com.donatodev.bcm_backend.dto.TotpConfirmResponse;
import com.donatodev.bcm_backend.dto.TotpSetupResponse;
import com.donatodev.bcm_backend.dto.TotpStatusResponse;
import com.donatodev.bcm_backend.service.TwoFactorAuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users/me/2fa")
public class TwoFactorAuthController {

    private final TwoFactorAuthService twoFactorAuthService;

    public TwoFactorAuthController(TwoFactorAuthService twoFactorAuthService) {
        this.twoFactorAuthService = twoFactorAuthService;
    }

    @GetMapping("/status")
    public ResponseEntity<TotpStatusResponse> status() {
        return ResponseEntity.ok(twoFactorAuthService.status());
    }

    @PostMapping("/setup")
    public ResponseEntity<TotpSetupResponse> setup() {
        return ResponseEntity.ok(twoFactorAuthService.setup());
    }

    @PostMapping("/confirm")
    public ResponseEntity<TotpConfirmResponse> confirm(@Valid @RequestBody TotpCodeRequest request) {
        return ResponseEntity.ok(twoFactorAuthService.confirm(request.code()));
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@Valid @RequestBody TotpCodeRequest request) {
        twoFactorAuthService.disable(request.code());
        return ResponseEntity.noContent().build();
    }
}
