package com.donatodev.bcm_backend.jwt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;

class RedisRateLimitBucketSourceTest {

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Delegates to the proxy manager and returns its tryConsume result")
    void shouldDelegateToProxyManager() {
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        RemoteBucketBuilder<String> builder = mock(RemoteBucketBuilder.class);
        BucketProxy bucket = mock(BucketProxy.class);

        when(proxyManager.builder()).thenReturn(builder);
        when(builder.build(anyString(), org.mockito.ArgumentMatchers.<Supplier<BucketConfiguration>>any()))
                .thenReturn(bucket);
        when(bucket.tryConsume(1L)).thenReturn(true);

        RedisRateLimitBucketSource source = new RedisRateLimitBucketSource(proxyManager);

        assertTrue(source.tryConsume("rate-limit:1.2.3.4", 5));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Rejects when the underlying bucket is exhausted")
    void shouldRejectWhenBucketExhausted() {
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        RemoteBucketBuilder<String> builder = mock(RemoteBucketBuilder.class);
        BucketProxy bucket = mock(BucketProxy.class);

        when(proxyManager.builder()).thenReturn(builder);
        when(builder.build(anyString(), org.mockito.ArgumentMatchers.<Supplier<BucketConfiguration>>any()))
                .thenReturn(bucket);
        when(bucket.tryConsume(1L)).thenReturn(false);

        RedisRateLimitBucketSource source = new RedisRateLimitBucketSource(proxyManager);

        assertFalse(source.tryConsume("rate-limit:1.2.3.4", 5));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Fails open (allows the request) when Redis is unreachable")
    void shouldFailOpenWhenProxyManagerThrows() {
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        when(proxyManager.builder()).thenThrow(new RuntimeException("Redis connection refused"));

        RedisRateLimitBucketSource source = new RedisRateLimitBucketSource(proxyManager);

        assertTrue(source.tryConsume("rate-limit:1.2.3.4", 5),
                "a Redis failure must not block requests");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Fails open when the bucket itself throws (e.g. connection dropped mid-call)")
    void shouldFailOpenWhenBucketThrows() {
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        RemoteBucketBuilder<String> builder = mock(RemoteBucketBuilder.class);
        BucketProxy bucket = mock(BucketProxy.class);

        when(proxyManager.builder()).thenReturn(builder);
        when(builder.build(anyString(), org.mockito.ArgumentMatchers.<Supplier<BucketConfiguration>>any()))
                .thenReturn(bucket);
        when(bucket.tryConsume(1L)).thenThrow(new RuntimeException("connection reset"));

        RedisRateLimitBucketSource source = new RedisRateLimitBucketSource(proxyManager);

        assertTrue(source.tryConsume("rate-limit:1.2.3.4", 5));
    }
}
