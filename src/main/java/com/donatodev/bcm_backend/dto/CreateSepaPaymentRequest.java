package com.donatodev.bcm_backend.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record CreateSepaPaymentRequest(
        @NotEmpty(message = "At least one invoice must be selected")
        List<Long> invoiceIds,

        LocalDate executionDate
) {}
