/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

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
 * Wires {@link com.donatodev.bcm_backend.jwt.RateLimitingFilter} to Redis for every profile
 * except {@code test} — the fast H2 unit suite gets an in-memory bucket source instead (see
 * {@link InMemoryRateLimiterConfig}), so it has no external dependency on Redis. Redis is a
 * hard prerequisite to start the app in dev/prod, same posture as the MySQL datasource; a
 * Redis outage *after* successful startup degrades gracefully instead (see
 * {@link RedisRateLimitBucketSource}).
 */
@Configuration
@Profile("!test")
public class RedisRateLimiterConfig {

    @Value("${redis.host:localhost}")
    private String redisHost;

    @Value("${redis.port:6379}")
    private int redisPort;

    @Value("${redis.username:}")
    private String redisUsername;

    @Value("${redis.password:}")
    private String redisPassword;

    @Bean(destroyMethod = "shutdown")
    public RedisClient rateLimitRedisClient() {
        return RedisClient.create(redisUri(redisHost, redisPort, redisUsername, redisPassword));
    }

    /**
     * Where Redis is, and who the backend is to it (OWASP Database Security,
     * authentication and least privilege): in production its own ACL user, whose
     * password is a Docker secret and whose commands are only those the rate
     * limiter needs (bcm-v2-docker/ops/redis/create-acl.sh). Without a password,
     * on a laptop and in CI, Redis's default user.
     */
    public static RedisURI redisUri(String host, int port, String username, String password) {
        RedisURI.Builder uri = RedisURI.Builder.redis(host, port);
        if (password != null && !password.isBlank()) {
            uri.withAuthentication(username == null || username.isBlank() ? "default" : username,
                    password.trim().toCharArray());
        }
        return uri.build();
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(RedisClient rateLimitRedisClient) {
        return rateLimitRedisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    @Bean
    public ProxyManager<String> rateLimitProxyManager(
            StatefulRedisConnection<String, byte[]> rateLimitRedisConnection) {
        return LettuceBasedProxyManager.builderFor(rateLimitRedisConnection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2)))
                .build();
    }

    @Bean
    public RateLimitBucketSource rateLimitBucketSource(ProxyManager<String> rateLimitProxyManager) {
        return new RedisRateLimitBucketSource(rateLimitProxyManager);
    }
}
