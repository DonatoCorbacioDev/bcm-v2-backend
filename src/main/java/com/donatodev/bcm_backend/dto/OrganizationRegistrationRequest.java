package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationRegistrationRequest(

        @NotBlank(message = "Organization name is required")
        @Size(max = 255, message = "Organization name must not exceed 255 characters")
        String organizationName,

        @NotBlank(message = "Admin username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String adminUsername,

        @NotBlank(message = "Admin password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String adminPassword,

        @NotBlank(message = "Admin email is required")
        @Email(message = "Admin email must be a valid email address")
        String adminEmail,

        @NotBlank(message = "Admin first name is required")
        String adminFirstName,

        @NotBlank(message = "Admin last name is required")
        String adminLastName
) {}
