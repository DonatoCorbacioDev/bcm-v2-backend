package com.donatodev.bcm_backend.service;

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

import com.donatodev.bcm_backend.dto.AuditLogDTO;
import com.donatodev.bcm_backend.entity.AuditLog;
import com.donatodev.bcm_backend.repository.AuditLogRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class AuditLogServiceTest {

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

            auditLogService.save("CREATE", "Contract", 1L, "admin", 10L, "ContractService.createContract");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            AuditLog saved = captor.getValue();

            assertEquals("CREATE", saved.getAction());
            assertEquals("Contract", saved.getEntityType());
            assertEquals(1L, saved.getEntityId());
            assertEquals("admin", saved.getUsername());
            assertEquals(10L, saved.getOrgId());
            assertEquals("ContractService.createContract", saved.getDetails());
            assertNotNull(saved.getTimestamp());
        }

        @Test
        @Order(2)
        @DisplayName("findAll returns page of AuditLogDTOs")
        void shouldReturnPageOfAuditLogs() {
            AuditLog log = AuditLog.builder()
                    .id(1L).action("UPDATE").entityType("Manager").entityId(5L)
                    .username("admin").orgId(1L).timestamp(Instant.now())
                    .details("ManagerService.updateManager").build();

            Pageable pageable = PageRequest.of(0, 20);
            when(auditLogRepository.findAllByOrderByTimestampDesc(pageable))
                    .thenReturn(new PageImpl<>(List.of(log)));

            Page<AuditLogDTO> result = auditLogService.findAll(pageable);

            assertEquals(1, result.getTotalElements());
            AuditLogDTO dto = result.getContent().get(0);
            assertEquals("UPDATE", dto.action());
            assertEquals("Manager", dto.entityType());
            assertEquals("admin", dto.username());
        }
    }
}
