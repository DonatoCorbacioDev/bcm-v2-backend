package com.donatodev.bcm_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BcmBackendApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: verifies that the Spring application context loads with the 'test' profile.
    }
}