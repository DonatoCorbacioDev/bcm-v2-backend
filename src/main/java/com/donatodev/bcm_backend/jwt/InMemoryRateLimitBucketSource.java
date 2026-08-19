package com.donatodev.bcm_backend.jwt;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * Per-instance, in-memory bucket source — the original (pre-Redis) rate limiting behavior.
 * Used for the {@code test} Spring profile so the fast H2 unit suite has no external
 * dependency on Redis; {@link RedisRateLimitBucketSource} is what actually runs in dev/prod.
 */
public class InMemoryRateLimitBucketSource implements RateLimitBucketSource {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean tryConsume(String key, int requestsPerMinute) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(requestsPerMinute));
        return bucket.tryConsume(1);
    }

    private static Bucket createBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillIntervally(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
