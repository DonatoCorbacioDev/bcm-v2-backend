package com.donatodev.bcm_backend.aspect;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.service.AuditLogService;

import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@Slf4j
public class AuditAspect {

    private final AuditLogService auditLogService;

    public AuditAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Around("execution(public * com.donatodev.bcm_backend.service.*Service.create*(..)) || " +
            "execution(public * com.donatodev.bcm_backend.service.*Service.update*(..)) || " +
            "execution(public * com.donatodev.bcm_backend.service.*Service.delete*(..))")
    public Object auditServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            String methodName = joinPoint.getSignature().getName();
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String action = inferAction(methodName);
            String entityType = inferEntityType(className);
            Long entityId = extractEntityId(result, joinPoint.getArgs());
            String username = getCurrentUsername();
            Long orgId = TenantContext.get();
            String details = className + "." + methodName;

            auditLogService.save(action, entityType, entityId, username, orgId, details);
        } catch (Exception e) {
            log.warn("Failed to persist audit log entry: {}", e.getMessage());
        }

        return result;
    }

    private String inferAction(String methodName) {
        if (methodName.startsWith("create")) return "CREATE";
        if (methodName.startsWith("update")) return "UPDATE";
        if (methodName.startsWith("delete")) return "DELETE";
        return methodName.toUpperCase();
    }

    private String inferEntityType(String className) {
        return className.replace("Service", "").replace("Impl", "");
    }

    private Long extractEntityId(Object result, Object[] args) {
        if (result != null) {
            try {
                Method idMethod = result.getClass().getMethod("id");
                Object value = idMethod.invoke(result);
                if (value instanceof Number n) return n.longValue();
            } catch (Exception e) {
                // DTO does not expose id() — try getId() below
            }
            try {
                Method getIdMethod = result.getClass().getMethod("getId");
                Object value = getIdMethod.invoke(result);
                if (value instanceof Number n) return n.longValue();
            } catch (Exception e) {
                // entity does not expose getId() — id will be null in audit log
            }
        }
        // Fallback: first Long argument (typical for delete/update by id)
        for (Object arg : args) {
            if (arg instanceof Long id) return id;
        }
        return null;
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }
}
