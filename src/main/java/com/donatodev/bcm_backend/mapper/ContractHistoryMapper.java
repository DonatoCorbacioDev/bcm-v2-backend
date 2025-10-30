package com.donatodev.bcm_backend.mapper;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.ContractHistoryDTO;
import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Mapper class responsible for converting between {@link ContractHistory} entities
 * and {@link ContractHistoryDTO} data transfer objects.
 * <p>
 * Used to decouple the data access layer from the API layer, ensuring clean
 * and controlled data transformations for contract history records.
 */
@Component
public class ContractHistoryMapper {

	private final ContractsRepository contractsRepository;
    private final UsersRepository usersRepository;

    public ContractHistoryMapper(ContractsRepository contractsRepository, UsersRepository usersRepository) {
        this.contractsRepository = contractsRepository;
        this.usersRepository = usersRepository;
    }

    /**
     * Converts a {@link ContractHistory} entity to a {@link ContractHistoryDTO}.
     *
     * @param history the contract history entity
     * @return the corresponding DTO
     */
    public ContractHistoryDTO toDTO(ContractHistory history) {
        return new ContractHistoryDTO(
                history.getId(),
                history.getContract().getId(),
                history.getModifiedBy().getId(),
                history.getModificationDate(),
                history.getPreviousStatus(),
                history.getNewStatus()
        );
    }

    /**
     * Converts a {@link ContractHistoryDTO} to a {@link ContractHistory} entity.
     * <p>
     * This method also retrieves related entities (contract, user) from their repositories.
     *
     * @param dto the DTO to convert
     * @return the corresponding entity
     * @throws RuntimeException if the referenced contract or user is not found
     */
    public ContractHistory toEntity(ContractHistoryDTO dto) {
        return ContractHistory.builder()
                .id(dto.id())
                .contract(contractsRepository.findById(dto.contractId())
                        .orElseThrow(() -> new ContractNotFoundException("Contract not found")))
                .modifiedBy(usersRepository.findById(dto.modifiedById())
                        .orElseThrow(() -> new UserNotFoundException("User not found")))
                .modificationDate(dto.modificationDate() != null ? dto.modificationDate() : java.time.LocalDateTime.now())
                .previousStatus(dto.previousStatus())
                .newStatus(dto.newStatus())
                .build();
    }
}
