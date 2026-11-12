package com.donatodev.bcm_backend.service;

import java.util.UUID;
import static com.donatodev.bcm_backend.support.TestIds.uid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.donatodev.bcm_backend.dto.ContractWorkflowEventDTO;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.ContractWorkflowEvent;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.entity.WorkflowAction;
import com.donatodev.bcm_backend.entity.WorkflowStage;
import com.donatodev.bcm_backend.repository.ContractWorkflowEventRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;

/**
 * Unit tests for {@link ContractWorkflowService}.
 */
@ExtendWith(MockitoExtension.class)
class ContractWorkflowServiceTest {

    @Mock private ContractAccessGuard contractAccessGuard;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private ContractWorkflowEventRepository eventRepository;
    @Mock private ContractsRepository contractsRepository;
    @Mock private AgentNotificationService agentNotificationService;

    @InjectMocks
    private ContractWorkflowService workflowService;

    private static final UUID CONTRACT_ID = uid(1);

    private Contracts contractWithStage(WorkflowStage stage) {
        Contracts c = new Contracts();
        c.setId(CONTRACT_ID);
        c.setStatus(ContractStatus.DRAFT);
        c.setWorkflowStage(stage);
        return c;
    }

    private Users userWithRole(String roleName, boolean canApprove) {
        Users u = new Users();
        u.setId(uid(2));
        u.setUsername("actor");
        u.setRole(Roles.builder().role(roleName).build());
        u.setCanApproveContracts(canApprove);
        return u;
    }

    @Nested
    @DisplayName("submitForReview")
    class SubmitForReview {

        @Test
        @DisplayName("moves a DRAFT contract to IN_REVIEW and notifies approvers")
        void happyPath() {
            Contracts contract = contractWithStage(WorkflowStage.DRAFT);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            Users actor = userWithRole("MANAGER", false);
            when(currentUserResolver.resolve()).thenReturn(actor);

            workflowService.submitForReview(CONTRACT_ID);

            assertEquals(WorkflowStage.IN_REVIEW, contract.getWorkflowStage());
            verify(contractAccessGuard).checkManagerCanAccess(contract);
            ArgumentCaptor<ContractWorkflowEvent> captor = ArgumentCaptor.forClass(ContractWorkflowEvent.class);
            verify(eventRepository).save(captor.capture());
            assertEquals(WorkflowStage.DRAFT, captor.getValue().getFromStage());
            assertEquals(WorkflowStage.IN_REVIEW, captor.getValue().getToStage());
            assertEquals(WorkflowAction.SUBMIT, captor.getValue().getAction());
            verify(agentNotificationService).notifySubmittedForReview(contract);
        }

        @Test
        @DisplayName("rejects submitting a contract that isn't DRAFT")
        void wrongStage() {
            Contracts contract = contractWithStage(WorkflowStage.IN_REVIEW);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);

