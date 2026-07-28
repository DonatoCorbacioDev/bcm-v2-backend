package com.donatodev.bcm_backend.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record CreateSepaPaymentRequest(
        @NotEmpty(message = "Selezionare almeno una fattura")
        List<Long> invoiceIds,

        LocalDate executionDate
) {}
