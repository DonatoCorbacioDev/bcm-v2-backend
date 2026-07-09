package com.donatodev.bcm_backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateInvoicePaymentDetailsRequest(
        @NotBlank(message = "IBAN is required")
        @Size(max = 34, message = "IBAN must not exceed 34 characters")
        String supplierIban,

        @Size(max = 11, message = "BIC must not exceed 11 characters")
        String supplierBic,

        LocalDate paymentDueDate
) {}