            assertThrows(IllegalArgumentException.class, () -> workflowService.submitForReview(CONTRACT_ID));
            verify(eventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("four eyes: an approver may not decide on their own contract")
    class FourEyes {

        private Users approverWithProfile() {
            Users u = userWithRole("MANAGER", true);
            u.setManager(com.donatodev.bcm_backend.entity.Managers.builder().id(uid(20)).build());
            return u;
        }

        private ContractWorkflowEvent submittedBy(Users who) {
            return ContractWorkflowEvent.builder().action(WorkflowAction.SUBMIT).actor(who).build();
        }

        @Test
        @DisplayName("a contract assigned to the approver: neither approved nor rejected")
        void notOnTheirAssignedContract() {
            Users approver = approverWithProfile();
            Contracts contract = contractWithStage(WorkflowStage.IN_REVIEW);
            contract.setManager(approver.getManager());
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(currentUserResolver.resolve()).thenReturn(approver);

            AccessDeniedException denied = assertThrows(AccessDeniedException.class, () -> workflowService.approve(CONTRACT_ID));
            assertEquals(ContractWorkflowService.SELF_APPROVAL, denied.getMessage());
            assertThrows(AccessDeniedException.class, () -> workflowService.reject(CONTRACT_ID, "no"));
            assertEquals(WorkflowStage.IN_REVIEW, contract.getWorkflowStage());
            verify(eventRepository, never()).save(any());
        }

        @Test
        @DisplayName("a contract the approver submitted, even someone else's: refused")
        void notOnWhatTheySubmitted() {
            Users approver = approverWithProfile();
            Contracts contract = contractWithStage(WorkflowStage.IN_REVIEW);
            contract.setManager(com.donatodev.bcm_backend.entity.Managers.builder().id(uid(21)).build());
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(currentUserResolver.resolve()).thenReturn(approver);
            Users colleague = userWithRole("MANAGER", false);
            colleague.setId(uid(30));
            when(eventRepository.findByContractIdOrderByCreatedAtAsc(CONTRACT_ID))
                    .thenReturn(java.util.List.of(submittedBy(colleague), submittedBy(approver)));

            assertThrows(AccessDeniedException.class, () -> workflowService.approve(CONTRACT_ID));
            assertEquals(ContractStatus.DRAFT, contract.getStatus());
        }

        @Test
        @DisplayName("a colleague's contract submitted by the colleague: the approver decides")
        void aSecondPersonDecides() {
            Users approver = approverWithProfile();
            Contracts contract = contractWithStage(WorkflowStage.IN_REVIEW);
            contract.setManager(com.donatodev.bcm_backend.entity.Managers.builder().id(uid(21)).build());
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(currentUserResolver.resolve()).thenReturn(approver);
            Users colleague = userWithRole("MANAGER", false);
            colleague.setId(uid(30));
            when(eventRepository.findByContractIdOrderByCreatedAtAsc(CONTRACT_ID)).thenReturn(java.util.List.of(submittedBy(colleague)));

            workflowService.approve(CONTRACT_ID);

            assertEquals(WorkflowStage.APPROVED, contract.getWorkflowStage());
        }

        @Test
        @DisplayName("an ADMIN may approve what they submitted: a one-admin organization must still approve")
        void adminMay() {
            Users admin = userWithRole("ADMIN", false);
            Contracts contract = contractWithStage(WorkflowStage.IN_REVIEW);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(currentUserResolver.resolve()).thenReturn(admin);

            workflowService.approve(CONTRACT_ID);

            assertEquals(WorkflowStage.APPROVED, contract.getWorkflowStage());
        }
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("moves IN_REVIEW to APPROVED and activates the contract")
        void happyPath() {
            Contracts contract = contractWithStage(WorkflowStage.IN_REVIEW);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(currentUserResolver.resolve()).thenReturn(userWithRole("MANAGER", true));

            workflowService.approve(CONTRACT_ID);

            assertEquals(WorkflowStage.APPROVED, contract.getWorkflowStage());
            assertEquals(ContractStatus.ACTIVE, contract.getStatus());
            verify(agentNotificationService).notifyWorkflowApproved(contract);
        }

        @Test
        @DisplayName("allows an ADMIN to approve even without the approval flag")
        void adminBypassesPermission() {
            Contracts contract = contractWithStage(WorkflowStage.IN_REVIEW);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(currentUserResolver.resolve()).thenReturn(userWithRole("ADMIN", false));

            workflowService.approve(CONTRACT_ID);

            assertEquals(WorkflowStage.APPROVED, contract.getWorkflowStage());
        }

        @Test
        @DisplayName("rejects approval from a manager without the approval permission")
        void noPermission() {
            Contracts contract = contractWithStage(WorkflowStage.IN_REVIEW);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(currentUserResolver.resolve()).thenReturn(userWithRole("MANAGER", false));

            assertThrows(AccessDeniedException.class, () -> workflowService.approve(CONTRACT_ID));
            verify(eventRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects approving a contract that isn't IN_REVIEW")
        void wrongStage() {
            Contracts contract = contractWithStage(WorkflowStage.DRAFT);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(currentUserResolver.resolve()).thenReturn(userWithRole("ADMIN", false));

            assertThrows(IllegalArgumentException.class, () -> workflowService.approve(CONTRACT_ID));
        }
    }

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        @DisplayName("sends an IN_REVIEW contract back to DRAFT with the comment")
        void happyPath() {
            Contracts contract = contractWithStage(WorkflowStage.IN_REVIEW);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(currentUserResolver.resolve()).thenReturn(userWithRole("ADMIN", false));

            workflowService.reject(CONTRACT_ID, "Manca l'allegato");

            assertEquals(WorkflowStage.DRAFT, contract.getWorkflowStage());
            assertEquals(ContractStatus.DRAFT, contract.getStatus());
            ArgumentCaptor<ContractWorkflowEvent> captor = ArgumentCaptor.forClass(ContractWorkflowEvent.class);
            verify(eventRepository).save(captor.capture());
            assertEquals("Manca l'allegato", captor.getValue().getComment());
            verify(agentNotificationService).notifyWorkflowRejected(contract, "Manca l'allegato");
        }

        @Test
        @DisplayName("requires a non-blank comment")
        void blankComment() {
            assertThrows(IllegalArgumentException.class, () -> workflowService.reject(CONTRACT_ID, "  "));
            assertThrows(IllegalArgumentException.class, () -> workflowService.reject(CONTRACT_ID, null));
            verify(contractAccessGuard, never()).getContractInScope(any());
        }

        @Test
        @DisplayName("rejects rejection from a manager without the approval permission")
        void noPermission() {
            Contracts contract = contractWithStage(WorkflowStage.IN_REVIEW);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(currentUserResolver.resolve()).thenReturn(userWithRole("MANAGER", false));

            assertThrows(AccessDeniedException.class, () -> workflowService.reject(CONTRACT_ID, "no"));
        }

        @Test
        @DisplayName("rejects rejecting a contract that isn't IN_REVIEW")
        void wrongStage() {
            Contracts contract = contractWithStage(WorkflowStage.APPROVED);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(currentUserResolver.resolve()).thenReturn(userWithRole("ADMIN", false));

            assertThrows(IllegalArgumentException.class, () -> workflowService.reject(CONTRACT_ID, "no"));
        }
    }

    @Nested
    @DisplayName("getEvents")
    class GetEvents {

        @Test
        @DisplayName("maps events to DTOs in chronological order")
        void mapsEvents() {
            Contracts contract = contractWithStage(WorkflowStage.IN_REVIEW);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(currentUserResolver.resolve()).thenReturn(userWithRole("ADMIN", false));

            Users actor = userWithRole("MANAGER", false);
            actor.setUsername("mario.rossi");
            ContractWorkflowEvent event = ContractWorkflowEvent.builder()
                    .id(uid(5)).contract(contract).fromStage(WorkflowStage.DRAFT).toStage(WorkflowStage.IN_REVIEW)
                    .action(WorkflowAction.SUBMIT).actor(actor).build();
            when(eventRepository.findByContractIdOrderByCreatedAtAsc(CONTRACT_ID)).thenReturn(java.util.List.of(event));

            var result = workflowService.getEvents(CONTRACT_ID);

            assertEquals(1, result.size());
            ContractWorkflowEventDTO dto = result.get(0);
            assertEquals(uid(5), dto.id());
            assertEquals("mario.rossi", dto.actorUsername());
            assertEquals(WorkflowAction.SUBMIT, dto.action());
        }

        @Test
        @DisplayName("enforces manager-ownership only for plain managers, not admins or approvers")
        void permissionScoping() {
            Contracts contract = contractWithStage(WorkflowStage.IN_REVIEW);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(eventRepository.findByContractIdOrderByCreatedAtAsc(CONTRACT_ID)).thenReturn(java.util.List.of());

            when(currentUserResolver.resolve()).thenReturn(userWithRole("ADMIN", false));
            workflowService.getEvents(CONTRACT_ID);
            verify(contractAccessGuard, never()).checkManagerCanAccess(any());

            when(currentUserResolver.resolve()).thenReturn(userWithRole("MANAGER", true));
            workflowService.getEvents(CONTRACT_ID);
            verify(contractAccessGuard, never()).checkManagerCanAccess(any());

            when(currentUserResolver.resolve()).thenReturn(userWithRole("MANAGER", false));
            workflowService.getEvents(CONTRACT_ID);
            verify(contractAccessGuard, times(1)).checkManagerCanAccess(contract);
        }
    }
}
