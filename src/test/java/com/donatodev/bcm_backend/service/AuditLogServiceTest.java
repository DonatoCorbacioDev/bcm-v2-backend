package com.donatodev.bcm_backend.service;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.AuditLogDTO;
import com.donatodev.bcm_backend.entity.AuditLog;
import com.donatodev.bcm_backend.repository.AuditLogRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class AuditLogServiceTest {

    /** The organization every request carries (its token's); a test that needs another sets its own. */
    private static final UUID TENANT = uid(1);

    @BeforeEach
    void enterTenant() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void leaveTenant() {
        TenantContext.clear();
    }

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: AuditLogService")
    @SuppressWarnings("unused")
    class VerifyAuditLogService {

        @Test
        @Order(1)
        @DisplayName("save persists audit log with correct fields")
        void shouldSaveAuditLog() {
            when(auditLogRepository.save(any(AuditLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            auditLogService.save("CREATE", "Contract", uid(1), "admin", uid(10), "ContractService.createContract");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            AuditLog saved = captor.getValue();

            assertEquals("CREATE", saved.getAction());
            assertEquals("Contract", saved.getEntityType());
            assertEquals(uid(1), saved.getEntityId());
            assertEquals("admin", saved.getUsername());
            assertEquals(uid(10), saved.getOrgId());
            assertEquals("ContractService.createContract", saved.getDetails());
            assertNotNull(saved.getTimestamp());
        }

        @Test
        @Order(2)
        @DisplayName("findAll returns page of AuditLogDTOs")
        void shouldReturnPageOfAuditLogs() {
            AuditLog log = AuditLog.builder()
                    .id(uid(1)).action("UPDATE").entityType("Manager").entityId(uid(5))
                    .username("admin").orgId(uid(1)).timestamp(Instant.parse("2027-01-15T12:00:00Z"))
                    .details("ManagerService.updateManager").build();

            Pageable pageable = PageRequest.of(0, 20);
            when(auditLogRepository.findAllByOrgIdOrderByTimestampDesc(TENANT, pageable))
                    .thenReturn(new PageImpl<>(List.of(log)));

            Page<AuditLogDTO> result = auditLogService.findAll(pageable);

            assertEquals(1, result.getTotalElements());
            AuditLogDTO dto = result.getContent().get(0);
            assertEquals("UPDATE", dto.action());
            assertEquals("Manager", dto.entityType());
            assertEquals("admin", dto.username());
        }

        @Test
        @Order(3)
        @DisplayName("findAll with TenantContext uses org-filtered repository")
        void shouldReturnPageOfAuditLogsScopedToOrganization() {
            AuditLog log = AuditLog.builder()
                    .id(uid(2)).action("DELETE").entityType("Contract").entityId(uid(7))
                    .username("manager").orgId(uid(3)).timestamp(Instant.parse("2027-02-01T08:30:00Z"))
                    .details("ContractService.deleteContract").build();

            Pageable pageable = PageRequest.of(0, 20);

            TenantContext.set(uid(3));
            try {
                when(auditLogRepository.findAllByOrgIdOrderByTimestampDesc(uid(3), pageable))
                        .thenReturn(new PageImpl<>(List.of(log)));

                Page<AuditLogDTO> result = auditLogService.findAll(pageable);

                assertEquals(1, result.getTotalElements());
                AuditLogDTO dto = result.getContent().get(0);
                assertEquals("DELETE", dto.action());
                assertEquals("Contract", dto.entityType());
                assertEquals(uid(3), dto.orgId());
                verify(auditLogRepository).findAllByOrgIdOrderByTimestampDesc(uid(3), pageable);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
