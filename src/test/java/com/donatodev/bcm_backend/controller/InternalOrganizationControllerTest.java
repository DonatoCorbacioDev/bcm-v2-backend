package com.donatodev.bcm_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.repository.AuditLogRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.security.InternalApiKeyGuard;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "internal.admin-api-key=test-admin-key")
class InternalOrganizationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private Long orgId;

    @BeforeEach
    void setup() {
        orgId = organizationRepository.save(Organization.builder()
                .name("Internal Test Org").slug("internal-test-org-" + System.nanoTime())
                .subscriptionTier(SubscriptionTier.FREE).build()).getId();
    }

    private static String tierUrl(Long id) {
        return "/internal/organizations/" + id + "/tier";
    }

    @Test
    @DisplayName("changes the tier with the right key, no JWT needed")
    void shouldChangeTierWithValidKey() throws Exception {
        mockMvc.perform(put(tierUrl(orgId))
                        .header(InternalApiKeyGuard.HEADER, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionTier").value("PRO"));

        assertEquals(SubscriptionTier.PRO, organizationRepository.findById(orgId).orElseThrow().getSubscriptionTier());
        assertEquals(1, auditLogRepository.findAll().stream()
                .filter(log -> "TIER_CHANGED".equals(log.getAction()) && orgId.equals(log.getEntityId()))
                .count());
    }

    @Test
    @DisplayName("rejects a wrong key with 403 and leaves the tier untouched")
    void shouldRejectWrongKey() throws Exception {
        mockMvc.perform(put(tierUrl(orgId))
                        .header(InternalApiKeyGuard.HEADER, "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\":\"ENTERPRISE\"}"))
                .andExpect(status().isForbidden());

        assertEquals(SubscriptionTier.FREE, organizationRepository.findById(orgId).orElseThrow().getSubscriptionTier());
    }

    @Test
    @DisplayName("rejects a missing key with 403")
    void shouldRejectMissingKey() throws Exception {
        mockMvc.perform(put(tierUrl(orgId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\":\"PRO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rejects a missing tier with 400")
    void shouldRejectMissingTier() throws Exception {
        mockMvc.perform(put(tierUrl(orgId))
                        .header(InternalApiKeyGuard.HEADER, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("returns 404 for an unknown organization")
    void shouldReturn404ForUnknownOrg() throws Exception {
        mockMvc.perform(put(tierUrl(999_999L))
                        .header(InternalApiKeyGuard.HEADER, "test-admin-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\":\"PRO\"}"))
                .andExpect(status().isNotFound());
    }
}
