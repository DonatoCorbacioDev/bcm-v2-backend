package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.Size;

// subscriptionTier is deliberately not settable here: it must only change via a
// paid-plan flow (billing webhook / admin console), never through the generic
// self-service org update any ADMIN can call for their own organization.
public record UpdateOrganizationRequest(
        @Size(max = 255, message = "Il nome organizzazione non può superare 255 caratteri")
        String name,

        @Size(max = 34, message = "L'IBAN non può superare 34 caratteri")
        String iban,

        @Size(max = 11, message = "Il BIC non può superare 11 caratteri")
        String bic
) {}
