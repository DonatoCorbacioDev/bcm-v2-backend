package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.donatodev.bcm_backend.validation.ValidPassword;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * OWASP Input Validation Cheat Sheet, denied by default like the roles in
 * {@code FunctionLevelAuthorizationTest}: every request body is validated,
 * and every text field a request can carry has a bound on its length or its
 * form. A new endpoint or field without one fails here unless it is listed
 * below with the reason it needs none.
 */
@SpringBootTest
@ActiveProfiles("test")
class InputValidationGuardTest {

    /** Bodies read without bean validation, and why. */
    private static final Set<String> UNVALIDATED_BODIES = Set.of(
            // Stripe's raw payload: its signature is verified before a byte of it is read.
            "BillingController.webhook");

    /** Text fields with no bound of their own, and why. */
    private static final Map<String, String> UNBOUNDED_TEXT = Map.of(
            "ContractDTO.managerName", "read-only, filled in answers and never read from a request",
            "BudgetDTO.areaName", "read-only, filled in answers and never read from a request",
            "FinancialValueDTO.typeName", "read-only, filled in answers and never read from a request",
            "FinancialValueDTO.areaName", "read-only, filled in answers and never read from a request",
            "FinancialValueDTO.customerName", "read-only, filled in answers and never read from a request");

    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    @Test
    @DisplayName("every request body is validated, or listed with the reason it is not")
    void everyBodyIsValidated() {
        Set<String> unvalidated = new TreeSet<>();
        for (HandlerMethod handler : ourHandlers()) {
            for (MethodParameter parameter : handler.getMethodParameters()) {
                if (parameter.hasParameterAnnotation(RequestBody.class)
                        && !parameter.hasParameterAnnotation(Valid.class)
                        && !parameter.hasParameterAnnotation(Validated.class)) {
                    unvalidated.add(name(handler));
                }
            }
        }
        assertThat(unvalidated).as("request bodies without @Valid").isEqualTo(new TreeSet<>(UNVALIDATED_BODIES));
    }

    @Test
    @DisplayName("every text field of a request body has a bound, or is listed with the reason it has none")
    void everyTextFieldIsBounded() {
        Set<String> unbounded = new TreeSet<>();
        for (HandlerMethod handler : ourHandlers()) {
            for (MethodParameter parameter : handler.getMethodParameters()) {
                Class<?> body = parameter.getParameterType();
                if (!parameter.hasParameterAnnotation(RequestBody.class) || body == String.class) {
                    continue;
                }
                for (Field field : body.getDeclaredFields()) {
                    if (field.getType() == String.class && !Modifier.isStatic(field.getModifiers())
                            && !bounded(field)) {
                        unbounded.add(body.getSimpleName() + "." + field.getName());
                    }
                }
            }
        }
        assertThat(unbounded).as("text fields without @Size, @Pattern, @Email or @ValidPassword")
                .isEqualTo(new TreeSet<>(UNBOUNDED_TEXT.keySet()));
    }

    private static boolean bounded(Field field) {
        return field.isAnnotationPresent(Size.class) || field.isAnnotationPresent(Pattern.class)
                || field.isAnnotationPresent(Email.class) || field.isAnnotationPresent(ValidPassword.class);
    }

    private Iterable<HandlerMethod> ourHandlers() {
        return mappings.getHandlerMethods().values().stream()
                .filter(handler -> handler.getBeanType().getPackageName().startsWith("com.donatodev"))
                .toList();
    }

    private static String name(HandlerMethod handler) {
        return handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName();
    }
}
