/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-service registration. The three declarations are primitive booleans on
 * purpose: a missing field deserializes to {@code false} and fails
 * {@link AssertTrue}, whereas a {@code Boolean} null would pass it.
 *
 * @param termsVersion            the /termini version the user saw; must equal
 *                                {@code LegalTerms.CURRENT_VERSION}
 * @param termsAccepted           Terms of Service accepted, Privacy notice read
 * @param professionalUse         acting as a business/professional, not a consumer
 *                                (the service is B2B-only)
 * @param onerousClausesApproved  separate specific approval of the onerous
 *                                clauses, required by arts. 1341-1342 c.c.
 */
public record OrganizationRegistrationRequest(

        @NotBlank(message = "Nome organizzazione obbligatorio")
        @Size(max = 255, message = "Il nome organizzazione non può superare 255 caratteri")
        String organizationName,

        @NotBlank(message = "Username amministratore obbligatorio")
        @Size(min = 3, max = 50, message = "Lo username deve avere tra 3 e 50 caratteri")
        String adminUsername,

        @NotBlank(message = "Password amministratore obbligatoria")
        @Size(min = 6, message = "La password deve contenere almeno 6 caratteri")
        String adminPassword,

        @NotBlank(message = "Email amministratore obbligatoria")
        @Email(message = "L'email amministratore non è valida")
        String adminEmail,

        @NotBlank(message = "Nome amministratore obbligatorio")
        String adminFirstName,

        @NotBlank(message = "Cognome amministratore obbligatorio")
        String adminLastName,

        @NotBlank(message = "Versione dei Termini di servizio mancante")
        String termsVersion,

        @AssertTrue(message = "È necessario accettare i Termini di servizio")
        boolean termsAccepted,

        @AssertTrue(message = "Il servizio è riservato a imprese e professionisti")
        boolean professionalUse,

        @AssertTrue(message = "È necessario approvare specificamente le clausole indicate (artt. 1341-1342 c.c.)")
        boolean onerousClausesApproved
) {}
