package com.donatodev.bcm_backend.controller;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setup() {
        auditLogRepository.deleteAll();
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
                    .username("admin").orgId(1L).timestamp(Instant.now())
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
                        .username("admin").orgId(1L).timestamp(Instant.now())
                        .details("ManagerService.deleteManager").build());
            }

            mockMvc.perform(get("/audit-logs?size=3&page=0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(3)))
                    .andExpect(jsonPath("$.totalElements").value(5));
        }
    }
}
