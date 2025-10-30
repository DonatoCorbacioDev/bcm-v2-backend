package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CompleteInviteRequest {
	
    @NotBlank
    private String token;

    @NotBlank @Size(min = 8, message = "Password must be at least 8 chars")
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
