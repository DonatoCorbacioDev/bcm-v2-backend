package com.donatodev.bcm_backend.controller;

import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.RiskFeedbackRequest;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link RiskFeedbackController}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RiskFeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContractsRepository contractsRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private ManagersRepository managersRepository;

    @Autowired
    private BusinessAreasRepository businessAreasRepository;

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TestDataCleaner testDataCleaner;

    @BeforeEach
    @SuppressWarnings("unused")
    void cleanDb() {
        testDataCleaner.clean();
    }

    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @DisplayName("API Verification on Risk Feedback")
    @SuppressWarnings("unused")
    class VerificationApiRiskFeedback {

        @Test
        @Order(1)
        @DisplayName("Feedback is created for a contract as ADMIN")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void shouldCreateFeedback() throws Exception {
            Organization organization = organizationRepository.save(Organization.builder()
                    .name("Acme Legal").slug("acme-legal").build());

            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Marco").lastName("Rossi").email("marco.rossi@example.com").department("Legal").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Legal Area").description("Handles legal affairs").build());

            Contracts contract = contractsRepository.save(Contracts.builder()
                    .customerName("Cliente Uno").contractNumber("RF001").projectName("Legal Project")
                    .businessArea(area).manager(manager).organization(organization)
                    .startDate(LocalDate.of(2027, Month.JUNE, 15)).endDate(LocalDate.of(2027, Month.JUNE, 15).plusDays(365))
                    .status(ContractStatus.ACTIVE).build());

            Roles savedRole = rolesRepository.save(Roles.builder().role("ADMIN").build());

            usersRepository.save(Users.builder()
                    .username("admin").passwordHash("hashedpassword").manager(manager).role(savedRole).build());

            RiskFeedbackRequest request = new RiskFeedbackRequest(0.82, "HIGH", 0.7, "HIGH", true);

            mockMvc.perform(post("/risk-feedback/contracts/{contractId}", contract.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.contractId").value(contract.getId()))
                    .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                    .andExpect(jsonPath("$.agree").value(true));
        }

        @Test
        @Order(2)
        @DisplayName("Feedback list returns the most recent entry for the contract")
        @WithMockUser(username = "elena.hr", roles = "ADMIN")
        void shouldGetFeedbackList() throws Exception {
            Organization organization = organizationRepository.save(Organization.builder()
                    .name("Acme HR").slug("acme-hr").build());

            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Elena").lastName("Bianchi").email("elena.bianchi@example.com").department("HR").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("HR Area").description("Handles human resources").build());

            Contracts contract = contractsRepository.save(Contracts.builder()
                    .customerName("Client Beta").contractNumber("RF002").projectName("HR Project")
                    .businessArea(area).manager(manager).organization(organization)
                    .startDate(LocalDate.of(2027, Month.JUNE, 15)).endDate(LocalDate.of(2027, Month.JUNE, 15).plusMonths(6))
                    .status(ContractStatus.ACTIVE).build());

            Roles savedRole = rolesRepository.findByRole("ADMIN")
                    .orElseGet(() -> rolesRepository.save(Roles.builder().role("ADMIN").build()));

            usersRepository.save(Users.builder()
                    .username("elena.hr").passwordHash("passwordhr").manager(manager).role(savedRole).build());

            RiskFeedbackRequest firstAnswer = new RiskFeedbackRequest(0.3, "LOW", null, null, false);
            RiskFeedbackRequest secondAnswer = new RiskFeedbackRequest(0.3, "LOW", null, null, true);

            mockMvc.perform(post("/risk-feedback/contracts/{contractId}", contract.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(firstAnswer)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/risk-feedback/contracts/{contractId}", contract.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(secondAnswer)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/risk-feedback"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].agree").value(true));
        }

        @Test
        @Order(3)
        @DisplayName("Feedback submission is rejected without a level")
        @WithMockUser(username = "giorgio.neri", roles = "ADMIN")
        void shouldRejectInvalidPayload() throws Exception {
            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Giorgio").lastName("Neri").email("giorgio.neri@example.com").department("Finance").build());

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Finance Area").description("Finance operations").build());

            Contracts contract = contractsRepository.save(Contracts.builder()
                    .customerName("Finance Corp").contractNumber("RF003").projectName("Finance Project")
                    .businessArea(area).manager(manager)
                    .startDate(LocalDate.of(2027, Month.JUNE, 15)).endDate(LocalDate.of(2027, Month.JUNE, 15).plusMonths(12))
                    .status(ContractStatus.ACTIVE).build());

            Roles savedRole = rolesRepository.findByRole("ADMIN")
                    .orElseGet(() -> rolesRepository.save(Roles.builder().role("ADMIN").build()));

            usersRepository.save(Users.builder()
                    .username("giorgio.neri").passwordHash("passwordfinance").manager(manager).role(savedRole).build());

            String invalidJson = "{\"riskScore\":0.5,\"agree\":true}";

            mockMvc.perform(post("/risk-feedback/contracts/{contractId}", contract.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(4)
        @DisplayName("Feedback submission for a non-existent contract returns 404")
        @WithMockUser(username = "anna.verdi", roles = "ADMIN")
        void shouldReturnNotFoundForMissingContract() throws Exception {
            Roles savedRole = rolesRepository.findByRole("ADMIN")
                    .orElseGet(() -> rolesRepository.save(Roles.builder().role("ADMIN").build()));

            Managers manager = managersRepository.save(Managers.builder()
                    .firstName("Anna").lastName("Verdi").email("anna.verdi@example.com").department("Operations").build());

            usersRepository.save(Users.builder()
                    .username("anna.verdi").passwordHash("securepass").manager(manager).role(savedRole).build());

            RiskFeedbackRequest request = new RiskFeedbackRequest(0.5, "MEDIUM", null, null, true);

            mockMvc.perform(post("/risk-feedback/contracts/{contractId}", 999999L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }
}
