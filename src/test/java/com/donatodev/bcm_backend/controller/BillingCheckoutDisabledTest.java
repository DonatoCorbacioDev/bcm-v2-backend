package com.donatodev.bcm_backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.StripeCheckoutGateway;
import com.donatodev.bcm_backend.util.TestDataCleaner;

/**
 * The Pro plan is not for sale: with the default configuration, an admin of a
 * FREE organization calling the checkout endpoint directly (not through the
 * button) is refused, and Stripe is never contacted, even with Stripe keys
 * configured. Real authentication path, real property default.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "stripe.secret-key=sk_test_configured",
        "stripe.pro-price-id=price_configured",
})
@DisplayName("Pro checkout is switched off by default")
class BillingCheckoutDisabledTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private RolesRepository rolesRepository;
    @Autowired private CounterpartiesRepository counterpartiesRepository;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private TestDataCleaner testDataCleaner;

    @MockitoBean private StripeCheckoutGateway checkoutGateway;

    private String adminToken;

    @BeforeEach
    void setUp() {
        testDataCleaner.clean();
        counterpartiesRepository.deleteAll();
        organizationRepository.deleteAll();
        Organization org = organizationRepository.save(Organization.builder().name("Free Org").slug("free-org").build());
        Roles admin = rolesRepository.save(Roles.builder().role("ADMIN").build());
        Users user = usersRepository.save(Users.builder().username("free-admin").passwordHash("irrelevant")
                .verified(true).role(admin).organization(org).build());
        adminToken = jwtUtils.generateTokenFromUser(user);
    }

    @Test
    @DisplayName("POST /billing/checkout-session answers 409 and creates no Stripe session")
    void refusesCheckout() throws Exception {
        mockMvc.perform(post("/billing/checkout-session").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Il piano Pro non è ancora acquistabile: scrivici per avere informazioni"));

        verify(checkoutGateway, never()).createSubscriptionCheckout(any(), any(), any(), any(), any());
    }
}
