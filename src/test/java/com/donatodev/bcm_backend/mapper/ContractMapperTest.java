package com.donatodev.bcm_backend.mapper;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractManagerRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.util.TestDataCleaner;

/**
 * Integration tests for {@link ContractMapper}.
 * <p>
 * This test class verifies the correctness of the conversion logic between
 * {@link Contracts} entities and {@link ContractDTO} objects, including various
 * edge cases such as missing business areas or managers.
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ContractMapperTest {

    @Autowired
    private ContractManagerRepository contractManagerRepository;

    @Autowired
    private ContractMapper contractMapper;

    @Autowired
    private BusinessAreasRepository businessAreasRepository;

    @Autowired
    private ManagersRepository managersRepository;

    @Autowired
    private ContractsRepository contractsRepository;

    @Autowired
    private TestDataCleaner cleaner;

    private BusinessAreas savedArea;
    private Managers savedManager;

    /**
     * Cleans the test database and sets up common test data before each test.
     */
    @SuppressWarnings("unused")
    @BeforeEach
    void setup() {
        cleaner.clean();

        contractManagerRepository.deleteAll();
        contractsRepository.deleteAll();
        businessAreasRepository.deleteAll();
        managersRepository.deleteAll();

        savedArea = businessAreasRepository.save(BusinessAreas.builder()
                .name("IT").build());

        savedManager = managersRepository.save(Managers.builder()
                .firstName("Donato").lastName("Dev").email("dev@mail.com")
                .department("IT").phoneNumber("123456789").build());
    }

    /**
     * Tests mapping a contract with null values for area and manager.
     */
    @Test
    void shouldMapToDTOWithNulls() {
        Contracts contract = Contracts.builder()
                .id(1L)
                .customerName("ACME")
                .contractNumber("CN-001")
                .wbsCode("WBS-001")
                .projectName("Progetto")
                .status(ContractStatus.ACTIVE)
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 12, 31))
                .build();

        ContractDTO dto = contractMapper.toDTO(contract);

        assertEquals(1L, dto.id());
        assertNull(dto.areaId());
        assertNull(dto.managerId());
        assertEquals("ACME", dto.customerName());
    }

    /**
     * Tests mapping a contract with valid area and manager set.
     */
    @Test
    void shouldMapToDTOWithValues() {
        Contracts contract = Contracts.builder()
                .id(2L)
                .customerName("Company")
                .contractNumber("CN-002")
                .wbsCode("WBS-002")
                .projectName("Project")
                .status(ContractStatus.EXPIRED)
                .startDate(LocalDate.of(2023, 3, 1))
                .endDate(LocalDate.of(2023, 9, 1))
                .businessArea(savedArea)
                .manager(savedManager)
                .build();

        ContractDTO dto = contractMapper.toDTO(contract);

        assertEquals(savedArea.getId(), dto.areaId());
        assertEquals(savedManager.getId(), dto.managerId());
    }

    /**
     * Tests mapping from DTO to entity when manager is provided.
     */
    @Test
    void shouldMapToEntityWithManager() {
        ContractDTO dto = new ContractDTO(
                3L, "Client", "CN-003", "WBS-003", "TestProject", ContractStatus.ACTIVE,
                LocalDate.now(), LocalDate.now().plusMonths(6),
                savedArea.getId(), savedManager.getId(), null, null);

        Contracts contract = contractMapper.toEntity(dto);

        assertEquals("Client", contract.getCustomerName());
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertEquals(savedArea.getId(), contract.getBusinessArea().getId());
        assertEquals(savedManager.getId(), contract.getManager().getId());
    }

    /**
     * Tests mapping from DTO to entity when manager is null.
     */
    @Test
    void shouldMapToEntityWithoutManager() {
        ContractDTO dto = new ContractDTO(
                4L, "Client2", "CN-004", "WBS-004", "SoloArea", ContractStatus.CANCELLED,
                LocalDate.now(), LocalDate.now().plusMonths(3),
                savedArea.getId(), null, null, null);

        Contracts contract = contractMapper.toEntity(dto);

        assertEquals("Client2", contract.getCustomerName());
        assertEquals(ContractStatus.CANCELLED, contract.getStatus());
        assertNotNull(contract.getBusinessArea());
        assertNull(contract.getManager());
    }

    /**
     * Tests exception when business area is not found.
     */
    @Test
    void shouldThrowIfAreaNotFound() {
        ContractDTO dto = new ContractDTO(
                5L, "Missing", "CN-005", "WBS-005", "Error", ContractStatus.CANCELLED,
                LocalDate.now(), LocalDate.now(), 999L, null, null, null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> contractMapper.toEntity(dto));
        assertEquals("Business Area not found", ex.getMessage());
    }

    /**
     * Tests exception when manager is not found.
     */
    @Test
    void shouldThrowIfManagerNotFound() {
        ContractDTO dto = new ContractDTO(
                6L, "Missing", "CN-006", "WBS-006", "Errore", ContractStatus.EXPIRED,
                LocalDate.now(), LocalDate.now(), savedArea.getId(), 888L, null, null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> contractMapper.toEntity(dto));
        assertEquals("Manager not found", ex.getMessage());
    }

    /**
     * Tests that toDTO returns null when given a null contract.
     */
    @Test
    void shouldReturnNullWhenToDTOGivenNull() {
        ContractDTO dto = contractMapper.toDTO(null);
        assertNull(dto);
    }

    /**
     * Tests that toEntity returns null when given a null DTO.
     */
    @Test
    void shouldReturnNullWhenToEntityGivenNull() {
        Contracts contract = contractMapper.toEntity(null);
        assertNull(contract);
    }

    /**
     * Tests mapping from DTO to entity when both area and manager are null.
     */
    @Test
    void shouldMapToEntityWithNullAreaAndManager() {
        ContractDTO dto = new ContractDTO(
                7L, "ClientNoArea", "CN-007", "WBS-007", "NoAreaProject",
                ContractStatus.ACTIVE,
                LocalDate.now(), LocalDate.now().plusMonths(2),
                null, // No area ID
                null, // No manager ID
                null, // No manager nested
                null); // No area nested

        Contracts contract = contractMapper.toEntity(dto);

        assertEquals("ClientNoArea", contract.getCustomerName());
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertNull(contract.getBusinessArea());  // Should be null
        assertNull(contract.getManager());       // Should be null
    }
}
