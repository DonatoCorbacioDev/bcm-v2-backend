package com.donatodev.bcm_backend.exception;

public class OrganizationNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OrganizationNotFoundException(String message) {
        super(message);
    }
}
