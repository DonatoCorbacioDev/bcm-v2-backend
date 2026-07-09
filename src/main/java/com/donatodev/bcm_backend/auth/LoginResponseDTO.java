package com.donatodev.bcm_backend.auth;

/**
 * Response for POST /auth/login and POST /auth/2fa/verify.
 * <p>
 * When {@code mfaRequired} is true, {@code token} is null and the client
 * must call /auth/2fa/verify with {@code mfaToken} plus a TOTP or recovery
 * code to obtain the real access token.
 */
public record LoginResponseDTO(String token, boolean mfaRequired, String mfaToken) {}
