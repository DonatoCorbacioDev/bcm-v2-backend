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

    @Bean(destroyMethod = "shutdown")
    public RedisClient rateLimitRedisClient() {
        return RedisClient.create(RedisURI.Builder.redis(redisHost, redisPort).build());
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
