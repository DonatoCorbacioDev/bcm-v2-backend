package com.donatodev.bcm_backend.dto;

public record TotpSetupResponse(String secret, String otpAuthUri) {}
