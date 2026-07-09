package com.donatodev.bcm_backend.dto;

import java.util.List;

public record TotpConfirmResponse(List<String> recoveryCodes) {}
