package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Size(min = 4, max = 30, message = "Username must be between 4 and 30 characters")
    private String username;

    private String role;

    private Long managerId;

    private String password;
}
