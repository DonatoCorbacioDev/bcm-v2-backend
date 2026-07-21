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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.BudgetDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.BudgetRepository;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for {@link BudgetController}.
 * <p>
 * Verifies the REST API endpoints related to {@code Budget}, covering full
 * CRUD operations plus the actual-amount computation against
 * {@code FinancialValues}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BusinessAreasRepository businessAreasRepository;

    @Autowired
    private FinancialTypesRepository financialTypesRepository;

    @Autowired
    private ContractsRepository contractsRepository;

    @Autowired
    private FinancialValuesRepository financialValuesRepository;

    @Autowired
    private ManagersRepository managersRepository;

    @Autowired
    private BudgetRepository budgetRepository;

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

    @BeforeEach
    @SuppressWarnings("unused")
    void cleanDb() {
        testDataCleaner.clean();
    }

    private Managers createManager() {
        return managersRepository.save(Managers.builder()
                .firstName("Test")
                .lastName("Manager")
                .email("test.manager." + System.currentTimeMillis() + "@example.com")
                .department("TestDept")
                .build());
    }

    private Contracts createContract(BusinessAreas area, Managers manager) {
        return contractsRepository.save(Contracts.builder()
                .customerName("Customer " + System.currentTimeMillis())
                .contractNumber("CONTRACT-" + System.currentTimeMillis())
                .businessArea(area)
                .manager(manager)
                .startDate(LocalDate.of(2025, Month.JANUARY, 1))
                .status(ContractStatus.ACTIVE)
                .build());
    }

    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @DisplayName("API Verification on Budget")
    @SuppressWarnings("unused")
    class VerificationApiBudget {

        @Test
        @Order(1)
        @DisplayName("The new budget has been created")
        @WithMockUser(roles = "ADMIN")
        void shouldCreateBudget() throws Exception {
            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Operations")
                    .description("Ops")
                    .build());

            BudgetDTO dto = new BudgetDTO(null, area.getId(), null, FinancialCategory.COST, 2025, 50000.0, 0, 0);

            mockMvc.perform(post("/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.targetAmount").value(50000.0))
                    .andExpect(jsonPath("$.category").value("COST"))
                    .andExpect(jsonPath("$.year").value(2025))
                    .andExpect(jsonPath("$.actualAmount").value(0.0))
                    .andExpect(jsonPath("$.percentUsed").value(0.0));
        }

        @Test
        @Order(2)
        @DisplayName("All budgets have been recovered successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldGetAllBudgets() throws Exception {
            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("IT").description("IT dept").build());
            budgetRepository.save(com.donatodev.bcm_backend.entity.Budget.builder()
                    .businessArea(area).category(FinancialCategory.REVENUE).year(2025).targetAmount(1000.0).build());

            mockMvc.perform(get("/budgets"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].areaName").value("IT"));
        }

        @Test
        @Order(3)
        @DisplayName("The budget with given ID has been retrieved successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldGetBudgetById() throws Exception {
            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Finance").description("Finance dept").build());
            com.donatodev.bcm_backend.entity.Budget saved = budgetRepository.save(
                    com.donatodev.bcm_backend.entity.Budget.builder()
                            .businessArea(area).category(FinancialCategory.COST).year(2026).targetAmount(2000.0).build());

            mockMvc.perform(get("/budgets/{id}", saved.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(saved.getId()))
                    .andExpect(jsonPath("$.year").value(2026));
        }

        @Test
        @Order(4)
        @DisplayName("Actual amount and percent used are computed from matching financial values")
        void shouldComputeActualAmountFromFinancialValues() throws Exception {
            Organization org = organizationRepository.save(
                    Organization.builder().name("Budget Test Org").slug("budget-test-org").build());
            Roles adminRole = rolesRepository.save(Roles.builder().role("ADMIN").build());
            Users admin = usersRepository.save(Users.builder()
                    .username("budget-admin")
                    .passwordHash("irrelevant")
                    .verified(true)
                    .role(adminRole)
                    .organization(org)
                    .build());
            String token = jwtUtils.generateTokenFromUser(admin);

            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Sales").description("Sales dept").organization(org).build());
            FinancialTypes revenueType = financialTypesRepository.save(FinancialTypes.builder()
                    .name("Contract revenue").category(FinancialCategory.REVENUE).organization(org).build());
            Managers manager = createManager();
            Contracts contract = createContract(area, manager);

            financialValuesRepository.save(FinancialValues.builder()
                    .month(3).year(2025).financialAmount(3000.0)
                    .financialType(revenueType).businessArea(area).contract(contract).organization(org).build());
            financialValuesRepository.save(FinancialValues.builder()
                    .month(6).year(2025).financialAmount(2000.0)
                    .financialType(revenueType).businessArea(area).contract(contract).organization(org).build());

            com.donatodev.bcm_backend.entity.Budget budget = budgetRepository.save(
                    com.donatodev.bcm_backend.entity.Budget.builder()
                            .businessArea(area).category(FinancialCategory.REVENUE).year(2025)
                            .targetAmount(10000.0).organization(org).build());

            mockMvc.perform(get("/budgets/{id}", budget.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.actualAmount").value(5000.0))
                    .andExpect(jsonPath("$.percentUsed").value(50.0));
        }

        @Test
        @Order(5)
        @DisplayName("The budget has been updated successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldUpdateBudget() throws Exception {
            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Legal").description("Legal dept").build());
            BusinessAreas newArea = businessAreasRepository.save(BusinessAreas.builder()
                    .name("HR").description("HR dept").build());
            com.donatodev.bcm_backend.entity.Budget original = budgetRepository.save(
                    com.donatodev.bcm_backend.entity.Budget.builder()
                            .businessArea(area).category(FinancialCategory.COST).year(2025).targetAmount(1000.0).build());

            BudgetDTO updatedDTO = new BudgetDTO(original.getId(), newArea.getId(), null, FinancialCategory.REVENUE, 2026, 9999.0, 0, 0);

            mockMvc.perform(put("/budgets/{id}", original.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updatedDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.areaName").value("HR"))
                    .andExpect(jsonPath("$.category").value("REVENUE"))
                    .andExpect(jsonPath("$.year").value(2026))
                    .andExpect(jsonPath("$.targetAmount").value(9999.0));
        }

        @Test
        @Order(6)
        @DisplayName("The budget has been deleted successfully")
        @WithMockUser(roles = "ADMIN")
        void shouldDeleteBudget() throws Exception {
            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Temp").description("Temp dept").build());
            com.donatodev.bcm_backend.entity.Budget toDelete = budgetRepository.save(
                    com.donatodev.bcm_backend.entity.Budget.builder()
                            .businessArea(area).category(FinancialCategory.COST).year(2025).targetAmount(1.0).build());

            mockMvc.perform(delete("/budgets/{id}", toDelete.getId()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @Order(7)
        @DisplayName("Budget not found when searching by invalid ID")
        @WithMockUser(roles = "ADMIN")
        void shouldReturnNotFoundForInvalidId() throws Exception {
            mockMvc.perform(get("/budgets/{id}", 9999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @Order(8)
        @DisplayName("Manager can read budgets but not create them")
        @WithMockUser(roles = "MANAGER")
        void managerCannotCreateBudget() throws Exception {
            BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                    .name("Restricted").description("Restricted dept").build());
            BudgetDTO dto = new BudgetDTO(null, area.getId(), null, FinancialCategory.COST, 2025, 100.0, 0, 0);

            mockMvc.perform(post("/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/budgets"))
                    .andExpect(status().isOk());
        }
    }
}
