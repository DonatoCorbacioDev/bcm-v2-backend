package com.donatodev.bcm_backend.exception;

public class NotificationNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotificationNotFoundException(String message) {
        super(message);
    }
}
