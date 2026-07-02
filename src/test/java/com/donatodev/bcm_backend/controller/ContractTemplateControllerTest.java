package com.donatodev.bcm_backend.controller;

import java.time.LocalDate;
import java.time.Month;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.ContractTemplateDTO;
import com.donatodev.bcm_backend.dto.InstantiateTemplateDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.ContractTemplate;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractTemplateRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link ContractTemplateController}.
 * <p>
 * Uses real JWTs (not {@code @WithMockUser}) so requests go through
 * {@link com.donatodev.bcm_backend.jwt.JwtAuthenticationFilter} and populate
 * {@link com.donatodev.bcm_backend.config.TenantContext} — the service layer
 * requires an organization context and throws otherwise.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContractTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContractTemplateRepository templateRepository;

    @Autowired
    private BusinessAreasRepository businessAreasRepository;

    @Autowired
    private ManagersRepository managersRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TestDataCleaner testDataCleaner;

    private Organization org;
    private String adminToken;
    private String managerToken;

    @BeforeEach
    void setUp() {
        testDataCleaner.clean();
        organizationRepository.deleteAll();

        org = organizationRepository.save(Organization.builder().name("Acme").slug("acme").build());

        Roles adminRole = rolesRepository.save(Roles.builder().role("ADMIN").build());
        Roles managerRole = rolesRepository.save(Roles.builder().role("MANAGER").build());

        Users admin = usersRepository.save(Users.builder()
                .username("admin").passwordHash("irrelevant").verified(true)
                .role(adminRole).organization(org).build());

        Users manager = usersRepository.save(Users.builder()
                .username("manager").passwordHash("irrelevant").verified(true)
                .role(managerRole).organization(org).build());

        adminToken = jwtUtils.generateTokenFromUser(admin);
        managerToken = jwtUtils.generateTokenFromUser(manager);
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("API Verification on Contract Template")
    @SuppressWarnings("unused")
    class VerificationApiContractTemplate {

        @Test
        @Order(1)
        @DisplayName("GET /contract-templates returns an empty list initially")
        void shouldReturnEmptyListInitially() throws Exception {
            mockMvc.perform(get("/contract-templates").header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @Order(2)
        @DisplayName("POST /contract-templates creates a new template as ADMIN")
        void shouldCreateTemplate() throws Exception {
            ContractTemplateDTO dto = new ContractTemplateDTO(
                    null, "NDA Standard", "Standard NDA", ContractStatus.ACTIVE,
                    90, null, null, true, 15, null);

            mockMvc.perform(post("/contract-templates")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("NDA Standard"))
                    .andExpect(jsonPath("$.defaultStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.autoRenew").value(true));
        }

        @Test
        @Order(3)
        @DisplayName("POST /contract-templates is forbidden for MANAGER")
        void shouldForbidCreateForManager() throws Exception {
            ContractTemplateDTO dto = new ContractTemplateDTO(
                    null, "Forbidden", null, null, null, null, null, false, null, null);

            mockMvc.perform(post("/contract-templates")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @Order(4)
        @DisplayName("GET /contract-templates/{id} returns the template")
        void shouldGetTemplateById() throws Exception {
            ContractTemplate saved = templateRepository.save(ContractTemplate.builder()
                    .name("By Id").autoRenew(false).orgId(org.getId()).build());

            mockMvc.perform(get("/contract-templates/{id}", saved.getId())
                    .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("By Id"));
        }

        @Test
        @Order(5)
        @DisplayName("GET /contract-templates/{id} returns 404 for an unknown id")
        void shouldReturnNotFoundForInvalidId() throws Exception {
            mockMvc.perform(get("/contract-templates/{id}", 9999L)
                    .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @Order(6)
        @DisplayName("GET /contract-templates lists templates for MANAGER too")
        void shouldAllowManagerToListTemplates() throws Exception {
            templateRepository.save(ContractTemplate.builder()
                    .name("Visible").autoRenew(false).orgId(org.getId()).build());

            mockMvc.perform(get("/contract-templates").header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @Order(7)
        @DisplayName("PUT /contract-templates/{id} updates the template and defaults a null status to DRAFT")
        void shouldUpdateTemplateAndDefaultNullStatusToDraft() throws Exception {
            ContractTemplate saved = templateRepository.save(ContractTemplate.builder()
                    .name("Old Name").defaultStatus(ContractStatus.ACTIVE).autoRenew(false)
                    .orgId(org.getId()).build());

            ContractTemplateDTO updated = new ContractTemplateDTO(
                    saved.getId(), "New Name", "Updated", null, null, null, null, true, null, null);

            mockMvc.perform(put("/contract-templates/{id}", saved.getId())
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updated)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New Name"))
                    .andExpect(jsonPath("$.defaultStatus").value("DRAFT"))
                    .andExpect(jsonPath("$.autoRenew").value(true));
        }

        @Test
        @Order(8)
        @DisplayName("DELETE /contract-templates/{id} removes the template as ADMIN")
        void shouldDeleteTemplate() throws Exception {
            ContractTemplate saved = templateRepository.save(ContractTemplate.builder()
                    .name("To Delete").autoRenew(false).orgId(org.getId()).build());

            mockMvc.perform(delete("/contract-templates/{id}", saved.getId())
                    .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());
        }

        @Test
        @Order(9)
        @DisplayName("DELETE /contract-templates/{id} is forbidden for MANAGER")
        void shouldForbidDeleteForManager() throws Exception {
            ContractTemplate saved = templateRepository.save(ContractTemplate.builder()
                    .name("Protected").autoRenew(false).orgId(org.getId()).build());

            mockMvc.perform(delete("/contract-templates/{id}", saved.getId())
                    .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @Order(10)
        @DisplayName("POST /contract-templates/{id}/instantiate uses explicit overrides from the request")
        void shouldInstantiateTemplateWithExplicitOverrides() throws Exception {
            BusinessAreas templateArea = businessAreasRepository.save(
                    BusinessAreas.builder().name("Template Area").description("d").build());
            BusinessAreas overrideArea = businessAreasRepository.save(
                    BusinessAreas.builder().name("Override Area").description("d").build());
            Managers overrideManager = managersRepository.save(Managers.builder()
                    .firstName("Over").lastName("Ride").email("over@ride.com")
                    .phoneNumber("123").department("Sales").build());

            ContractTemplate saved = templateRepository.save(ContractTemplate.builder()
                    .name("Base Template").defaultStatus(ContractStatus.DRAFT)
                    .defaultDurationDays(30).businessArea(templateArea).autoRenew(false)
                    .orgId(org.getId()).build());

            InstantiateTemplateDTO req = new InstantiateTemplateDTO(
                    "Acme Corp", "CTR-OVR-001", "WBS-1", "Project X",
                    LocalDate.of(2026, Month.JANUARY, 1), LocalDate.of(2026, Month.DECEMBER, 31),
                    overrideArea.getId(), overrideManager.getId(), ContractStatus.ACTIVE);

            mockMvc.perform(post("/contract-templates/{id}/instantiate", saved.getId())
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.customerName").value("Acme Corp"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.endDate").value("2026-12-31"))
                    .andExpect(jsonPath("$.areaId").value(overrideArea.getId()));
        }

        @Test
        @Order(11)
        @DisplayName("POST /contract-templates/{id}/instantiate falls back to the template's area, manager, status and computed end date")
        void shouldInstantiateTemplateUsingTemplateDefaults() throws Exception {
            BusinessAreas area = businessAreasRepository.save(
                    BusinessAreas.builder().name("Default Area").description("d").build());
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Default").lastName("Manager").email("default@manager.com")
                    .phoneNumber("456").department("Ops").build());

            ContractTemplate saved = templateRepository.save(ContractTemplate.builder()
                    .name("Full Default Template").defaultStatus(ContractStatus.DRAFT)
                    .defaultDurationDays(60).businessArea(area).defaultManager(manager)
                    .autoRenew(false).orgId(org.getId()).build());

            InstantiateTemplateDTO req = new InstantiateTemplateDTO(
                    "Beta Corp", "CTR-DEF-001", null, null,
                    LocalDate.of(2026, Month.JANUARY, 1), null,
                    null, null, null);

            mockMvc.perform(post("/contract-templates/{id}/instantiate", saved.getId())
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.customerName").value("Beta Corp"))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.endDate").value("2026-03-02"))
                    .andExpect(jsonPath("$.areaId").value(area.getId()))
                    .andExpect(jsonPath("$.managerId").value(manager.getId()));
        }

        @Test
        @Order(12)
        @DisplayName("POST /contract-templates/{id}/instantiate returns 400 when no business area is available")
        void shouldReturnBadRequestWhenNoBusinessAreaAvailable() throws Exception {
            ContractTemplate saved = templateRepository.save(ContractTemplate.builder()
                    .name("No Area Template").autoRenew(false).orgId(org.getId()).build());

            InstantiateTemplateDTO req = new InstantiateTemplateDTO(
                    "Gamma Corp", "CTR-NOAREA-001", null, null,
                    LocalDate.of(2026, Month.JANUARY, 1), null,
                    null, null, null);

            mockMvc.perform(post("/contract-templates/{id}/instantiate", saved.getId())
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(13)
        @DisplayName("POST /contract-templates/{id}/instantiate leaves endDate null when neither the request nor the template provide a duration")
        void shouldLeaveEndDateNullWhenNoDurationAvailable() throws Exception {
            BusinessAreas area = businessAreasRepository.save(
                    BusinessAreas.builder().name("No Duration Area").description("d").build());

            ContractTemplate saved = templateRepository.save(ContractTemplate.builder()
                    .name("No Duration Template").businessArea(area).autoRenew(false)
                    .orgId(org.getId()).build());

            InstantiateTemplateDTO req = new InstantiateTemplateDTO(
                    "Delta Corp", "CTR-NODUR-001", null, null,
                    LocalDate.of(2026, Month.JANUARY, 1), null,
                    null, null, null);

            mockMvc.perform(post("/contract-templates/{id}/instantiate", saved.getId())
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.customerName").value("Delta Corp"))
                    .andExpect(jsonPath("$.endDate").doesNotExist());
        }

        @Test
        @Order(14)
        @DisplayName("GET /contract-templates returns 401 without a token")
        void shouldReturnUnauthorizedWithoutToken() throws Exception {
            mockMvc.perform(get("/contract-templates"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
