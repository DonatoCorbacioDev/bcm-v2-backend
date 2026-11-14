/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import com.donatodev.bcm_backend.entity.CounterpartyType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.donatodev.bcm_backend.validation.InputLimits;

/**
 * Data Transfer Object for Counterparty.
 * <p>
 * Used to transfer data between client and server in REST API operations.
 *
 * @param id           the ID of the counterparty
 * @param name         the counterparty's company name
 * @param type         whether it is a customer, a supplier, or both
 * @param vatNumber    VAT number (Partita IVA), optional
 * @param taxCode      tax code (Codice Fiscale), optional
 * @param address      registered address, optional
 * @param contactName  name of the primary contact person, optional
 * @param contactEmail email of the primary contact person, optional
 * @param contactPhone phone of the primary contact person, optional
 * @param notes        free-text notes, optional
 * @param verifiedIban IBAN this counterparty is trusted to be paid at for SEPA
 *                     payments, read-only here -- only
 *                     {@code PATCH /counterparties/{id}/verified-iban} can set
 *                     it, never the general create/update endpoints. Masked
 *                     ({@code "IT...3456"}) on read, never the full value.
 * @param verifiedBic  BIC paired with {@code verifiedIban}, same read-only
 *                     rule and masking
 */
public record CounterpartyDTO(
        UUID id,
        @NotBlank(message = "Nome obbligatorio")
        @Size(max = 255, message = "Il nome non può superare 255 caratteri") String name,
        @NotNull(message = "Tipo obbligatorio") CounterpartyType type,
        @Size(max = 50, message = "La partita IVA non può superare 50 caratteri") String vatNumber,
        @Size(max = 50, message = "Il codice fiscale non può superare 50 caratteri") String taxCode,
        @Size(max = 500, message = "L'indirizzo non può superare 500 caratteri") String address,
        @Size(max = 255, message = "Il referente non può superare 255 caratteri") String contactName,
        @Email(message = "Email non valida")
        @Size(max = 255, message = "L'email non può superare 255 caratteri") String contactEmail,
        @Size(max = 50, message = "Il telefono non può superare 50 caratteri") String contactPhone,
        @Size(max = InputLimits.LONG_TEXT, message = "Le note sono troppo lunghe") String notes,
        @Size(max = 34, message = "L'IBAN non può superare 34 caratteri") String verifiedIban,
        @Size(max = 11, message = "Il BIC non può superare 11 caratteri") String verifiedBic,
        // The rest of the address (address holds street and number).
        @Size(max = 10, message = "Il CAP non può superare i 10 caratteri") String postalCode,
        @Size(max = 100, message = "Il comune non può superare i 100 caratteri") String city,
        @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "La provincia è una sigla di 2 lettere") String province,
        @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "La nazione è un codice di 2 lettere") String country
) {

    /** Without the address parts: call sites predating them. */
    public CounterpartyDTO(UUID id, String name, CounterpartyType type, String vatNumber, String taxCode,
            String address, String contactName, String contactEmail, String contactPhone, String notes,
            String verifiedIban, String verifiedBic) {
        this(id, name, type, vatNumber, taxCode, address, contactName, contactEmail, contactPhone, notes,
                verifiedIban, verifiedBic, null, null, null, null);
    }
}
