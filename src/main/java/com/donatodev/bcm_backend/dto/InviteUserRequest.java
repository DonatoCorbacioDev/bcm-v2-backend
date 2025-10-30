package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class InviteUserRequest {
	
    @NotBlank @Size(min = 4, max = 255)
    private String username; 

    @NotBlank
    private String role;

    @NotNull
    private Long managerId;

    public String getUsername() { 
    	return username; 
    	}
    
    public void setUsername(String username) {
    	this.username = username; 
    	}
    
    public String getRole() {
    	return role; 
    	}
    
    public void setRole(String role) {
    	this.role = role; 
    	}
    
    public Long getManagerId() {
    	return managerId; 
    	}
    
    public void setManagerId(Long managerId) {
    	this.managerId = managerId; 
    	}
}
