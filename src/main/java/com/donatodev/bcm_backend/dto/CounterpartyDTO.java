package com.donatodev.bcm_backend.dto;

import com.donatodev.bcm_backend.entity.CounterpartyType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
 */
public record CounterpartyDTO(
        Long id,
        @NotBlank(message = "Nome obbligatorio") String name,
        @NotNull(message = "Tipo obbligatorio") CounterpartyType type,
        String vatNumber,
        String taxCode,
        String address,
        String contactName,
        @Email(message = "Email non valida") String contactEmail,
        String contactPhone,
        String notes
) {}
