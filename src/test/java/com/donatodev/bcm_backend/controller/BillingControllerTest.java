package com.donatodev.bcm_backend.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.donatodev.bcm_backend.service.BillingService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private BillingService billingService;

    @Test
    @DisplayName("webhook is reachable without a JWT and passes the raw body and signature through")
    void webhookNeedsNoJwt() throws Exception {
        String payload = "{\"id\":\"evt_1\"}";
        mockMvc.perform(post("/billing/webhook")
                        .header("Stripe-Signature", "t=1,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(billingService).handleWebhook(payload, "t=1,v1=abc");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("checkout returns the Stripe URL to an ADMIN")
    void checkoutForAdmin() throws Exception {
        when(billingService.createProCheckoutUrl()).thenReturn("https://checkout.stripe.com/c/pay/cs_test_1");

        mockMvc.perform(post("/billing/checkout-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/c/pay/cs_test_1"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("checkout is forbidden to a MANAGER — only an org admin can commit the org to a paid plan")
    void checkoutForbiddenForManager() throws Exception {
        mockMvc.perform(post("/billing/checkout-session"))
                .andExpect(status().isForbidden());

        verify(billingService, never()).createProCheckoutUrl();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("portal returns the Stripe Customer Portal URL to an ADMIN")
    void portalForAdmin() throws Exception {
        when(billingService.createPortalUrl()).thenReturn("https://billing.stripe.com/p/session/test_1");

        mockMvc.perform(post("/billing/portal-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://billing.stripe.com/p/session/test_1"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    @DisplayName("portal is forbidden to a MANAGER — cancelling the org's plan is an admin decision")
    void portalForbiddenForManager() throws Exception {
        mockMvc.perform(post("/billing/portal-session"))
                .andExpect(status().isForbidden());

        verify(billingService, never()).createPortalUrl();
    }

    @Test
    @DisplayName("checkout requires authentication")
    void checkoutRequiresAuth() throws Exception {
        mockMvc.perform(post("/billing/checkout-session"))
                .andExpect(status().isUnauthorized());

        verify(billingService, never()).handleWebhook(anyString(), anyString());
    }
}
