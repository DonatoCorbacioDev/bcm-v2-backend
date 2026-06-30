package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.dto.ContractTemplateDTO;
import com.donatodev.bcm_backend.dto.InstantiateTemplateDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.ContractTemplate;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.exception.BusinessAreaNotFoundException;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.ManagerNotFoundException;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractTemplateRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ContractTemplateServiceTest {

    @Mock private ContractTemplateRepository templateRepository;
    @Mock private BusinessAreasRepository businessAreasRepository;
    @Mock private ManagersRepository managersRepository;
    @Mock private ContractService contractService;

    @InjectMocks
    private ContractTemplateService templateService;

    private static final Long ORG_ID = 10L;
    private static final Long TEMPLATE_ID = 1L;

    @BeforeEach
    void setup() {
        TenantContext.set(ORG_ID);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private ContractTemplate fakeTemplate() {
        return ContractTemplate.builder()
                .id(TEMPLATE_ID)
                .name("Standard NDA")
                .description("Non-disclosure agreement template")
                .defaultStatus(ContractStatus.DRAFT)
                .defaultDurationDays(365)
                .autoRenew(false)
                .notificationDays(30)
                .orgId(ORG_ID)
                .build();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: ContractTemplateService")
    @SuppressWarnings("unused")
    class ContractTemplateTests {

        @Test
        @Order(1)
        @DisplayName("getTemplates returns list of DTOs for current org")
        void shouldReturnTemplateList() {
            when(templateRepository.findAllByOrgIdOrderByCreatedAtDesc(ORG_ID))
                    .thenReturn(List.of(fakeTemplate()));

            List<ContractTemplateDTO> result = templateService.getTemplates();

            assertEquals(1, result.size());
            assertEquals("Standard NDA", result.get(0).name());
            assertEquals(365, result.get(0).defaultDurationDays());
        }

        @Test
        @Order(2)
        @DisplayName("getTemplate returns DTO when template belongs to org")
        void shouldReturnSingleTemplate() {
            when(templateRepository.findByIdAndOrgId(TEMPLATE_ID, ORG_ID))
                    .thenReturn(Optional.of(fakeTemplate()));

            ContractTemplateDTO result = templateService.getTemplate(TEMPLATE_ID);

            assertEquals(TEMPLATE_ID, result.id());
            assertEquals(ContractStatus.DRAFT, result.defaultStatus());
        }

        @Test
        @Order(3)
        @DisplayName("getTemplate throws ContractNotFoundException when not found")
        void shouldThrowWhenTemplateNotFound() {
            when(templateRepository.findByIdAndOrgId(TEMPLATE_ID, ORG_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> templateService.getTemplate(TEMPLATE_ID));
        }

        @Test
        @Order(4)
        @DisplayName("createTemplate saves entity with orgId and returns DTO")
        void shouldCreateTemplate() {
            ContractTemplateDTO dto = new ContractTemplateDTO(
                    null, "NDA", "desc", ContractStatus.DRAFT, 180,
                    null, null, false, 30, null);
            ContractTemplate saved = fakeTemplate();

            when(templateRepository.save(any(ContractTemplate.class))).thenReturn(saved);

            ContractTemplateDTO result = templateService.createTemplate(dto);

            assertNotNull(result);
            assertEquals("Standard NDA", result.name());
            verify(templateRepository).save(any(ContractTemplate.class));
        }

        @Test
        @Order(5)
        @DisplayName("createTemplate resolves businessAreaId to entity")
        void shouldResolveBusinessArea() {
            BusinessAreas area = new BusinessAreas();
            area.setId(5L);
            ContractTemplateDTO dto = new ContractTemplateDTO(
                    null, "Sales", null, ContractStatus.ACTIVE, 365,
                    5L, null, false, null, null);
            ContractTemplate saved = fakeTemplate();
            saved.setBusinessArea(area);

            when(businessAreasRepository.findById(5L)).thenReturn(Optional.of(area));
            when(templateRepository.save(any(ContractTemplate.class))).thenReturn(saved);

            ContractTemplateDTO result = templateService.createTemplate(dto);

            assertEquals(5L, result.businessAreaId());
        }

        @Test
        @Order(6)
        @DisplayName("createTemplate throws BusinessAreaNotFoundException when area not found")
        void shouldThrowWhenBusinessAreaNotFound() {
            ContractTemplateDTO dto = new ContractTemplateDTO(
                    null, "NDA", null, null, null,
                    99L, null, false, null, null);

            when(businessAreasRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(BusinessAreaNotFoundException.class,
                    () -> templateService.createTemplate(dto));
        }

        @Test
        @Order(7)
        @DisplayName("createTemplate throws ManagerNotFoundException when manager not found")
        void shouldThrowWhenManagerNotFound() {
            ContractTemplateDTO dto = new ContractTemplateDTO(
                    null, "NDA", null, null, null,
                    null, 99L, false, null, null);

            when(managersRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(ManagerNotFoundException.class,
                    () -> templateService.createTemplate(dto));
        }

        @Test
        @Order(8)
        @DisplayName("updateTemplate updates fields and returns DTO")
        void shouldUpdateTemplate() {
            ContractTemplateDTO dto = new ContractTemplateDTO(
                    null, "Updated NDA", "new desc", ContractStatus.ACTIVE, 730,
                    null, null, true, 60, null);
            ContractTemplate existing = fakeTemplate();

            when(templateRepository.findByIdAndOrgId(TEMPLATE_ID, ORG_ID))
                    .thenReturn(Optional.of(existing));
            when(templateRepository.save(any(ContractTemplate.class))).thenReturn(existing);

            ContractTemplateDTO result = templateService.updateTemplate(TEMPLATE_ID, dto);

            assertNotNull(result);
            verify(templateRepository).save(existing);
        }

        @Test
        @Order(9)
        @DisplayName("deleteTemplate removes the template")
        void shouldDeleteTemplate() {
            ContractTemplate template = fakeTemplate();
            when(templateRepository.findByIdAndOrgId(TEMPLATE_ID, ORG_ID))
                    .thenReturn(Optional.of(template));

            templateService.deleteTemplate(TEMPLATE_ID);

            verify(templateRepository).delete(template);
        }

        @Test
        @Order(10)
        @DisplayName("instantiateTemplate creates contract with template defaults")
        void shouldInstantiateTemplateWithDefaults() {
            ContractTemplate template = fakeTemplate();
            BusinessAreas area = new BusinessAreas();
            area.setId(3L);
            template.setBusinessArea(area);

            InstantiateTemplateDTO req = new InstantiateTemplateDTO(
                    "Acme Corp", "CTR-001", null, null,
                    LocalDate.of(2026, 1, 1), null,
                    null, null, null);

            ContractDTO created = new ContractDTO(
                    42L, "Acme Corp", "CTR-001", null, null,
                    ContractStatus.DRAFT, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                    3L, null, null, null, null, null);

            when(templateRepository.findByIdAndOrgId(TEMPLATE_ID, ORG_ID))
                    .thenReturn(Optional.of(template));
            when(contractService.createContract(any(ContractDTO.class))).thenReturn(created);

            ContractDTO result = templateService.instantiateTemplate(TEMPLATE_ID, req);

            assertEquals(42L, result.id());
            assertEquals("Acme Corp", result.customerName());
        }

        @Test
        @Order(11)
        @DisplayName("instantiateTemplate calculates endDate from defaultDurationDays")
        void shouldCalculateEndDate() {
            ContractTemplate template = fakeTemplate(); // defaultDurationDays = 365
            BusinessAreas area = new BusinessAreas();
            area.setId(3L);
            template.setBusinessArea(area);

            InstantiateTemplateDTO req = new InstantiateTemplateDTO(
                    "Acme", "CTR-002", null, null,
                    LocalDate.of(2026, 1, 1), null,
                    null, null, null);

            when(templateRepository.findByIdAndOrgId(TEMPLATE_ID, ORG_ID))
                    .thenReturn(Optional.of(template));
            when(contractService.createContract(any(ContractDTO.class))).thenAnswer(inv -> {
                ContractDTO arg = inv.getArgument(0);
                assertEquals(LocalDate.of(2027, 1, 1), arg.endDate());
                return new ContractDTO(1L, "Acme", "CTR-002", null, null,
                        ContractStatus.DRAFT, arg.startDate(), arg.endDate(),
                        3L, null, null, null, null, null);
            });

            templateService.instantiateTemplate(TEMPLATE_ID, req);
        }

        @Test
        @Order(12)
        @DisplayName("instantiateTemplate uses req endDate over calculated one")
        void shouldUseExplicitEndDate() {
            ContractTemplate template = fakeTemplate(); // defaultDurationDays = 365
            BusinessAreas area = new BusinessAreas();
            area.setId(3L);
            template.setBusinessArea(area);

            LocalDate explicitEnd = LocalDate.of(2026, 6, 30);
            InstantiateTemplateDTO req = new InstantiateTemplateDTO(
                    "Acme", "CTR-003", null, null,
                    LocalDate.of(2026, 1, 1), explicitEnd,
                    null, null, null);

            when(templateRepository.findByIdAndOrgId(TEMPLATE_ID, ORG_ID))
                    .thenReturn(Optional.of(template));
            when(contractService.createContract(any(ContractDTO.class))).thenAnswer(inv -> {
                ContractDTO arg = inv.getArgument(0);
                assertEquals(explicitEnd, arg.endDate());
                return new ContractDTO(1L, "Acme", "CTR-003", null, null,
                        ContractStatus.DRAFT, arg.startDate(), arg.endDate(),
                        3L, null, null, null, null, null);
            });

            templateService.instantiateTemplate(TEMPLATE_ID, req);
        }

        @Test
        @Order(13)
        @DisplayName("instantiateTemplate throws when no business area in template or request")
        void shouldThrowWhenNoBusinessArea() {
            ContractTemplate template = fakeTemplate();
            template.setBusinessArea(null);

            InstantiateTemplateDTO req = new InstantiateTemplateDTO(
                    "Acme", "CTR-004", null, null,
                    LocalDate.of(2026, 1, 1), null,
                    null, null, null);

            when(templateRepository.findByIdAndOrgId(TEMPLATE_ID, ORG_ID))
                    .thenReturn(Optional.of(template));

            assertThrows(IllegalArgumentException.class,
                    () -> templateService.instantiateTemplate(TEMPLATE_ID, req));
        }

        @Test
        @Order(14)
        @DisplayName("instantiateTemplate uses req businessAreaId override")
        void shouldOverrideBusinessArea() {
            ContractTemplate template = fakeTemplate();
            template.setBusinessArea(null);

            InstantiateTemplateDTO req = new InstantiateTemplateDTO(
                    "Acme", "CTR-005", null, null,
                    LocalDate.of(2026, 1, 1), null,
                    7L, null, null);

            when(templateRepository.findByIdAndOrgId(TEMPLATE_ID, ORG_ID))
                    .thenReturn(Optional.of(template));
            when(contractService.createContract(any(ContractDTO.class))).thenAnswer(inv -> {
                ContractDTO arg = inv.getArgument(0);
                assertEquals(7L, arg.areaId());
                return new ContractDTO(1L, "Acme", "CTR-005", null, null,
                        ContractStatus.DRAFT, arg.startDate(), null, 7L, null, null, null, null, null);
            });

            templateService.instantiateTemplate(TEMPLATE_ID, req);
        }

        @Test
        @Order(15)
        @DisplayName("requireOrgId throws when TenantContext is null")
        void shouldThrowWhenNoOrgContext() {
            TenantContext.clear();
            assertThrows(IllegalStateException.class,
                    () -> templateService.getTemplates());
        }

        @Test
        @Order(16)
        @DisplayName("createTemplate uses DRAFT as default status when null")
        void shouldDefaultToDraftStatus() {
            ContractTemplateDTO dto = new ContractTemplateDTO(
                    null, "NDA", null, null, null,
                    null, null, false, null, null);
            ContractTemplate saved = fakeTemplate();

            when(templateRepository.save(any(ContractTemplate.class))).thenAnswer(inv -> {
                ContractTemplate t = inv.getArgument(0);
                assertEquals(ContractStatus.DRAFT, t.getDefaultStatus());
                return saved;
            });

            templateService.createTemplate(dto);
        }

        @Test
        @Order(17)
        @DisplayName("instantiateTemplate applies req status override")
        void shouldOverrideStatus() {
            ContractTemplate template = fakeTemplate();
            BusinessAreas area = new BusinessAreas();
            area.setId(3L);
            template.setBusinessArea(area);

            InstantiateTemplateDTO req = new InstantiateTemplateDTO(
                    "Acme", "CTR-006", null, null,
                    LocalDate.of(2026, 1, 1), null,
                    null, null, ContractStatus.ACTIVE);

            when(templateRepository.findByIdAndOrgId(TEMPLATE_ID, ORG_ID))
                    .thenReturn(Optional.of(template));
            when(contractService.createContract(any(ContractDTO.class))).thenAnswer(inv -> {
                ContractDTO arg = inv.getArgument(0);
                assertEquals(ContractStatus.ACTIVE, arg.status());
                return new ContractDTO(1L, "Acme", "CTR-006", null, null,
                        ContractStatus.ACTIVE, arg.startDate(), null, 3L, null, null, null, null, null);
            });

            templateService.instantiateTemplate(TEMPLATE_ID, req);
        }

        @Test
        @Order(18)
        @DisplayName("toDTO returns null businessAreaId when no businessArea set")
        void shouldReturnNullBusinessAreaIdInDTO() {
            ContractTemplate t = fakeTemplate();
            t.setBusinessArea(null);

            when(templateRepository.findByIdAndOrgId(TEMPLATE_ID, ORG_ID))
                    .thenReturn(Optional.of(t));

            ContractTemplateDTO result = templateService.getTemplate(TEMPLATE_ID);

            assertNull(result.businessAreaId());
        }

        @Test
        @Order(19)
        @DisplayName("toDTO returns null defaultManagerId when no defaultManager set")
        void shouldReturnNullDefaultManagerIdInDTO() {
            ContractTemplate t = fakeTemplate();
            t.setDefaultManager(null);

            when(templateRepository.findByIdAndOrgId(TEMPLATE_ID, ORG_ID))
                    .thenReturn(Optional.of(t));

            ContractTemplateDTO result = templateService.getTemplate(TEMPLATE_ID);

            assertNull(result.defaultManagerId());
        }

        @Test
        @Order(20)
        @DisplayName("createTemplate resolves defaultManagerId to entity")
        void shouldResolveManager() {
            Managers manager = Managers.builder().id(7L).build();
            ContractTemplateDTO dto = new ContractTemplateDTO(
                    null, "NDA", null, null, null,
                    null, 7L, false, null, null);
            ContractTemplate saved = fakeTemplate();
            saved.setDefaultManager(manager);

            when(managersRepository.findById(7L)).thenReturn(Optional.of(manager));
            when(templateRepository.save(any(ContractTemplate.class))).thenReturn(saved);

            ContractTemplateDTO result = templateService.createTemplate(dto);

            assertEquals(7L, result.defaultManagerId());
        }
    }
}
