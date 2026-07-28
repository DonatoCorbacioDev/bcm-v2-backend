package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CompleteInviteRequest {
	
    @NotBlank
    private String token;

    @NotBlank @Size(min = 8, message = "La password deve contenere almeno 8 caratteri")
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
