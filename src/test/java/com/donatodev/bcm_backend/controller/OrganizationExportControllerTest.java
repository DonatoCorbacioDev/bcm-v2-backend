package com.donatodev.bcm_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.AuditLogRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationExportControllerTest {

    private static final String EXPORT_URL = "/organizations/me/export";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private ManagersRepository managersRepository;
    @Autowired private RolesRepository rolesRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private Organization org;

    @BeforeEach
    void setup() {
        auditLogRepository.deleteAll();
        usersRepository.deleteAll();
        managersRepository.deleteAll();
        rolesRepository.deleteAll();
        organizationRepository.deleteAll();

        org = organizationRepository.save(Organization.builder()
                .name("Export Corp").slug("export-corp").subscriptionTier(SubscriptionTier.FREE)
                .iban("IT60X0542811101000000123456").build());
        Managers manager = managersRepository.save(Managers.builder()
                .firstName("Admin").lastName("User").email("admin@export.test").organization(org).build());
        Roles admin = rolesRepository.save(Roles.builder().role("ADMIN").build());
        usersRepository.save(Users.builder()
                .username("export-admin").passwordHash("pwd").verified(true)
                .role(admin).manager(manager).organization(org).build());
    }

    /** Runs the request through the async dispatch and returns the archive's entries by path. */
    private Map<String, byte[]> export() throws Exception {
        MvcResult started = mockMvc.perform(get(EXPORT_URL))
                .andExpect(request().asyncStarted())
                .andReturn();
        byte[] zip = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andReturn().getResponse().getContentAsByteArray();
        return unzip(zip);
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                entries.put(e.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "ADMIN")
    @DisplayName("Admin downloads a ZIP with the organization data and a manifest written last")
    void exportsOrganizationAndManifest() throws Exception {
        Map<String, byte[]> entries = export();

        assertThat(entries.keySet()).containsExactly("data/organization.json", "manifest.json");

        JsonNode organization = objectMapper.readTree(entries.get("data/organization.json"));
        assertThat(organization.get("name").asText()).isEqualTo("Export Corp");
        // The customer's own copy: unmasked, unlike GET /organizations/me.
        assertThat(organization.get("iban").asText()).isEqualTo("IT60X0542811101000000123456");
        assertThat(organization.has("stripeCustomerId")).isFalse();

        JsonNode manifest = objectMapper.readTree(entries.get("manifest.json"));
        assertThat(manifest.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(manifest.get("organizationId").asLong()).isEqualTo(org.getId());
        JsonNode file = manifest.get("files").get(0);
        byte[] content = entries.get("data/organization.json");
        assertThat(file.get("path").asText()).isEqualTo("data/organization.json");
        assertThat(file.get("records").asInt()).isEqualTo(1);
        assertThat(file.get("sizeBytes").asLong()).isEqualTo(content.length);
        assertThat(file.get("sha256").asText())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "ADMIN")
    @DisplayName("Names the file after the organization and forbids caching")
    void setsDownloadHeaders() throws Exception {
        MvcResult started = mockMvc.perform(get(EXPORT_URL)).andReturn();
        mockMvc.perform(asyncDispatch(started))
                .andExpect(header().string("Content-Disposition",
                        matchesPattern(
                                "attachment; filename=\"bcm-export-export-corp-\\d{4}-\\d{2}-\\d{2}\\.zip\"")))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "ADMIN")
    @DisplayName("Every export is recorded in the audit log")
    void recordsTheExport() throws Exception {
        export();

        assertThat(auditLogRepository.findAll())
                .anySatisfy(log -> {
                    assertThat(log.getAction()).isEqualTo("DATA_EXPORTED");
                    assertThat(log.getUsername()).isEqualTo("export-admin");
                    assertThat(log.getOrgId()).isEqualTo(org.getId());
                });
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "MANAGER")
    @DisplayName("A manager cannot export the organization")
    void managerIsForbidden() throws Exception {
        mockMvc.perform(get(EXPORT_URL)).andExpect(status().isForbidden());
        assertThat(auditLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Unauthenticated request returns 401")
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get(EXPORT_URL)).andExpect(status().isUnauthorized());
    }
}
