package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Size(min = 4, max = 30, message = "Lo username deve avere tra 4 e 30 caratteri")
    private String username;

    private String role;

    private Long managerId;

    private String password;
}
