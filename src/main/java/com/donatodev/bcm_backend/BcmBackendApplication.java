package com.donatodev.bcm_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Business Contracts Manager backend application.
 * <p>
 * This class boots the Spring Boot application using {@link SpringApplication}.
 */
@SpringBootApplication
@EnableScheduling
public class BcmBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BcmBackendApplication.class, args);
    }

}
