package com.donatodev.bcm_backend.mapper;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.BusinessAreaDTO;
import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.dto.ManagerDTO;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;

/**
 * Mapper responsible for converting between {@link Contracts} entities and
 * {@link ContractDTO} objects.
 * <p>
 * This component helps isolate persistence logic from the API layer and
 * centralizes the transformation logic for contract data.
 */
@Component
public class ContractMapper {

    private final BusinessAreasRepository businessAreasRepository;
    private final ManagersRepository managersRepository;

    public ContractMapper(BusinessAreasRepository businessAreasRepository,
            ManagersRepository managersRepository) {
        this.businessAreasRepository = businessAreasRepository;
        this.managersRepository = managersRepository;
    }

    /**
     * Converts a {@link Contracts} entity into a {@link ContractDTO}. Includes
     * nested manager and business area DTOs for complete representation.
     *
     * @param contract the contract entity
     * @return the corresponding DTO with nested objects
     */
    public ContractDTO toDTO(Contracts contract) {
        if (contract == null) {
            return null;
        }

        // Convert nested entities to DTOs
        ManagerDTO managerDTO = contract.getManager() != null
                ? new ManagerDTO(
                        contract.getManager().getId(),
                        contract.getManager().getFirstName(),
                        contract.getManager().getLastName(),
                        contract.getManager().getEmail(),
                        contract.getManager().getPhoneNumber(),
                        contract.getManager().getDepartment()
                )
                : null;

        BusinessAreaDTO areaDTO = contract.getBusinessArea() != null
                ? new BusinessAreaDTO(
                        contract.getBusinessArea().getId(),
                        contract.getBusinessArea().getName(),
                        contract.getBusinessArea().getDescription()
                )
                : null;

        // Calculate days until expiry (only for ACTIVE contracts)
        Integer daysUntilExpiry = null;
        if (contract.getStatus() == ContractStatus.ACTIVE && contract.getEndDate() != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), contract.getEndDate());
            daysUntilExpiry = (int) days;
        }

        return new ContractDTO(
                contract.getId(),
                contract.getCustomerName(),
                contract.getContractNumber(),
                contract.getWbsCode(),
                contract.getProjectName(),
                contract.getStatus(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getBusinessArea() != null ? contract.getBusinessArea().getId() : null,
                contract.getManager() != null ? contract.getManager().getId() : null,
                contract.getManager() != null ? contract.getManager().getFirstName() + " " + contract.getManager().getLastName() : null,
                managerDTO,
                areaDTO,
                daysUntilExpiry
        );
    }

    /**
     * Converts a {@link ContractDTO} into a {@link Contracts} entity.
     * <p>
     * Related entities such as business area and manager are resolved using
     * repositories.
     *
     * @param dto the DTO to convert
     * @return the corresponding entity
     * @throws RuntimeException if referenced business area or manager is not
     * found
     */
    public Contracts toEntity(ContractDTO dto) {
        if (dto == null) {
            return null;
        }

        return Contracts.builder()
                .id(dto.id())
                .customerName(dto.customerName())
                .contractNumber(dto.contractNumber())
                .wbsCode(dto.wbsCode())
                .projectName(dto.projectName())
                .status(dto.status())
                .startDate(dto.startDate())
                .endDate(dto.endDate())
                .businessArea(dto.areaId() != null
                        ? businessAreasRepository.findById(dto.areaId())
                                .orElseThrow(() -> new RuntimeException("Business Area not found"))
                        : null)
                .manager(resolveManager(dto.managerId()))
                .build();
    }

    /**
     * Resolves the manager entity based on the given ID. Returns {@code null}
     * if the ID is {@code null}.
     */
    private Managers resolveManager(Long managerId) {
        if (managerId == null) {
            return null;
        }
        return managersRepository.findById(managerId)
                .orElseThrow(() -> new RuntimeException("Manager not found"));
    }
}
