/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.donatodev.bcm_backend.validation.ValidPassword;
import com.donatodev.bcm_backend.validation.InputLimits;

public class CompleteInviteRequest {
	
    @NotBlank
    @Size(max = InputLimits.TOKEN, message = "Token non valido")
    private String token;

    @ValidPassword
    private String password;

    public String getToken() {
    	return token; 
    	}
    
    public void setToken(String token) {
    	this.token = token; 
    	}
    public String getPassword() {
    	return password; 
    	}
    
    public void setPassword(String password) {
    	this.password = password; 
    	}
}
