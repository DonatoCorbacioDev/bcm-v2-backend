package com.donatodev.bcm_backend.jwt;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;

/**
 * Redis-backed bucket source: buckets live server-side in Redis instead of a per-instance
 * map, so the limit is shared across backend instances instead of resetting (and effectively
 * multiplying) per instance. See docs/SECURITY.md — this closes the last unchecked item on
 * the production checklist for the built-in rate limiter.
 */
public class RedisRateLimitBucketSource implements RateLimitBucketSource {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitBucketSource.class);
    private static final String CRLF_REGEX = "[\r\n]";

    private final ProxyManager<String> proxyManager;

    public RedisRateLimitBucketSource(ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }

    /**
     * Fails open on any Redis error: a rate limiter that turns a Redis blip into a full
     * login outage would be a worse failure mode than the rate limit briefly not being
     * enforced. Same "degrade the feature, not the request" posture as the ML proxy and the
     * Ollama embedding calls elsewhere in this codebase (see ADR-0004, ADR-0005). This only
     * covers Redis going away *after* a successful startup — like the MySQL datasource,
     * Redis is a hard prerequisite for the app to start in the first place.
     */
    @Override
    public boolean tryConsume(String key, int requestsPerMinute) {
        try {
            Bucket bucket = proxyManager.builder().build(key, () -> bucketConfiguration(requestsPerMinute));
            return bucket.tryConsume(1);
        } catch (Exception ex) {
            log.warn("Rate limiter backend (Redis) unavailable, allowing request through: {}", safeMessage(ex));
            return true;
        }
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null ? null : message.replaceAll(CRLF_REGEX, "_");
    }

    private static BucketConfiguration bucketConfiguration(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillIntervally(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return BucketConfiguration.builder().addLimit(limit).build();
    }
}
