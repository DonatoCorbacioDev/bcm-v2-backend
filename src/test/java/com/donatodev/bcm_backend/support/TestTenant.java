package com.donatodev.bcm_backend.support;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.donatodev.bcm_backend.config.TenantContext;

/**
 * The organization MockMvc requests carry in tests, as the JWT filter takes it
 * from the token in production.
 * <p>
 * Tests that authenticate with {@code @WithMockUser} never go through the JWT
 * filter, so nothing would set {@link TenantContext} and every tenant lookup
 * would (rightly) refuse the request. A test class creates its organization,
 * calls {@link #use(Long)} in {@code @BeforeEach} and {@link #clear()} after;
 * every request then runs inside that organization, like a real token's.
 */
public final class TestTenant {

    private static final ThreadLocal<Long> ORG = new ThreadLocal<>();

    private TestTenant() {
    }

    /** Requests from this test on run as members of {@code orgId}. */
    public static void use(Long orgId) {
        ORG.set(orgId);
    }

    public static void clear() {
        ORG.remove();
    }

    /** Applies the test's organization to every MockMvc request, before the filter chain. */
    // A plain @Configuration (not @TestConfiguration) so every Spring test
    // context picks it up; it does nothing until a test calls use().
    @Configuration
    public static class MockMvcConfig {

        @Bean
        MockMvcBuilderCustomizer testTenantCustomizer() {
            return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/").with(request -> {
                Long orgId = ORG.get();
                if (orgId != null) {
                    TenantContext.set(orgId);
                }
                return request;
            }));
        }
    }
}
