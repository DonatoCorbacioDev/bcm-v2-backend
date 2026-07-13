package com.donatodev.bcm_backend.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.dto.ContractWorkflowEventDTO;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.ContractWorkflowEvent;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.entity.WorkflowAction;
import com.donatodev.bcm_backend.entity.WorkflowStage;
import com.donatodev.bcm_backend.repository.ContractWorkflowEventRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;

/**
 * Drives a contract's approval workflow: DRAFT -&gt; IN_REVIEW -&gt; APPROVED
 * (which also flips {@link ContractStatus} from DRAFT to ACTIVE). A reject at
 * any stage sends the contract back to DRAFT with a required comment — there
 * is deliberately no multi-level state machine for rejections.
 */
@Service
public class ContractWorkflowService {

    private static final String ROLE_ADMIN = "ADMIN";

    private final ContractAccessGuard contractAccessGuard;
    private final CurrentUserResolver currentUserResolver;
    private final ContractWorkflowEventRepository eventRepository;
    private final ContractsRepository contractsRepository;
    private final AgentNotificationService agentNotificationService;

    public ContractWorkflowService(
            ContractAccessGuard contractAccessGuard,
            CurrentUserResolver currentUserResolver,
            ContractWorkflowEventRepository eventRepository,
            ContractsRepository contractsRepository,
            AgentNotificationService agentNotificationService) {
        this.contractAccessGuard = contractAccessGuard;
        this.currentUserResolver = currentUserResolver;
        this.eventRepository = eventRepository;
        this.contractsRepository = contractsRepository;
        this.agentNotificationService = agentNotificationService;
    }

    /**
     * Submits a DRAFT contract for review. Allowed for the contract's own
     * manager or an ADMIN (same ownership check used for editing).
     */
    public void submitForReview(Long contractId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        requireStage(contract, WorkflowStage.DRAFT, "Contract must be in DRAFT to submit for review");

        Users actor = currentUserResolver.resolve();
        recordAndApply(contract, WorkflowStage.IN_REVIEW, WorkflowAction.SUBMIT, actor, null);
        agentNotificationService.notifySubmittedForReview(contract);
    }

    /**
     * Approves a contract IN_REVIEW: moves the workflow stage to APPROVED
     * and the contract status from DRAFT to ACTIVE in the same update.
     */
    public void approve(Long contractId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        Users actor = requireApprover();
        requireStage(contract, WorkflowStage.IN_REVIEW, "Contract must be under review to approve");

        contract.setStatus(ContractStatus.ACTIVE);
        recordAndApply(contract, WorkflowStage.APPROVED, WorkflowAction.APPROVE, actor, null);
        agentNotificationService.notifyWorkflowApproved(contract);
    }

    /**
     * Rejects a contract IN_REVIEW, sending it back to DRAFT with a
     * mandatory comment explaining why.
     */
    public void reject(Long contractId, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("A comment is required to reject a contract");
        }
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        Users actor = requireApprover();
        requireStage(contract, WorkflowStage.IN_REVIEW, "Contract must be under review to reject");

        recordAndApply(contract, WorkflowStage.DRAFT, WorkflowAction.REJECT, actor, comment);
        agentNotificationService.notifyWorkflowRejected(contract, comment);
    }

    /**
     * Returns the full workflow history for a contract, oldest first.
     */
    public List<ContractWorkflowEventDTO> getEvents(Long contractId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        // Approvers legitimately review contracts they don't own, so the
        // ordinary manager-ownership check only applies to plain managers.
        Users actor = currentUserResolver.resolve();
        boolean isAdmin = ROLE_ADMIN.equals(actor.getRole().getRole());
        if (!isAdmin && !actor.isCanApproveContracts()) {
            contractAccessGuard.checkManagerCanAccess(contract);
        }
        return eventRepository.findByContractIdOrderByCreatedAtAsc(contractId).stream()
                .map(this::toDTO)
                .toList();
    }

    private void requireStage(Contracts contract, WorkflowStage expected, String message) {
        if (contract.getWorkflowStage() != expected) {
            throw new IllegalArgumentException(message);
        }
    }

    private Users requireApprover() {
        Users actor = currentUserResolver.resolve();
        boolean isAdmin = ROLE_ADMIN.equals(actor.getRole().getRole());
        if (!isAdmin && !actor.isCanApproveContracts()) {
            throw new AccessDeniedException("Not authorized to approve or reject contracts");
        }
        return actor;
    }

    private void recordAndApply(Contracts contract, WorkflowStage toStage, WorkflowAction action,
            Users actor, String comment) {
        WorkflowStage fromStage = contract.getWorkflowStage();
        contract.setWorkflowStage(toStage);
        contractsRepository.save(contract);

        ContractWorkflowEvent event = ContractWorkflowEvent.builder()
                .contract(contract)
                .fromStage(fromStage)
                .toStage(toStage)
                .action(action)
                .actor(actor)
                .comment(comment)
                .build();
        eventRepository.save(event);
    }

    private ContractWorkflowEventDTO toDTO(ContractWorkflowEvent event) {
        return new ContractWorkflowEventDTO(
                event.getId(),
                event.getFromStage(),
                event.getToStage(),
                event.getAction(),
                event.getActor().getUsername(),
                event.getComment(),
                event.getCreatedAt()
        );
    }
}
