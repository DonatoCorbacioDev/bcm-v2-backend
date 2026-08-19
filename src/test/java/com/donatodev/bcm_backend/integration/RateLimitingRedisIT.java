package com.donatodev.bcm_backend.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.donatodev.bcm_backend.jwt.RateLimitBucketSource;
import com.donatodev.bcm_backend.jwt.RedisRateLimitBucketSource;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

/**
 * Proves distributed rate limiting actually coordinates across backend instances against a
 * real Redis — something a mocked ProxyManager (see RedisRateLimitBucketSourceTest) cannot
 * demonstrate. Two independent {@link RedisRateLimitBucketSource} instances, each with its
 * own Lettuce connection, stand in for two backend replicas sharing one Redis. No Spring
 * context needed: this only exercises the Redis-backed class directly, not the profile
 * wiring in RedisRateLimiterConfig (which is what the "test" Spring profile deliberately
 * bypasses in favor of InMemoryRateLimiterConfig — see that class's javadoc).
 */
@Testcontainers
@Tag("integration")
@DisplayName("Integration Test: distributed rate limiting against real Redis")
class RateLimitingRedisIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static RedisClient clientA;
    private static RedisClient clientB;

    @BeforeAll
    static void setUp() {
        RedisURI uri = RedisURI.Builder.redis(REDIS.getHost(), REDIS.getMappedPort(6379)).build();
        clientA = RedisClient.create(uri);
        clientB = RedisClient.create(uri);
    }

    @AfterAll
    static void tearDown() {
        clientA.shutdown();
        clientB.shutdown();
    }

    private static RateLimitBucketSource newBucketSource(RedisClient client) {
        StatefulRedisConnection<String, byte[]> connection = client.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        ProxyManager<String> proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
                .build();
        return new RedisRateLimitBucketSource(proxyManager);
    }

    @Test
    @DisplayName("A single source enforces the limit against real Redis")
    void enforcesLimitAgainstRealRedis() {
        RateLimitBucketSource source = newBucketSource(clientA);
        String key = "it-test:" + System.nanoTime();

        assertTrue(source.tryConsume(key, 2));
        assertTrue(source.tryConsume(key, 2));
        assertFalse(source.tryConsume(key, 2));
    }

    @Test
    @DisplayName("Two independent instances sharing Redis see the same bucket: exhausting "
            + "the limit via instance A blocks instance B too — the actual 'distributed' guarantee")
    void coordinatesAcrossInstances() {
        RateLimitBucketSource instanceA = newBucketSource(clientA);
        RateLimitBucketSource instanceB = newBucketSource(clientB);
        String key = "it-test:" + System.nanoTime();

        assertTrue(instanceA.tryConsume(key, 2));
        assertTrue(instanceB.tryConsume(key, 2));

        // Bucket is exhausted regardless of which "instance" asks next.
        assertFalse(instanceA.tryConsume(key, 2));
        assertFalse(instanceB.tryConsume(key, 2));
    }
}
