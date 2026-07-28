package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
        String adminLastName
) {}
