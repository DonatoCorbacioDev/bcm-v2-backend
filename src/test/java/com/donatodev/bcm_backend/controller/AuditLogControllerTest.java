package com.donatodev.bcm_backend.controller;

import org.junit.jupiter.api.AfterEach;
import com.donatodev.bcm_backend.support.TestTenant;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.entity.Organization;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.entity.AuditLog;
import com.donatodev.bcm_backend.repository.AuditLogRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditLogControllerTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    /** The organization this class's requests and fixtures belong to, as a real token's would. */
    private Organization testOrg;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void leaveTestTenant() {
        TestTenant.clear();
    }

    @BeforeEach
    void setup() {
        auditLogRepository.deleteAll();
        testOrg = organizationRepository.save(Organization.builder()
                .name("Test org").slug("test-org-" + java.util.UUID.randomUUID()).build());
        TestTenant.use(testOrg.getId());
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("GET /audit-logs")
    @SuppressWarnings("unused")
    class GetAuditLogs {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin can retrieve paginated audit logs")
        void shouldReturnAuditLogsForAdmin() throws Exception {
            auditLogRepository.save(AuditLog.builder()
                    .action("CREATE").entityType("Contract").entityId(1L)
                    .username("admin").orgId(testOrg.getId()).timestamp(Instant.parse("2027-01-15T12:00:00Z"))
                    .details("ContractService.createContract").build());

            mockMvc.perform(get("/audit-logs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].action").value("CREATE"))
                    .andExpect(jsonPath("$.content[0].entityType").value("Contract"));
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "MANAGER")
        @DisplayName("Manager is denied access to audit logs")
        void shouldReturn403ForManager() throws Exception {
            mockMvc.perform(get("/audit-logs"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @Order(3)
        @DisplayName("Unauthenticated request is denied")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/audit-logs"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @Order(4)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns empty page when no logs exist")
        void shouldReturnEmptyPageWhenNoLogs() throws Exception {
            mockMvc.perform(get("/audit-logs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @Order(5)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Pagination parameters are respected")
        void shouldRespectPaginationParameters() throws Exception {
            for (int i = 1; i <= 5; i++) {
                auditLogRepository.save(AuditLog.builder()
                        .action("DELETE").entityType("Manager").entityId((long) i)
                        .username("admin").orgId(testOrg.getId()).timestamp(Instant.parse("2027-01-15T12:00:00Z"))
                        .details("ManagerService.deleteManager").build());
            }

            mockMvc.perform(get("/audit-logs?size=3&page=0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(3)))
                    .andExpect(jsonPath("$.totalElements").value(5));
        }
    }
}
