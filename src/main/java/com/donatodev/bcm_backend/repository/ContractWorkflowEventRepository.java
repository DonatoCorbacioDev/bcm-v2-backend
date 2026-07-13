package com.donatodev.bcm_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.ContractWorkflowEvent;

/**
 * Repository interface for accessing {@link ContractWorkflowEvent} entities.
 */
@Repository
public interface ContractWorkflowEventRepository extends JpaRepository<ContractWorkflowEvent, Long> {

    /**
     * Retrieves all workflow events for a contract, oldest first.
     *
     * @param contractId the contract ID
     * @return the contract's workflow events in chronological order
     */
    List<ContractWorkflowEvent> findByContractIdOrderByCreatedAtAsc(Long contractId);
}
