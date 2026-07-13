package com.donatodev.bcm_backend.controller;

import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.entity.WorkflowStage;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.TestDataCleaner;

/**
 * Integration tests for {@link ContractWorkflowController}
 * (submit / approve / reject / events under {@code /contracts/{id}/workflow}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContractWorkflowControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ContractsRepository contractsRepository;
    @Autowired private ManagersRepository managersRepository;
    @Autowired private BusinessAreasRepository businessAreasRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private RolesRepository rolesRepository;
    @Autowired private TestDataCleaner testDataCleaner;

    @BeforeEach
    void cleanDb() {
        testDataCleaner.clean();
    }

    private Managers createManager(String email) {
        return managersRepository.save(Managers.builder()
                .firstName("Mario").lastName("Rossi").email(email)
                .phoneNumber("123456").department("Legal").build());
    }

    private void createUser(String username, String roleName, Managers manager, boolean canApprove) {
        Roles role = rolesRepository.findByRole(roleName).orElseGet(() -> rolesRepository.save(Roles.builder().role(roleName).build()));
        usersRepository.save(Users.builder()
                .username(username).passwordHash("password").verified(true)
                .role(role).manager(manager).canApproveContracts(canApprove)
                .build());
    }

    private Contracts createDraftContract(Managers manager) {
        BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder().name("WF-Area-" + System.nanoTime()).description("d").build());
        return contractsRepository.save(Contracts.builder()
                .customerName("Client WF").contractNumber("WF-" + System.nanoTime())
                .status(ContractStatus.DRAFT).workflowStage(WorkflowStage.DRAFT)
                .startDate(LocalDate.of(2025, Month.JANUARY, 1)).endDate(LocalDate.of(2026, Month.JANUARY, 1))
                .businessArea(area).manager(manager)
                .build());
    }

    @Test
    @DisplayName("Manager submits own DRAFT contract for review")
    @WithMockUser(username = "manager1", roles = "MANAGER")
    void submitForReview_ownContract_succeeds() throws Exception {
        Managers manager = createManager("manager1.wf@example.com");
        createUser("manager1", "MANAGER", manager, false);
        Contracts contract = createDraftContract(manager);

        mockMvc.perform(post("/contracts/" + contract.getId() + "/workflow/submit").with(csrf()))
                .andExpect(status().isNoContent());

        Contracts updated = contractsRepository.findById(contract.getId()).orElseThrow();
        assertEquals(WorkflowStage.IN_REVIEW, updated.getWorkflowStage());
    }

    // Cross-manager denial (checkManagerCanAccess throwing AccessDeniedException) is
    // covered by ContractAccessGuardTest and by ContractWorkflowServiceTest verifying the
    // guard is consulted — not re-tested here: @WithMockUser bypasses JwtAuthenticationFilter,
    // so TenantContext is never populated in this MockMvc setup and the guard's org-scoped
    // check would short-circuit as a no-op regardless of manager mismatch.

    @Test
    @DisplayName("Approver approves a contract IN_REVIEW, activating it")
    @WithMockUser(username = "approver1", roles = "MANAGER")
    void approve_asApprover_activatesContract() throws Exception {
        Managers manager = createManager("manager3.wf@example.com");
        createUser("submitter3", "MANAGER", manager, false);
        Managers approverManager = createManager("approver1.wf@example.com");
        createUser("approver1", "MANAGER", approverManager, true);
        Contracts contract = createDraftContract(manager);
        contract.setWorkflowStage(WorkflowStage.IN_REVIEW);
        contractsRepository.save(contract);

        mockMvc.perform(post("/contracts/" + contract.getId() + "/workflow/approve").with(csrf()))
                .andExpect(status().isNoContent());

        Contracts updated = contractsRepository.findById(contract.getId()).orElseThrow();
        assertEquals(WorkflowStage.APPROVED, updated.getWorkflowStage());
        assertEquals(ContractStatus.ACTIVE, updated.getStatus());
    }

    @Test
    @DisplayName("A manager without the approval permission cannot approve")
    @WithMockUser(username = "plainmanager", roles = "MANAGER")
    void approve_withoutPermission_forbidden() throws Exception {
        Managers manager = createManager("manager4.wf@example.com");
        createUser("plainmanager", "MANAGER", manager, false);
        Contracts contract = createDraftContract(manager);
        contract.setWorkflowStage(WorkflowStage.IN_REVIEW);
        contractsRepository.save(contract);

        mockMvc.perform(post("/contracts/" + contract.getId() + "/workflow/approve").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Approver rejects a contract IN_REVIEW with a comment, sending it back to DRAFT")
    @WithMockUser(username = "approver2", roles = "MANAGER")
    void reject_withComment_returnsToDraft() throws Exception {
        Managers manager = createManager("manager5.wf@example.com");
        createUser("submitter5", "MANAGER", manager, false);
        Managers approverManager = createManager("approver2.wf@example.com");
        createUser("approver2", "MANAGER", approverManager, true);
        Contracts contract = createDraftContract(manager);
        contract.setWorkflowStage(WorkflowStage.IN_REVIEW);
        contractsRepository.save(contract);

        mockMvc.perform(post("/contracts/" + contract.getId() + "/workflow/reject")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Manca l'allegato\"}"))
                .andExpect(status().isNoContent());

        Contracts updated = contractsRepository.findById(contract.getId()).orElseThrow();
        assertEquals(WorkflowStage.DRAFT, updated.getWorkflowStage());
    }

    @Test
    @DisplayName("Rejecting without a comment fails validation")
    @WithMockUser(username = "approver3", roles = "MANAGER")
    void reject_withoutComment_badRequest() throws Exception {
        Managers manager = createManager("manager6.wf@example.com");
        createUser("submitter6", "MANAGER", manager, false);
        Managers approverManager = createManager("approver3.wf@example.com");
        createUser("approver3", "MANAGER", approverManager, true);
        Contracts contract = createDraftContract(manager);
        contract.setWorkflowStage(WorkflowStage.IN_REVIEW);
        contractsRepository.save(contract);

        mockMvc.perform(post("/contracts/" + contract.getId() + "/workflow/reject")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Returns the workflow event history for a contract")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getEvents_returnsHistory() throws Exception {
        Managers manager = createManager("manager7.wf@example.com");
        createUser("admin", "ADMIN", null, false);
        Contracts contract = createDraftContract(manager);

        mockMvc.perform(post("/contracts/" + contract.getId() + "/workflow/submit").with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/contracts/" + contract.getId() + "/workflow/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("SUBMIT"))
                .andExpect(jsonPath("$[0].toStage").value("IN_REVIEW"));
    }
}
