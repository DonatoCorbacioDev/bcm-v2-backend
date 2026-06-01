package com.donatodev.bcm_backend.aspect;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.service.AuditLogService;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class AuditAspectTest {

    @Mock private AuditLogService auditLogService;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private Signature signature;

    @InjectMocks
    private AuditAspect auditAspect;

    @BeforeEach
    void setup() throws Throwable {
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // Helper records/classes for extractEntityId testing
    record DtoWithId(Long id) {}

    static class EntityWithGetId {
        public Long getId() { return 99L; }
    }

    static class EntityWithStringId {
        public String id() { return "not-a-number"; }
    }

    // Both id() and getId() exist but return non-Number values
    static class EntityWithBothStringIds {
        public String id() { return "not-a-number"; }
        public String getId() { return "also-not-a-number"; }
    }

    @Nested
    @DisplayName("Unit Test: AuditAspect")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class VerifyAuditAspect {

        @Test
        @Order(1)
        @DisplayName("Extracts id via id() record accessor")
        void shouldExtractIdFromIdMethod() throws Throwable {
            when(joinPoint.proceed()).thenReturn(new DtoWithId(42L));
            when(signature.getName()).thenReturn("createContract");

            auditAspect.auditServiceMethod(joinPoint);

            verify(auditLogService).save(eq("CREATE"), any(), eq(42L), any(), any(), any());
        }

        @Test
        @Order(2)
        @DisplayName("Extracts id via getId() when id() is absent — covers first catch block")
        void shouldExtractIdFromGetIdMethod() throws Throwable {
            when(joinPoint.proceed()).thenReturn(new EntityWithGetId());
            when(signature.getName()).thenReturn("updateItem");

            auditAspect.auditServiceMethod(joinPoint);

            verify(auditLogService).save(eq("UPDATE"), any(), eq(99L), any(), any(), any());
        }

        @Test
        @Order(3)
        @DisplayName("Falls back to Long arg when id() returns non-Number — covers both catch blocks")
        void shouldFallbackToArgWhenIdReturnsNonNumber() throws Throwable {
            when(joinPoint.proceed()).thenReturn(new EntityWithStringId());
            when(joinPoint.getArgs()).thenReturn(new Object[]{55L, "other"});
            when(signature.getName()).thenReturn("deleteStuff");

            auditAspect.auditServiceMethod(joinPoint);

            verify(auditLogService).save(eq("DELETE"), any(), eq(55L), any(), any(), any());
        }

        @Test
        @Order(4)
        @DisplayName("getId() exists but returns non-Number — covers second try false branch")
        void shouldHandleGetIdReturningNonNumber() throws Throwable {
            when(joinPoint.proceed()).thenReturn(new EntityWithBothStringIds());
            when(joinPoint.getArgs()).thenReturn(new Object[]{77L});
            when(signature.getName()).thenReturn("updateItem");

            auditAspect.auditServiceMethod(joinPoint);

            // Falls through both id() and getId() non-Number checks → Long arg fallback
            verify(auditLogService).save(eq("UPDATE"), any(), eq(77L), any(), any(), any());
        }

        @Test
        @Order(6)
        @DisplayName("Uses unknown method name as UPPERCASE action — covers inferAction fallback")
        void shouldUseUppercaseForUnknownMethodName() throws Throwable {
            when(joinPoint.proceed()).thenReturn(null);
            when(signature.getName()).thenReturn("fetchAll");

            auditAspect.auditServiceMethod(joinPoint);

            verify(auditLogService).save(eq("FETCHALL"), any(), isNull(), any(), any(), any());
        }

        @Test
        @Order(7)
        @DisplayName("Returns null entityId when result null and no Long in args")
        void shouldReturnNullEntityIdWithNoLongArg() throws Throwable {
            when(joinPoint.proceed()).thenReturn(null);
            when(joinPoint.getArgs()).thenReturn(new Object[]{"string", 42}); // Integer not Long
            when(signature.getName()).thenReturn("deleteIt");

            auditAspect.auditServiceMethod(joinPoint);

            verify(auditLogService).save(any(), any(), isNull(), any(), any(), any());
        }

        @Test
        @Order(6)
        @DisplayName("Returns null username when SecurityContext has no authentication")
        void shouldReturnNullUsernameWhenNoAuth() throws Throwable {
            SecurityContextHolder.clearContext();
            when(joinPoint.proceed()).thenReturn(null);
            when(signature.getName()).thenReturn("deleteIt");

            auditAspect.auditServiceMethod(joinPoint);

            verify(auditLogService).save(any(), any(), any(), isNull(), any(), any());
        }

        @Test
        @Order(7)
        @DisplayName("Returns null username when authentication is not authenticated")
        void shouldReturnNullUsernameWhenNotAuthenticated() throws Throwable {
            UsernamePasswordAuthenticationToken unauth =
                    new UsernamePasswordAuthenticationToken("user", "pass");
            SecurityContextHolder.getContext().setAuthentication(unauth);

            when(joinPoint.proceed()).thenReturn(null);
            when(signature.getName()).thenReturn("deleteIt");

            auditAspect.auditServiceMethod(joinPoint);

            verify(auditLogService).save(any(), any(), any(), isNull(), any(), any());
        }

        @Test
        @Order(8)
        @DisplayName("Returns null username when principal is 'anonymousUser'")
        void shouldReturnNullUsernameForAnonymousPrincipal() throws Throwable {
            UsernamePasswordAuthenticationToken anonAuth =
                    new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(anonAuth);

            when(joinPoint.proceed()).thenReturn(null);
            when(signature.getName()).thenReturn("deleteIt");

            auditAspect.auditServiceMethod(joinPoint);

            verify(auditLogService).save(any(), any(), any(), isNull(), any(), any());
        }

        @Test
        @Order(9)
        @DisplayName("Returns authenticated username when user is logged in")
        void shouldReturnUsernameWhenAuthenticated() throws Throwable {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken("donato", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);

            when(joinPoint.proceed()).thenReturn(null);
            when(signature.getName()).thenReturn("createItem");

            auditAspect.auditServiceMethod(joinPoint);

            verify(auditLogService).save(any(), any(), any(), eq("donato"), any(), any());
        }

        @Test
        @Order(10)
        @DisplayName("Does not propagate exception when auditLogService throws")
        void shouldNotPropagateAuditException() throws Throwable {
            when(joinPoint.proceed()).thenReturn(null);
            when(signature.getName()).thenReturn("deleteItem");
            org.mockito.Mockito.doThrow(new RuntimeException("DB error"))
                    .when(auditLogService).save(any(), any(), any(), any(), any(), any());

            assertDoesNotThrow(() -> auditAspect.auditServiceMethod(joinPoint));
        }
    }
}
