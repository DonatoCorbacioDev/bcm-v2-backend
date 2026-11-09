package com.donatodev.bcm_backend.security;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.donatodev.bcm_backend.service.ElectronicInvoiceService;
import com.donatodev.bcm_backend.service.FinancialValueService;
import com.donatodev.bcm_backend.service.SepaPaymentService;
import com.donatodev.bcm_backend.service.TwoFactorAuthService;
import com.donatodev.bcm_backend.service.UserService;

/**
 * The public demo login may not lock other visitors out or delete what the
 * demo shows; every other account, and every other endpoint, is unaffected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "bcm.demo.locked-username=demo")
class PublicDemoGuardTest {

    private static final String DISABLED = "Accesso negato: nella demo pubblica questa azione è disattivata";
    private static final String PASSWORD_BODY =
            "{\"currentPassword\":\"OldPass#2026\",\"newPassword\":\"NewPass#2026\"}";
    private static final String VALUE_BODY =
            "{\"month\":1,\"year\":2026,\"financialAmount\":100,\"financialTypeId\":\"00000000-0000-0000-0000-000000000001\",\"businessAreaId\":\"00000000-0000-0000-0000-000000000001\",\"contractId\":\"00000000-0000-0000-0000-000000000001\"}";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TwoFactorAuthService twoFactorAuthService;
    @MockitoBean private UserService userService;
    @MockitoBean private ElectronicInvoiceService electronicInvoiceService;
    @MockitoBean private FinancialValueService financialValueService;
    @MockitoBean private SepaPaymentService sepaPaymentService;

    @Nested
    @DisplayName("as the public demo account")
    @WithMockUser(username = "demo", roles = "MANAGER")
    class AsDemo {

        @Test
        @DisplayName("can't change its password")
        void passwordRefused() throws Exception {
            mockMvc.perform(post("/users/me/password").contentType(MediaType.APPLICATION_JSON).content(PASSWORD_BODY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(DISABLED));
            verify(userService, never()).changeOwnPassword(anyString(), anyString());
        }

        @Test
        @DisplayName("can't start or confirm two-factor login")
        void twoFactorRefused() throws Exception {
            mockMvc.perform(post("/users/me/2fa/setup")).andExpect(status().isForbidden());
            mockMvc.perform(post("/users/me/2fa/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                    .andExpect(status().isForbidden());
            verify(twoFactorAuthService, never()).setup();
            verify(twoFactorAuthService, never()).confirm(anyString());
        }

        @Test
        @DisplayName("can't pay invoices or change their bank details")
        void paymentsRefused() throws Exception {
            mockMvc.perform(post("/contracts/" + uid(1) + "/sepa-payments").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"invoiceIds\":[\"00000000-0000-0000-0000-000000000002\"],\"executionDate\":\"2026-12-01\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/contracts/" + uid(1) + "/invoices/" + uid(2) + "/payment-details").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierIban\":\"IT60X0542811101000000123456\"}"))
                    .andExpect(status().isForbidden());
            verify(sepaPaymentService, never()).createSepaPayment(any(UUID.class), any(), any());
            verify(electronicInvoiceService, never()).updatePaymentDetails(any(UUID.class), any(UUID.class), any());
        }

        @Test
        @DisplayName("can't create or change financial values")
        void financialValuesRefused() throws Exception {
            mockMvc.perform(post("/financial-values").contentType(MediaType.APPLICATION_JSON).content(VALUE_BODY))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/financial-values/" + uid(1)).contentType(MediaType.APPLICATION_JSON).content(VALUE_BODY))
                    .andExpect(status().isForbidden());
            verify(financialValueService, never()).createValue(any());
            verify(financialValueService, never()).updateValue(any(UUID.class), any());
        }
    }

    @Nested
    @DisplayName("as any other account")
    @WithMockUser(username = "marco", roles = "MANAGER")
    class AsSomeoneElse {

        @Test
        @DisplayName("changes its own password and deletes as before")
        void allowed() throws Exception {
            mockMvc.perform(post("/users/me/password").contentType(MediaType.APPLICATION_JSON).content(PASSWORD_BODY))
                    .andExpect(status().isNoContent());
            mockMvc.perform(patch("/contracts/" + uid(1) + "/invoices/" + uid(2) + "/payment-details").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierIban\":\"IT60X0542811101000000123456\"}"))
                    .andExpect(status().isOk());
            verify(userService).changeOwnPassword("OldPass#2026", "NewPass#2026");
            verify(electronicInvoiceService).updatePaymentDetails(eq(uid(1)), eq(uid(2)), any());
        }
    }

    @Test
    @DisplayName("locks nobody when the setting is empty")
    void emptySettingLocksNobody() {
        PublicDemoGuard guard = new PublicDemoGuard("");
        assertFalse(guard.isPublicDemo("demo"));
        assertFalse(guard.isPublicDemo(null));
        assertTrue(new PublicDemoGuard(" demo ").isPublicDemo("demo"));
    }

    @Test
    @DisplayName("locks nobody without the setting, and ignores requests that aren't controller methods")
    void nullSettingAndStaticResources() {
        PublicDemoGuard guard = new PublicDemoGuard(null);
        assertFalse(guard.isPublicDemo("demo"));

        PublicDemoGuard locked = new PublicDemoGuard("demo");
        assertTrue(locked.preHandle(null, null, new Object()));
    }
}
