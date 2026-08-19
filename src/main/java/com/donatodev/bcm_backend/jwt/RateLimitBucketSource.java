package com.donatodev.bcm_backend.jwt;

/**
 * Where {@link RateLimitingFilter} gets its token buckets from. Two implementations:
 * {@link RedisRateLimitBucketSource} (dev/prod, shared across backend instances) and
 * {@link InMemoryRateLimitBucketSource} (test profile, zero external dependencies).
 */
public interface RateLimitBucketSource {

    /**
     * @return true if a token was consumed (request allowed), false if the caller is over
     *         the limit for {@code key} and should be rejected.
     */
    boolean tryConsume(String key, int requestsPerMinute);
}
