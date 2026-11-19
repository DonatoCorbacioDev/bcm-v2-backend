package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.donatodev.bcm_backend.config.RedisRateLimiterConfig;
import com.donatodev.bcm_backend.jwt.RateLimitBucketSource;
import com.donatodev.bcm_backend.jwt.RedisRateLimitBucketSource;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

/**
 * OWASP Database Security for Redis, against real Redis: the ACL production
 * runs with (the rules bcm-v2-docker/ops/redis/create-acl.sh writes; keep the
 * two in step). The backend's own user keeps the rate limiter working and is
 * refused everything dangerous; Redis's default user, all an unauthenticated
 * client gets, may only PING (the healthcheck).
 */
@Testcontainers
@Tag("integration")
@DisplayName("Integration Test: Redis ACL, the backend's user and the default one")
class RedisAclIT {

    private static final String USER = "bcm";
    // A plain fake (gitleaks): only this throwaway Redis knows it.
    private static final String PASSWORD = "test-redis-password-long-enough";

    /** The rules of create-acl.sh, with this test's password. */
    static String acl(String user, String password) throws Exception {
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8)));
        return "user default on nopass -@all +ping\n"
                + "user " + user + " on #" + hash + " ~* +@read +@write +@scripting +@connection -@dangerous\n";
    }

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCopyToContainer(Transferable.of(aclOrFail()), "/etc/redis/users.acl")
            .withCommand("redis-server", "--aclfile", "/etc/redis/users.acl");

    private static String aclOrFail() {
        try {
            return acl(USER, PASSWORD);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static RedisClient backend;
    private static RedisClient anonymous;

    @BeforeAll
    static void setUp() {
        backend = RedisClient.create(RedisRateLimiterConfig.redisUri(
                REDIS.getHost(), REDIS.getMappedPort(6379), USER, PASSWORD));
        anonymous = RedisClient.create(RedisRateLimiterConfig.redisUri(
                REDIS.getHost(), REDIS.getMappedPort(6379), "", ""));
    }

    @AfterAll
    static void tearDown() {
        backend.shutdown();
        anonymous.shutdown();
    }

    @Test
    @DisplayName("the backend's user runs the rate limiter")
    void theRateLimiterWorksAsTheBackendUser() {
        StatefulRedisConnection<String, byte[]> connection = backend.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        RateLimitBucketSource source = new RedisRateLimitBucketSource(LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
                .build());
        String key = "acl-it:" + System.nanoTime();

        assertThat(source.tryConsume(key, 2)).isTrue();
        assertThat(source.tryConsume(key, 2)).isTrue();
        assertThat(source.tryConsume(key, 2)).isFalse();
        connection.close();
    }

    @Test
    @DisplayName("the backend's user is refused what could wipe or reconfigure Redis")
    void dangerousCommandsAreRefused() {
        try (StatefulRedisConnection<String, String> connection = backend.connect()) {
            RedisCommands<String, String> redis = connection.sync();
            assertThatThrownBy(redis::flushall).isInstanceOf(RedisCommandExecutionException.class)
                    .hasMessageContaining("NOPERM");
            assertThatThrownBy(() -> redis.configGet("requirepass")).isInstanceOf(RedisCommandExecutionException.class)
                    .hasMessageContaining("NOPERM");
            assertThatThrownBy(() -> redis.keys("*")).isInstanceOf(RedisCommandExecutionException.class)
                    .hasMessageContaining("NOPERM");
        }
    }

    @Test
    @DisplayName("without credentials only PING answers: no key can be read or written")
    void theDefaultUserMayOnlyPing() {
        try (StatefulRedisConnection<String, String> connection = anonymous.connect()) {
            RedisCommands<String, String> redis = connection.sync();
            assertThat(redis.ping()).isEqualTo("PONG");
            assertThatThrownBy(() -> redis.get("any")).isInstanceOf(RedisCommandExecutionException.class)
                    .hasMessageContaining("NOPERM");
            assertThatThrownBy(() -> redis.set("any", "x")).isInstanceOf(RedisCommandExecutionException.class)
                    .hasMessageContaining("NOPERM");
        }
    }

    @Test
    @DisplayName("a wrong password is refused")
    void aWrongPasswordIsRefused() {
        RedisClient wrong = RedisClient.create(RedisURI.Builder.redis(REDIS.getHost(), REDIS.getMappedPort(6379))
                .withAuthentication(USER, "not-the-password".toCharArray()).build());
        try {
            assertThatThrownBy(wrong::connect).isInstanceOf(RedisConnectionException.class);
        } finally {
            wrong.shutdown();
        }
    }
}
