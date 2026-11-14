/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.jwt.JwtAuthenticationFilter;
import com.donatodev.bcm_backend.service.AuditLogService;
import com.donatodev.bcm_backend.util.AuthenticatedUserUtils;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Makes refused abuse visible (OWASP API6): each refusal counts in the
 * {@code bcm.security.events} metric, is logged, and is kept in the audit log
 * for the weekly report ({@link SecurityReportService}).
 * <p>
 * The audit row is written in a transaction of its own: most refusals end in
 * an exception that rolls back the request's transaction, and the record of
 * the refusal must survive it. A flood is stored as one row per type, user
 * and address every {@link #THROTTLE}, so it can't fill the database; the
 * metric and the log still count every single event.
 */
@Component
public class SecurityEvents {

    private static final Logger logger = LoggerFactory.getLogger(SecurityEvents.class);
    private static final Pattern LINE_BREAKS = Pattern.compile("[\r\n]");

    static final Duration THROTTLE = Duration.ofMinutes(10);
    static final int MAX_TRACKED_KEYS = 10_000;
    static final String ENTITY_TYPE = "Security";

    /** What was refused. Volume events are stored at most once per {@link #THROTTLE}; rare ones always. */
    public enum Type {
        /** Too many sign-in, reset or registration requests from one address. */
        RATE_LIMITED("SECURITY_RATE_LIMITED", true),
        /** A user went over the per-minute budget of a costly endpoint. */
        COST_LIMITED("SECURITY_COST_LIMITED", true),
        /** The public demo account tried an action disabled for it. */
        DEMO_BLOCKED("SECURITY_DEMO_BLOCKED", true),
        /** An address asked for more registration codes than allowed. */
        REGISTRATION_CODE_REFUSED("SECURITY_REGISTRATION_CODE_REFUSED", true),
        /** A second payment of invoices already in a SEPA batch. */
        DUPLICATE_PAYMENT_REFUSED("SECURITY_DUPLICATE_PAYMENT_REFUSED", false),
        /** A SEPA payment to an IBAN other than the supplier's verified one. */
        SEPA_IBAN_MISMATCH_BLOCKED("SEPA_IBAN_MISMATCH_BLOCKED", false),
        /**
         * A function or a record refused to the caller's role or account (OWASP
         * Authorization Cheat Sheet): repeated refusals are how a stolen account
         * or a curious colleague shows. Records of another organization answer
         * 404 and are not counted here, being indistinguishable from a stale link.
         */
        ACCESS_DENIED("SECURITY_ACCESS_DENIED", true);

        private final String action;
        private final boolean throttled;

        Type(String action, boolean throttled) {
            this.action = action;
            this.throttled = throttled;
        }

        public String action() {
            return action;
        }
    }

    private final AuditLogService auditLogService;
    private final TransactionTemplate ownTransaction;
    private final MeterRegistry meters;
    private final Clock clock;
    private final Map<String, Instant> lastStored = new ConcurrentHashMap<>();

    @Autowired
    public SecurityEvents(AuditLogService auditLogService, PlatformTransactionManager transactions,
            MeterRegistry meters) {
        this(auditLogService, transactions, meters, Clock.systemUTC());
    }

    SecurityEvents(AuditLogService auditLogService, PlatformTransactionManager transactions,
            MeterRegistry meters, Clock clock) {
        this.auditLogService = auditLogService;
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.meters = meters;
        this.clock = clock;
    }

    /** Records a refusal by the current user (if any) from the current request's address. */
    public void record(Type type, String detail) {
        record(type, ENTITY_TYPE, null, detail);
    }

    /** As {@link #record(Type, String)}, about a given entity (the counterparty of a blocked payment). */
    public void record(Type type, String entityType, UUID entityId, String detail) {
        String username = currentUsername();
        String ip = currentIp();
        meters.counter("bcm.security.events", "type", type.name()).increment();
        if (logger.isWarnEnabled()) {
            logger.warn("Security event {}: user={} ip={} {}", type, oneLine(username), oneLine(ip), oneLine(detail));
        }
        if (type.throttled && !firstInWindow(type, username, ip)) {
            return;
        }
        UUID orgId = TenantContext.get();
        String details = details(ip, detail);
        try {
            ownTransaction.executeWithoutResult(status -> auditLogService.save(
                    type.action(), entityType, entityId, username, orgId, details));
        } catch (RuntimeException e) {
            // The refusal itself must still happen: losing its record is the lesser harm.
            logger.warn("Could not store security event {}: {}", type, oneLine(e.getMessage()));
        }
    }

    /** Records an authorization refusal of the given request: "GET /api/v1/users". */
    public void accessDenied(HttpServletRequest request) {
        record(Type.ACCESS_DENIED, request.getMethod() + " " + JwtAuthenticationFilter.loggablePath(request.getRequestURI()));
    }

    /** The refused endpoint, by name: "UserController.changeOwnPassword". */
    public static String operation(HandlerMethod method) {
        return method.getBeanType().getSimpleName() + "." + method.getMethod().getName();
    }

    /** The stored text: the address first, so the weekly report can group by it. */
    static String details(String ip, String detail) {
        String text = "ip=" + (ip == null ? "-" : ip) + (detail == null || detail.isBlank() ? "" : " | " + detail);
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private boolean firstInWindow(Type type, String username, String ip) {
        Instant now = clock.instant();
        if (lastStored.size() > MAX_TRACKED_KEYS) {
            lastStored.clear();
        }
        String key = type.name() + '|' + username + '|' + ip;
        boolean[] first = {false};
        lastStored.compute(key, (k, previous) -> {
            if (previous == null || !previous.plus(THROTTLE).isAfter(now)) {
                first[0] = true;
                return now;
            }
            return previous;
        });
        return first[0];
    }

    private static String currentUsername() {
        String username = AuthenticatedUserUtils.getUsernameOrNull();
        return "anonymousUser".equals(username) ? null : username;
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }

    private static String oneLine(String value) {
        return value == null ? "-" : LINE_BREAKS.matcher(value).replaceAll("_");
    }
}
