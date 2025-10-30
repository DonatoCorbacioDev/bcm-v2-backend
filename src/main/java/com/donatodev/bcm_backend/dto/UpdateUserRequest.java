package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.Size;

public class UpdateUserRequest {

    @Size(min = 4, max = 30, message = "Username must be between 4 and 30 characters")
    private String username;

    private String role;

    private Long managerId;

    private String password;

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

    public String getPassword() {
    	return password; 
    	}
    
    public void setPassword(String password) {
    	this.password = password; 
    	}
}
