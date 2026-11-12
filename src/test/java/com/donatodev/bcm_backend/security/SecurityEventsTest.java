package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.service.AuditLogService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** OWASP API6: refused abuse is counted, logged and kept, without a flood filling the database. */
class SecurityEventsTest {

    private static final UUID ORG = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final AuditLogService audit = mock(AuditLogService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-12T10:00:00Z"));
    private SecurityEvents events;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        events = new SecurityEvents(audit, transactions, meters, clock);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anna", null, AuthorityUtils.NO_AUTHORITIES));
        TenantContext.set(ORG);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("an event is counted and stored with user, organization and address, in a transaction of its own")
    void storesInItsOwnTransaction() {
        events.record(SecurityEvents.Type.COST_LIMITED, "MlProxyController.askAgent");

        assertThat(meters.counter("bcm.security.events", "type", "COST_LIMITED").count()).isEqualTo(1.0);
        verify(audit).save("SECURITY_COST_LIMITED", "Security", null, "anna", ORG,
                "ip=203.0.113.7 | MlProxyController.askAgent");
        verify(transactions).getTransaction(org.mockito.ArgumentMatchers.argThat(
                (TransactionDefinition d) -> d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    @DisplayName("a flood is stored once per ten minutes per user and address, but every event is counted")
    void floodIsThrottled() {
        for (int i = 0; i < 50; i++) {
            events.record(SecurityEvents.Type.RATE_LIMITED, "/auth/login");
        }
        verify(audit, times(1)).save(eq("SECURITY_RATE_LIMITED"), any(), any(), any(), any(), any());
        assertThat(meters.counter("bcm.security.events", "type", "RATE_LIMITED").count()).isEqualTo(50.0);

        clock.advance(SecurityEvents.THROTTLE);
        events.record(SecurityEvents.Type.RATE_LIMITED, "/auth/login");
        verify(audit, times(2)).save(eq("SECURITY_RATE_LIMITED"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("another address or another kind of event is stored on its own")
    void throttleIsPerKey() {
        events.record(SecurityEvents.Type.RATE_LIMITED, "/auth/login");
        events.record(SecurityEvents.Type.COST_LIMITED, "x");
        MockHttpServletRequest other = new MockHttpServletRequest();
        other.setRemoteAddr("198.51.100.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(other));
        events.record(SecurityEvents.Type.RATE_LIMITED, "/auth/login");

        verify(audit, times(3)).save(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a blocked payment is always stored, however many come")
    void paymentEventsAreNotThrottled() {
        UUID counterparty = UUID.randomUUID();
        events.record(SecurityEvents.Type.SEPA_IBAN_MISMATCH_BLOCKED, "Counterparty", counterparty, "blocked");
        events.record(SecurityEvents.Type.SEPA_IBAN_MISMATCH_BLOCKED, "Counterparty", counterparty, "blocked");

        verify(audit, times(2)).save("SEPA_IBAN_MISMATCH_BLOCKED", "Counterparty", counterparty, "anna", ORG,
                "ip=203.0.113.7 | blocked");
    }

    @Test
    @DisplayName("before a login (the sign-in limiter) there is no user, and no request means no address")
    void anonymousAndOutsideARequest() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("k", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        RequestContextHolder.resetRequestAttributes();
        TenantContext.clear();

        events.record(SecurityEvents.Type.REGISTRATION_CODE_REFUSED, "");

        verify(audit).save(eq("SECURITY_REGISTRATION_CODE_REFUSED"), eq("Security"), isNull(), isNull(), isNull(),
                eq("ip=-"));
    }

    @Test
    @DisplayName("a database failure loses the record, never the refusal")
    void storeFailureIsSwallowed() {
        doThrow(new IllegalStateException("db down")).when(audit).save(any(), any(), any(), any(), any(), any());

        assertThatCode(() -> events.record(SecurityEvents.Type.DUPLICATE_PAYMENT_REFUSED, "x"))
                .doesNotThrowAnyException();
        assertThat(meters.counter("bcm.security.events", "type", "DUPLICATE_PAYMENT_REFUSED").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("the tracking of floods is bounded: past the limit it starts over")
    void trackingIsBounded() {
        for (int i = 0; i <= SecurityEvents.MAX_TRACKED_KEYS + 1; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0." + (i / 256) + "." + (i % 256));
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            events.record(SecurityEvents.Type.RATE_LIMITED, null);
        }
        // The first address was forgotten with the rest, so it is stored again.
        MockHttpServletRequest first = new MockHttpServletRequest();
        first.setRemoteAddr("10.0.0.0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(first));
        events.record(SecurityEvents.Type.RATE_LIMITED, null);
        verify(audit, times(2)).save(any(), any(), any(), any(), any(), eq("ip=10.0.0.0"));
    }

    @Test
    @DisplayName("the stored text fits the audit column")
    void detailsAreTruncated() {
        assertThat(SecurityEvents.details("1.2.3.4", "x".repeat(600))).hasSize(500).startsWith("ip=1.2.3.4 | ");
        assertThat(SecurityEvents.details(null, null)).isEqualTo("ip=-");
        verify(audit, never()).save(any(), any(), any(), any(), any(), any());
    }

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
