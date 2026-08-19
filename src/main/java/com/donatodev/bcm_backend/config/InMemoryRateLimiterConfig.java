package com.donatodev.bcm_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.donatodev.bcm_backend.jwt.InMemoryRateLimitBucketSource;
import com.donatodev.bcm_backend.jwt.RateLimitBucketSource;

/**
 * Test-profile counterpart to {@link RedisRateLimiterConfig}: the fast H2 unit suite
 * (`mvn test`, {@code @ActiveProfiles("test")}) has no Redis available, so it gets a plain
 * in-memory bucket source instead — same interface the filter depends on, zero external
 * dependency.
 */
@Configuration
@Profile("test")
public class InMemoryRateLimiterConfig {

    @Bean
    public RateLimitBucketSource rateLimitBucketSource() {
        return new InMemoryRateLimitBucketSource();
    }
}
