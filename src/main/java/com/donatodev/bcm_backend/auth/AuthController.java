/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;

import com.donatodev.bcm_backend.dto.*;
import com.donatodev.bcm_backend.entity.*;
import com.donatodev.bcm_backend.exception.RefreshTokenException;
import com.donatodev.bcm_backend.service.*;
import com.donatodev.bcm_backend.service.RefreshTokenService;

import jakarta.validation.Valid;

/**
 * REST controller for login (with two-factor), token refresh and logout,
 * password reset, accepting an invitation, and the current user's profile.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final PasswordResetMailer passwordResetMailer;
    private final PasswordResetTokenService passwordResetTokenService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final TwoFactorAuthService twoFactorAuthService;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Autowired
    public AuthController(
            AuthService authService,
            UserService userService,
            PasswordResetMailer passwordResetMailer,
            PasswordResetTokenService passwordResetTokenService,
            RefreshTokenService refreshTokenService,
            RefreshCookieFactory refreshCookieFactory,
            TwoFactorAuthService twoFactorAuthService) {
        this.authService = authService;
        this.userService = userService;
        this.passwordResetMailer = passwordResetMailer;
        this.passwordResetTokenService = passwordResetTokenService;
        this.refreshTokenService = refreshTokenService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.twoFactorAuthService = twoFactorAuthService;
    }

    public AuthController() {
        this(null, null, null, null, null, null, null);
    }

    // There is deliberately no public POST /auth/register (nor the
    // GET /auth/verify that confirmed it): it took a managerId and a roleId
    // from the body, so anyone could create an ADMIN inside another
    // organization (OWASP API1/API3/API5). New organizations sign up through
    // /organizations/register, people join one by invitation.

    /**
     * Authenticates a user and returns a JWT access token. The refresh token
     * is never exposed in the response body: it is set as an HttpOnly cookie
     * so client-side JavaScript cannot read it.
     * <p>
     * If the account has 2FA enabled, no tokens are issued yet: the response
     * carries {@code mfaRequired=true} and a short-lived {@code mfaToken}
     * that must be exchanged via {@link #verifyTwoFactor} for the real
     * tokens.
     *
     * @param request the login request with username and password
     * @return the access token if authentication is successful, or an
     * MFA-pending response otherwise
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody AuthRequestDTO request) {
        LoginOutcome outcome = authService.authenticate(
                request.username(), request.password(), request.organizationSlug());

        if (outcome.mfaRequired()) {
            return ResponseEntity.ok(new LoginResponseDTO(null, true, outcome.mfaToken()));
        }

        AuthResponseDTO response = outcome.tokens();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(response.refreshToken()).toString())
                .body(new LoginResponseDTO(response.token(), false, null));
    }

    /**
     * Completes a two-step login: exchanges the MFA-pending token from
     * {@link #login} plus a TOTP or recovery code for the real access token.
     *
     * @param request the pending MFA token and the verification code
     * @return the access token if the code is valid
     */
    @PostMapping("/2fa/verify")
    public ResponseEntity<LoginResponseDTO> verifyTwoFactor(@Valid @RequestBody MfaVerifyRequest request) {
        AuthResponseDTO response = twoFactorAuthService.verifyLogin(request.mfaToken(), request.code());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(response.refreshToken()).toString())
                .body(new LoginResponseDTO(response.token(), false, null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RefreshTokenException("Refresh token non trovato");
        }
        RefreshTokenService.RotatedTokens rotated = refreshTokenService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(rotated.refreshToken()).toString())
                .body(new AccessTokenResponse(rotated.accessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeToken(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
                .build();
    }

    /**
     * Handles password reset requests by sending a reset link.
     * <p>
     * Always returns the same generic response regardless of whether the
     * email is registered, to avoid leaking which addresses have an account.
     *
     * @param request contains the user's email
     * @return 200 OK with a generic confirmation message
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequestDTO request) {
        // Looked up and sent off the request thread: the answer takes the same
        // time, and is the same, whether the address has an account or not.
        passwordResetMailer.requestReset(request.email());
        return ResponseEntity.ok("Se esiste un account con questa email, è stato inviato un link per reimpostare la password.");
    }

    /**
     * Resets the user's password using a valid reset token.
     *
     * @param request contains the reset token and new password
     * @return 200 OK if successful, otherwise 400 BAD REQUEST
     */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        try {
            PasswordResetToken token = passwordResetTokenService.getByToken(request.token());

            if (token.getExpiryDate().isBefore(LocalDateTime.now(ZoneId.systemDefault()))) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Token scaduto.");
            }

            Users user = token.getUser();
            // Ends every session and pending link, and lifts a login lock.
            userService.resetPassword(user, request.newPassword());
            passwordResetTokenService.deleteToken(token);

            return ResponseEntity.ok("Password reimpostata con successo.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Token non valido.");
        }
    }

    /**
     * Returns the profile of the currently authenticated user.
     *
     * @return user profile if authenticated, otherwise 401 UNAUTHORIZED
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> getCurrentUser() {
        if (!isUserAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userService.getCurrentUserProfile());
    }
    
    @PostMapping("/complete-invite")
    public ResponseEntity<Void> completeInvite(@Valid @RequestBody CompleteInviteRequest req) {
        userService.completeInvite(req.getToken(), req.getPassword());
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Checks whether the current user is authenticated and not anonymous.
     *
     * @return true if authenticated, false otherwise
     */
    public boolean isUserAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;
        Object principal = authentication.getPrincipal();
        return !(trustResolver.isAnonymous(authentication)
                || !authentication.isAuthenticated()
                || (principal instanceof String && "anonymousUser".equals(principal)));
    }
}
