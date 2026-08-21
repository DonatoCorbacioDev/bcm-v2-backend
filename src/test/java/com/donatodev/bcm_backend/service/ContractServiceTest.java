package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.dto.ContractStatsResponse;
import com.donatodev.bcm_backend.dto.ContractsByAreaDTO;
import com.donatodev.bcm_backend.dto.ContractsTimelineDTO;
import com.donatodev.bcm_backend.dto.TopManagerDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.BusinessAreaNotFoundException;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.ManagerNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.ContractMapper;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractHistoryRepository;
import com.donatodev.bcm_backend.repository.ContractManagerRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Unit tests for {@link ContractService}.
 * <p>
 * Verifies the business logic related to contract management including:
 * <ul>
 * <li>Retrieving all contracts (for ADMIN and MANAGER)</li>
 * <li>Fetching contract by ID</li>
 * <li>Creating, updating and deleting contracts</li>
 * <li>Handling authentication and user-role filtering</li>
 * <li>Throwing appropriate exceptions for edge cases</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ContractServiceTest {

    @Mock
    private ContractsRepository contractsRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private ContractMapper contractMapper;

    @Mock
    private ContractManagerRepository contractManagerRepository;

    @Mock
    private ManagerService managerService;

    @Mock
    private ContractHistoryRepository contractHistoryRepository;

    @Mock
    private BusinessAreasRepository businessAreasRepository;

    @InjectMocks
    private ContractService contractService;

    @AfterEach
    @SuppressWarnings("unused")
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthentication(String username, String role) {
        User user = new User(username, "password", List.of(() -> "ROLE_" + role));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: ContractService")
    @SuppressWarnings("unused")
    class VerifyContractService {

        /**
         * Tests that ADMIN can retrieve all contracts from the repository and
         * that the returned list contains the expected DTO data.
         */
        @Test
        @Order(1)
        @DisplayName("Get all contracts as ADMIN")
        void shouldGetAllContractsAsAdmin() {
            Users admin = Users.builder()
                    .id(1L)
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            ContractDTO dto = new ContractDTO(1L, "Cliente", "CONTR123", "WBS001", "Progetto A",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 2L, 3L, null, null, null, null);
            Contracts entity = Contracts.builder().id(1L).customerName("Cliente").build();

            mockAuthentication("admin", "ADMIN");

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findAll()).thenReturn(List.of(entity));
            when(contractMapper.toDTO(entity)).thenReturn(dto);

            List<ContractDTO> result = contractService.getAllContracts();

            assertEquals(1, result.size());
            assertEquals("Cliente", result.get(0).customerName());
        }

        /**
         * Tests retrieving a contract by its ID returns the corresponding DTO (ADMIN).
         */
        @Test
        @Order(2)
        @DisplayName("Get contract by ID returns DTO")
        void shouldGetContractById() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();
            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

            Contracts contract = Contracts.builder().id(1L).contractNumber("ABC123").customerName("Mario").build();
            ContractDTO dto = new ContractDTO(1L, "Mario", "ABC123", "WBS001", "Progetto",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(10), 1L, 1L, null, null, null, null);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            ContractDTO result = contractService.getContractById(1L);

            assertEquals("Mario", result.customerName());
        }

        @Test
        @Order(2)
        @DisplayName("Get contract by ID as MANAGER returns DTO for assigned contract")
        void shouldGetContractByIdAsManager() {
            Managers manager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder()
                    .username("manager1")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();
            mockAuthentication("manager1", "MANAGER");
            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(managerUser));

            Contracts contract = Contracts.builder().id(1L).customerName("Mario").manager(manager).build();
            ContractDTO dto = new ContractDTO(1L, "Mario", "ABC123", "WBS001", "Progetto",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(10), 1L, 5L, null, null, null, null);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            ContractDTO result = contractService.getContractById(1L);

            assertEquals("Mario", result.customerName());
        }

        @Test
        @Order(2)
        @DisplayName("Get contract by ID as MANAGER throws AccessDeniedException for unassigned contract")
        void shouldDenyGetContractByIdForUnassignedManager() {
            Managers myManager = Managers.builder().id(5L).build();
            Managers otherManager = Managers.builder().id(99L).build();
            Users managerUser = Users.builder()
                    .username("manager1")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(myManager)
                    .build();
            mockAuthentication("manager1", "MANAGER");
            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(managerUser));

            Contracts contract = Contracts.builder().id(1L).customerName("Mario").manager(otherManager).build();
            when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));

            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> contractService.getContractById(1L));
        }

        @Test
        @Order(2)
        @DisplayName("Get contract by ID as MANAGER with no manager profile throws AccessDeniedException")
        void shouldDenyGetContractByIdForManagerWithNoProfile() {
            Users managerUser = Users.builder()
                    .username("manager1")
                    .role(Roles.builder().role("MANAGER").build())
                    .build();
            mockAuthentication("manager1", "MANAGER");
            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(managerUser));

            Contracts contract = Contracts.builder().id(1L).customerName("Mario").build();
            when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));

            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> contractService.getContractById(1L));
        }

        @Test
        @Order(2)
        @DisplayName("Get contract by ID as MANAGER throws AccessDeniedException when the contract has no manager assigned")
        void shouldDenyGetContractByIdWhenContractHasNoManager() {
            Managers myManager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder()
                    .username("manager1")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(myManager)
                    .build();
            mockAuthentication("manager1", "MANAGER");
            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(managerUser));

            Contracts contract = Contracts.builder().id(1L).customerName("Mario").build();
            when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));

            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> contractService.getContractById(1L));
        }

        /**
         * Tests that ContractNotFoundException is thrown when a contract with
         * the given ID does not exist in the repository.
         */
        @Test
        @Order(3)
        @DisplayName("Get contract by ID throws exception if not found")
        void shouldThrowWhenContractNotFound() {
            when(contractsRepository.findById(999L)).thenReturn(Optional.empty());
            ContractNotFoundException ex
                    = assertThrows(ContractNotFoundException.class, () -> contractService.getContractById(999L));
            assertEquals("Contratto ID 999 non trovato", ex.getMessage());
        }

        /**
         * Tests that a contract is successfully created and saved to the
         * repository, and the correct DTO is returned.
         */
        @Test
        @Order(4)
        @DisplayName("Create contract returns saved DTO")
        void shouldCreateContract() {
            ContractDTO dto = new ContractDTO(null, "NewClient", "ABC123", "WBS001", "NewProject",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(10), 1L, 1L, null, null, null, null);

            Contracts entity = Contracts.builder().customerName("NewClient").build();
            Contracts saved = Contracts.builder().id(1L).customerName("NewClient").build();
            ContractDTO savedDTO = new ContractDTO(1L, "NewClient", "ABC123", "WBS001", "NewProject",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(10), 1L, 1L, null, null, null, null);

            when(contractMapper.toEntity(dto)).thenReturn(entity);
            when(contractsRepository.save(entity)).thenReturn(saved);
            when(contractMapper.toDTO(saved)).thenReturn(savedDTO);

            ContractDTO result = contractService.createContract(dto);

            assertEquals(1L, result.id());
            assertEquals("NewClient", result.customerName());
        }

        /**
         * Tests updating an existing contract and returns the updated DTO.
         */
        @Test
        @Order(5)
        @DisplayName("Update contract returns updated DTO")
        void shouldUpdateContract() {

            // Mock authentication (needed for history tracking)
            mockAuthentication("admin", "ADMIN");

            Roles adminRole = Roles.builder().role("ADMIN").build();
            Users adminUser = Users.builder()
                    .id(100L)
                    .username("admin")
                    .role(adminRole)
                    .build();

            Contracts existing = Contracts.builder()
                    .id(1L)
                    .customerName("OldClient")
                    .contractNumber("OLD123")
                    .wbsCode("OLDWBS")
                    .projectName("OldProject")
                    .status(ContractStatus.ACTIVE)
                    .startDate(LocalDate.of(2027, Month.JUNE, 15))
                    .endDate(LocalDate.of(2027, Month.JUNE, 15).plusDays(10))
                    .build();

            ContractDTO updateDTO = new ContractDTO(1L, "UpdatedClient", "NEW123", "WBSNEW", "NewProject",
                    ContractStatus.EXPIRED, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(5), 1L, 1L, null, null, null, null);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(contractsRepository.save(existing)).thenReturn(existing);
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(contractMapper.toDTO(existing)).thenReturn(updateDTO);
            when(businessAreasRepository.findById(1L)).thenReturn(Optional.of(BusinessAreas.builder().id(1L).name("Area").build()));

            ContractDTO result = contractService.updateContract(1L, updateDTO);

            assertEquals("UpdatedClient", result.customerName());
            assertEquals("NEW123", result.contractNumber());

            // Verify history was saved (status changed from ACTIVE to EXPIRED)
            verify(contractHistoryRepository, times(1)).save(any());
        }

        /**
         * Tests that a direct status change is rejected while the contract is
         * under approval-workflow review — it must go through approve/reject
         * instead of an arbitrary update.
         */
        @Test
        @Order(100)
        @DisplayName("Update contract rejects a status change while IN_REVIEW")
        void shouldRejectStatusChangeWhileInReview() {
            Contracts existing = Contracts.builder()
                    .id(1L)
                    .customerName("Client")
                    .contractNumber("CNTR-WF")
                    .status(ContractStatus.DRAFT)
                    .workflowStage(com.donatodev.bcm_backend.entity.WorkflowStage.IN_REVIEW)
                    .startDate(LocalDate.of(2027, Month.JUNE, 15))
                    .endDate(LocalDate.of(2027, Month.JUNE, 15).plusDays(10))
                    .build();

            ContractDTO updateDTO = new ContractDTO(1L, "Client", "CNTR-WF", null, null,
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15),
                    LocalDate.of(2027, Month.JUNE, 15).plusDays(5), 1L, 1L, null, null, null, null);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(existing));

            assertThrows(IllegalArgumentException.class, () -> contractService.updateContract(1L, updateDTO));
            verify(contractsRepository, never()).save(any());
        }

        @Test
        @Order(102)
        @DisplayName("Update contract allowed while IN_REVIEW when the status is unchanged")
        void shouldAllowNonStatusUpdateWhileInReview() {
            Contracts existing = Contracts.builder()
                    .id(1L)
                    .customerName("Client")
                    .contractNumber("CNTR-WF2")
                    .status(ContractStatus.DRAFT)
                    .workflowStage(com.donatodev.bcm_backend.entity.WorkflowStage.IN_REVIEW)
                    .startDate(LocalDate.of(2027, Month.JUNE, 15))
                    .endDate(LocalDate.of(2027, Month.JUNE, 15).plusDays(10))
                    .build();

            ContractDTO updateDTO = new ContractDTO(1L, "Client Renamed", "CNTR-WF2", null, null,
                    ContractStatus.DRAFT, LocalDate.of(2027, Month.JUNE, 15),
                    LocalDate.of(2027, Month.JUNE, 15).plusDays(5), null, null, null, null, null, null);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(contractsRepository.save(existing)).thenReturn(existing);
            when(contractMapper.toDTO(existing)).thenReturn(updateDTO);

            ContractDTO result = contractService.updateContract(1L, updateDTO);

            assertEquals("Client Renamed", result.customerName());
            verify(contractsRepository).save(existing);
        }

        /**
         * Tests that editing a non-DRAFT contract into DRAFT starts the
         * approval workflow, mirroring what happens on creation.
         */
        @Test
        @Order(101)
        @DisplayName("Update contract sets workflowStage=DRAFT when status changes to DRAFT")
        void shouldStartWorkflowWhenEditedIntoDraft() {
            mockAuthentication("admin", "ADMIN");
            Roles adminRole = Roles.builder().role("ADMIN").build();
            Users adminUser = Users.builder().id(100L).username("admin").role(adminRole).build();
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

            Contracts existing = Contracts.builder()
                    .id(1L)
                    .customerName("Client")
                    .contractNumber("CNTR-EDIT-DRAFT")
                    .status(ContractStatus.ACTIVE)
                    .workflowStage(null)
                    .startDate(LocalDate.of(2027, Month.JUNE, 15))
                    .endDate(LocalDate.of(2027, Month.JUNE, 15).plusDays(10))
                    .build();

            ContractDTO updateDTO = new ContractDTO(1L, "Client", "CNTR-EDIT-DRAFT", null, null,
                    ContractStatus.DRAFT, LocalDate.of(2027, Month.JUNE, 15),
                    LocalDate.of(2027, Month.JUNE, 15).plusDays(10), 1L, 1L, null, null, null, null);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(contractsRepository.save(existing)).thenReturn(existing);
            when(contractMapper.toDTO(existing)).thenReturn(updateDTO);
            when(businessAreasRepository.findById(1L)).thenReturn(Optional.of(BusinessAreas.builder().id(1L).name("Area").build()));

            contractService.updateContract(1L, updateDTO);

            assertEquals(com.donatodev.bcm_backend.entity.WorkflowStage.DRAFT, existing.getWorkflowStage());
        }

        /**
         * Tests that editing a DRAFT contract into a non-DRAFT status clears
         * the workflow stage, since it no longer applies.
         */
        @Test
        @Order(102)
        @DisplayName("Update contract clears workflowStage when status changes away from DRAFT")
        void shouldClearWorkflowStageWhenEditedAwayFromDraft() {
            mockAuthentication("admin", "ADMIN");
            Roles adminRole = Roles.builder().role("ADMIN").build();
            Users adminUser = Users.builder().id(100L).username("admin").role(adminRole).build();
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

            Contracts existing = Contracts.builder()
                    .id(1L)
                    .customerName("Client")
                    .contractNumber("CNTR-EDIT-ACTIVE")
                    .status(ContractStatus.DRAFT)
                    .workflowStage(com.donatodev.bcm_backend.entity.WorkflowStage.DRAFT)
                    .startDate(LocalDate.of(2027, Month.JUNE, 15))
                    .endDate(LocalDate.of(2027, Month.JUNE, 15).plusDays(10))
                    .build();

            ContractDTO updateDTO = new ContractDTO(1L, "Client", "CNTR-EDIT-ACTIVE", null, null,
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15),
                    LocalDate.of(2027, Month.JUNE, 15).plusDays(10), 1L, 1L, null, null, null, null);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(contractsRepository.save(existing)).thenReturn(existing);
            when(contractMapper.toDTO(existing)).thenReturn(updateDTO);
            when(businessAreasRepository.findById(1L)).thenReturn(Optional.of(BusinessAreas.builder().id(1L).name("Area").build()));

            contractService.updateContract(1L, updateDTO);

            assertEquals(null, existing.getWorkflowStage());
        }

        /**
         * Tests that editing other fields of a DRAFT contract without
         * changing its status leaves the workflow stage untouched.
         */
        @Test
        @Order(103)
        @DisplayName("Update contract leaves workflowStage untouched when status stays DRAFT")
        void shouldLeaveWorkflowStageWhenStatusUnchangedDraft() {
            Contracts existing = Contracts.builder()
                    .id(1L)
                    .customerName("Client")
                    .contractNumber("CNTR-STILL-DRAFT")
                    .status(ContractStatus.DRAFT)
                    .workflowStage(com.donatodev.bcm_backend.entity.WorkflowStage.DRAFT)
                    .startDate(LocalDate.of(2027, Month.JUNE, 15))
                    .endDate(LocalDate.of(2027, Month.JUNE, 15).plusDays(10))
                    .build();

            ContractDTO updateDTO = new ContractDTO(1L, "Client Renamed", "CNTR-STILL-DRAFT", null, null,
                    ContractStatus.DRAFT, LocalDate.of(2027, Month.JUNE, 15),
                    LocalDate.of(2027, Month.JUNE, 15).plusDays(10), 1L, 1L, null, null, null, null);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(contractsRepository.save(existing)).thenReturn(existing);
            when(contractMapper.toDTO(existing)).thenReturn(updateDTO);
            when(businessAreasRepository.findById(1L)).thenReturn(Optional.of(BusinessAreas.builder().id(1L).name("Area").build()));

            contractService.updateContract(1L, updateDTO);

            assertEquals(com.donatodev.bcm_backend.entity.WorkflowStage.DRAFT, existing.getWorkflowStage());
        }

        /**
         * Tests that updating a contract actually applies the new manager and
         * business area — previously the update path silently ignored both.
         */
        @Test
        @Order(104)
        @DisplayName("Update contract applies the new manager and business area")
        void shouldUpdateManagerAndBusinessAreaOnEdit() {
            Contracts existing = Contracts.builder()
                    .id(1L)
                    .customerName("Client")
                    .contractNumber("CNTR-MGR-AREA")
                    .status(ContractStatus.ACTIVE)
                    .manager(null)
                    .startDate(LocalDate.of(2027, Month.JUNE, 15))
                    .endDate(LocalDate.of(2027, Month.JUNE, 15).plusDays(10))
                    .build();

            ContractDTO updateDTO = new ContractDTO(1L, "Client", "CNTR-MGR-AREA", null, null,
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15),
                    LocalDate.of(2027, Month.JUNE, 15).plusDays(10), 2L, 5L, null, null, null, null);

            Managers newManager = Managers.builder().id(5L).firstName("Mario").lastName("Rossi").build();
            BusinessAreas newArea = BusinessAreas.builder().id(2L).name("Sales").build();

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(contractsRepository.save(existing)).thenReturn(existing);
            when(contractMapper.toDTO(existing)).thenReturn(updateDTO);
            when(businessAreasRepository.findById(2L)).thenReturn(Optional.of(newArea));
            when(managerService.getManagerEntity(5L)).thenReturn(newManager);

            contractService.updateContract(1L, updateDTO);

            assertEquals(newManager, existing.getManager());
            assertEquals(newArea, existing.getBusinessArea());
        }

        /**
         * Tests that clearing the manager (managerId=null) on update actually
         * unassigns it, rather than leaving the previous manager in place.
         */
        @Test
        @Order(105)
        @DisplayName("Update contract clears the manager when managerId is null")
        void shouldClearManagerWhenManagerIdIsNullOnEdit() {
            Managers oldManager = Managers.builder().id(9L).firstName("Old").lastName("Manager").build();
            Contracts existing = Contracts.builder()
                    .id(1L)
                    .customerName("Client")
                    .contractNumber("CNTR-CLEAR-MGR")
                    .status(ContractStatus.ACTIVE)
                    .manager(oldManager)
                    .startDate(LocalDate.of(2027, Month.JUNE, 15))
                    .endDate(LocalDate.of(2027, Month.JUNE, 15).plusDays(10))
                    .build();

            ContractDTO updateDTO = new ContractDTO(1L, "Client", "CNTR-CLEAR-MGR", null, null,
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15),
                    LocalDate.of(2027, Month.JUNE, 15).plusDays(10), 1L, null, null, null, null, null);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(contractsRepository.save(existing)).thenReturn(existing);
            when(contractMapper.toDTO(existing)).thenReturn(updateDTO);
            when(businessAreasRepository.findById(1L)).thenReturn(Optional.of(BusinessAreas.builder().id(1L).name("Area").build()));

            contractService.updateContract(1L, updateDTO);

            assertEquals(null, existing.getManager());
            verify(managerService, never()).getManagerEntity(any());
        }

        /**
         * Tests that updating a contract with a non-existent business area ID
         * throws rather than silently keeping the old area.
         */
        @Test
        @Order(106)
        @DisplayName("Update contract throws when the business area does not exist")
        void shouldThrowWhenUpdateReferencesUnknownBusinessArea() {
            Contracts existing = Contracts.builder()
                    .id(1L)
                    .customerName("Client")
                    .contractNumber("CNTR-BAD-AREA")
                    .status(ContractStatus.ACTIVE)
                    .startDate(LocalDate.of(2027, Month.JUNE, 15))
                    .endDate(LocalDate.of(2027, Month.JUNE, 15).plusDays(10))
                    .build();

            ContractDTO updateDTO = new ContractDTO(1L, "Client", "CNTR-BAD-AREA", null, null,
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15),
                    LocalDate.of(2027, Month.JUNE, 15).plusDays(10), 999L, null, null, null, null, null);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(businessAreasRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(BusinessAreaNotFoundException.class, () -> contractService.updateContract(1L, updateDTO));
            verify(contractsRepository, never()).save(any());
        }

        /**
         * Tests that the repository's deleteById method is called when deleting
         * a contract.
         */
        @Test
        @Order(6)
        @DisplayName("Delete contract calls repository")
        void shouldDeleteContract() {
            Contracts contract = Contracts.builder().id(1L).build();
            when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));

            contractService.deleteContract(1L);

            verify(contractsRepository, times(1)).delete(contract);
        }

        /**
         * Tests that deleting a non-existent contract throws
         * ContractNotFoundException.
         */
        @Test
        @Order(30)
        @DisplayName("Delete contract throws when contract not found")
        void shouldThrowWhenDeletingMissingContract() {
            when(contractsRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class, () -> contractService.deleteContract(1L));
        }

        /**
         * Tests that UserNotFoundException is thrown when the current user is
         * not found in the repository during contract retrieval.
         */
        @Test
        @Order(7)
        @DisplayName("Get all contracts throws if user not found")
        @MockitoSettings(strictness = Strictness.LENIENT)
        void shouldThrowWhenUserNotFound() {
            mockAuthentication("ghost", "USER");
            when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UserNotFoundException ex
                    = assertThrows(UserNotFoundException.class, () -> contractService.getAllContracts());
            assertEquals("Utente non trovato", ex.getMessage());
        }

        /**
         * Test retrieving all contracts as a MANAGER should return only those
         * assigned to the manager.
         */
        @Test
        @Order(8)
        @DisplayName("Get all contracts as MANAGER")
        void shouldGetAllContractsAsManager() {
            Roles managerRole = Roles.builder().role("MANAGER").build();
            Managers manager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder()
                    .username("manager1")
                    .role(managerRole)
                    .manager(manager)
                    .build();

            Contracts contract = Contracts.builder().id(1L).customerName("ClientA").build();
            ContractDTO dto = new ContractDTO(1L, "ClientA", "CON123", "WBS", "Proj", ContractStatus.ACTIVE,
                    LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 5L, 1L, null, null, null, null);

            mockAuthentication("manager1", "MANAGER");

            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(managerUser));
            when(contractsRepository.findByManagerId(5L)).thenReturn(List.of(contract));
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            List<ContractDTO> result = contractService.getAllContracts();

            assertEquals(1, result.size());
            assertEquals("ClientA", result.get(0).customerName());
        }

        /**
         * Test retrieving contracts filtered by status as a MANAGER should
         * return matching results.
         */
        @Test
        @Order(9)
        @DisplayName("Get contracts by status as MANAGER")
        void shouldGetContractsByStatusAsManager() {
            Roles managerRole = Roles.builder().role("MANAGER").build();
            Managers manager = Managers.builder().id(7L).build();
            Users managerUser = Users.builder()
                    .username("manager2")
                    .role(managerRole)
                    .manager(manager)
                    .build();

            Contracts contract = Contracts.builder().id(2L).status(ContractStatus.ACTIVE).customerName("ClientB").build();
            ContractDTO dto = new ContractDTO(2L, "ClientB", "CON456", "WBS2", "Proj2", ContractStatus.ACTIVE,
                    LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(20), 7L, 1L, null, null, null, null);

            mockAuthentication("manager2", "MANAGER");

            when(usersRepository.findByUsername("manager2")).thenReturn(Optional.of(managerUser));
            when(contractsRepository.findByManagerIdAndStatus(7L, ContractStatus.ACTIVE)).thenReturn(List.of(contract));
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            List<ContractDTO> result = contractService.getContractsByStatus(ContractStatus.ACTIVE);

            assertEquals(1, result.size());
            assertEquals("ClientB", result.get(0).customerName());
        }

        /**
         * Test should throw UserNotFoundException when authentication is
         * missing.
         */
        @Test
        @Order(10)
        @DisplayName("Should throw if authentication is missing or invalid")
        void shouldThrowIfAuthenticationInvalid() {
            SecurityContextHolder.clearContext();

            UserNotFoundException ex
                    = assertThrows(UserNotFoundException.class, () -> contractService.getAllContracts());
            assertEquals("Nessun utente autenticato", ex.getMessage());
        }

        /**
         * Test should throw UserNotFoundException when authentication context
         * is null.
         */
        @Test
        @Order(11)
        @DisplayName("Should throw if authentication is null")
        void shouldThrowIfAuthenticationNull() {
            SecurityContextHolder.getContext().setAuthentication(null); // Explicitly set authentication to null
            UserNotFoundException ex
                    = assertThrows(UserNotFoundException.class, () -> contractService.getAllContracts());
            assertEquals("Nessun utente autenticato", ex.getMessage());
        }

        /**
         * Test fallback to principal.toString() when principal is not instance
         * of UserDetails.
         */
        @Test
        @Order(12)
        @DisplayName("Should return principal.toString() if not instance of UserDetails")
        void shouldReturnPrincipalToStringIfNotUserDetails() {

            UsernamePasswordAuthenticationToken auth
                    = new UsernamePasswordAuthenticationToken("basicPrincipal", null, List.of(() -> "ROLE_ADMIN"));

            SecurityContextHolder.getContext().setAuthentication(auth);

            when(usersRepository.findByUsername("basicPrincipal"))
                    .thenReturn(Optional.of(Users.builder()
                            .id(99L)
                            .role(Roles.builder().role("ADMIN").build())
                            .build()));

            when(contractsRepository.findAll()).thenReturn(List.of());

            List<ContractDTO> result = contractService.getAllContracts();

            assertTrue(result.isEmpty());
        }

        /**
         * Test should throw UserNotFoundException when authentication is
         * present but not authenticated.
         */
        @Test
        @Order(13)
        @DisplayName("Should throw if authentication not authenticated")
        void shouldThrowIfAuthenticationNotAuthenticated() {
            UsernamePasswordAuthenticationToken auth
                    = new UsernamePasswordAuthenticationToken("user", null, List.of());
            auth.setAuthenticated(false);
            SecurityContextHolder.getContext().setAuthentication(auth);

            UserNotFoundException ex
                    = assertThrows(UserNotFoundException.class, () -> contractService.getAllContracts());
            assertEquals("Nessun utente autenticato", ex.getMessage());
        }

        /**
         * Test should throw UserNotFoundException when principal is explicitly
         * null.
         */
        @Test
        @Order(14)
        @DisplayName("Should throw if principal is null")
        void shouldThrowIfPrincipalIsNull() {
            UsernamePasswordAuthenticationToken auth
                    = new UsernamePasswordAuthenticationToken(null, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);

            UserNotFoundException ex
                    = assertThrows(UserNotFoundException.class, () -> contractService.getAllContracts());
            assertEquals("Nessun utente autenticato", ex.getMessage());
        }

        /**
         * Test should throw UserNotFoundException if user is not found when
         * filtering contracts by status.
         */
        @Test
        @Order(15)
        @DisplayName("Should throw if user not found in getContractsByStatus")
        void shouldThrowIfUserNotFoundInGetContractsByStatus() {

            mockAuthentication("ghost", "USER");

            when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UserNotFoundException ex
                    = assertThrows(UserNotFoundException.class,
                            () -> contractService.getContractsByStatus(ContractStatus.ACTIVE));
            assertEquals("Utente non trovato", ex.getMessage());
        }

        /**
         * Test should throw ContractNotFoundException when trying to update a
         * contract that doesn't exist.
         */
        @Test
        @Order(16)
        @DisplayName("Should throw if contract to update is not found")
        void shouldThrowIfContractToUpdateNotFound() {
            Long contractId = 999L;
            ContractDTO dto = new ContractDTO(contractId, "x", "x", "x", "x",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(5), 1L, 1L, null, null, null, null);

            when(contractsRepository.findById(contractId)).thenReturn(Optional.empty());

            ContractNotFoundException ex
                    = assertThrows(ContractNotFoundException.class, () -> contractService.updateContract(contractId, dto));
            assertEquals("Contratto non trovato", ex.getMessage());
        }

        @Test
        @Order(17)
        @DisplayName("Get contracts by status as ADMIN")
        void shouldGetContractsByStatusAsAdmin() {
            Roles adminRole = Roles.builder().role("ADMIN").build();
            Users admin = Users.builder()
                    .username("admin")
                    .role(adminRole)
                    .build();

            Contracts contract = Contracts.builder()
                    .id(1L)
                    .status(ContractStatus.ACTIVE)
                    .customerName("ClientA")
                    .build();
            ContractDTO dto = new ContractDTO(1L, "ClientA", "CON123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);

            mockAuthentication("admin", "ADMIN");

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findByStatus(ContractStatus.ACTIVE)).thenReturn(List.of(contract));
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            List<ContractDTO> result = contractService.getContractsByStatus(ContractStatus.ACTIVE);

            assertEquals(1, result.size());
            assertEquals("ClientA", result.get(0).customerName());
        }

        @Test
        @Order(18)
        @DisplayName("Get contract stats returns correct counts")
        void shouldGetContractStats() {
            when(contractsRepository.countAllContracts()).thenReturn(100);
            when(contractsRepository.countActiveContracts()).thenReturn(50);
            when(contractsRepository.countExpiringContracts(any(LocalDate.class))).thenReturn(30);
            when(contractsRepository.countExpiredContracts()).thenReturn(20);
            when(contractsRepository.countDraftContracts()).thenReturn(10);

            ContractStatsResponse result = contractService.getContractStats();

            assertEquals(100, result.getTotal());
            assertEquals(50, result.getActive());
            assertEquals(30, result.getExpiring());
            assertEquals(20, result.getExpired());
            assertEquals(10, result.getDraft());
        }

        @Test
        @Order(19)
        @DisplayName("Assign manager to contract successfully")
        void shouldAssignManagerToContract() {
            Long contractId = 1L;
            Long managerId = 5L;

            Contracts contract = Contracts.builder().id(contractId).build();
            Managers manager = Managers.builder().id(managerId).build();

            when(contractsRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(managerService.getManagerEntity(managerId)).thenReturn(manager);
            when(contractsRepository.save(contract)).thenReturn(contract);

            contractService.assignManager(contractId, managerId);

            verify(contractsRepository).save(contract);
            assertEquals(manager, contract.getManager());
        }

        @Test
        @Order(20)
        @DisplayName("Assign manager throws when contract not found")
        void shouldThrowWhenAssigningManagerToNonExistentContract() {
            when(contractsRepository.findById(999L)).thenReturn(Optional.empty());

            IllegalArgumentException ex
                    = assertThrows(IllegalArgumentException.class,
                            () -> contractService.assignManager(999L, 5L));
            assertTrue(ex.getMessage().contains("Contratto non trovato"));
        }

        @Test
        @Order(21)
        @DisplayName("Assign manager throws when manager not found")
        void shouldThrowWhenAssigningNonExistentManager() {
            Contracts contract = Contracts.builder().id(1L).build();

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));
            when(managerService.getManagerEntity(999L)).thenReturn(null);

            ManagerNotFoundException ex
                    = assertThrows(ManagerNotFoundException.class,
                            () -> contractService.assignManager(1L, 999L));
            assertTrue(ex.getMessage().contains("Manager non trovato"));
        }

        @Test
        @Order(22)
        @DisplayName("Get collaborator IDs returns list of manager IDs")
        void shouldGetCollaboratorIds() {
            Long contractId = 1L;
            Contracts contract = Contracts.builder().id(contractId).build();
            List<Long> managerIds = List.of(10L, 20L, 30L);

            when(contractsRepository.findById(contractId)).thenReturn(Optional.of(contract));
            when(contractManagerRepository.findManagerIdsByContractId(contractId)).thenReturn(managerIds);

            List<Long> result = contractService.getCollaboratorIds(contractId);

            assertEquals(3, result.size());
            assertEquals(10L, result.get(0));
            assertEquals(20L, result.get(1));
            assertEquals(30L, result.get(2));
            verify(contractManagerRepository).findManagerIdsByContractId(contractId);
        }

        @Test
        @Order(23)
        @DisplayName("Get collaborator IDs throws when contract not found")
        void shouldThrowWhenGettingCollaboratorIdsForNonExistentContract() {
            when(contractsRepository.findById(999L)).thenReturn(Optional.empty());

            ContractNotFoundException ex
                    = assertThrows(ContractNotFoundException.class,
                            () -> contractService.getCollaboratorIds(999L));
            assertTrue(ex.getMessage().contains("Contratto non trovato"));
        }

        @Test
        @Order(24)
        @DisplayName("Set collaborators with valid manager IDs")
        void shouldSetCollaborators() {
            Long contractId = 1L;
            List<Long> managerIds = List.of(5L, 10L, 15L);
            Contracts contract = Contracts.builder().id(contractId).build();

            when(contractsRepository.findById(contractId)).thenReturn(Optional.of(contract));

            contractService.setCollaborators(contractId, managerIds);

            verify(contractManagerRepository).deleteAllByContractId(contractId);
            verify(contractManagerRepository).insertIgnore(contractId, 5L);
            verify(contractManagerRepository).insertIgnore(contractId, 10L);
            verify(contractManagerRepository).insertIgnore(contractId, 15L);
        }

        @Test
        @Order(25)
        @DisplayName("Set collaborators with null list removes all collaborators")
        void shouldSetCollaboratorsWithNull() {
            Long contractId = 1L;
            Contracts contract = Contracts.builder().id(contractId).build();

            when(contractsRepository.findById(contractId)).thenReturn(Optional.of(contract));

            contractService.setCollaborators(contractId, null);

            verify(contractManagerRepository).deleteAllByContractId(contractId);
            verify(contractManagerRepository, times(0)).insertIgnore(any(), any());
        }

        @Test
        @Order(26)
        @DisplayName("Set collaborators throws when contract not found")
        void shouldThrowWhenSettingCollaboratorsForNonExistentContract() {
            when(contractsRepository.findById(999L)).thenReturn(Optional.empty());

            List<Long> managerIds = List.of(1L, 2L);

            ContractNotFoundException ex
                    = assertThrows(ContractNotFoundException.class,
                            () -> contractService.setCollaborators(999L, managerIds));
            assertTrue(ex.getMessage().contains("Contratto non trovato"));
        }

        @ParameterizedTest(name = "[{index}] Search paged as ADMIN: term=''{0}'', status={1}")
        @MethodSource("provideAdminSearchParameters")
        @Order(27)
        @DisplayName("Search paged as ADMIN with various filters")
        void shouldSearchPagedAdmin(String searchTerm, ContractStatus status, String expectedRepoMethod) {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            Contracts contract = Contracts.builder()
                    .id(1L)
                    .customerName("TestClient")
                    .status(ContractStatus.ACTIVE)
                    .build();
            ContractDTO dto = new ContractDTO(1L, "TestClient", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

            // Mock repository based on expected method
            switch (expectedRepoMethod) {
                case "findAllBy" ->
                    when(contractsRepository.findAllBy(any(Pageable.class))).thenReturn(page);
                case "findByStatus" ->
                    when(contractsRepository.findByStatus(eq(status), any(Pageable.class))).thenReturn(page);
                case "findByTerm" ->
                    when(contractsRepository.findByContractNumberContainingIgnoreCaseOrCustomerNameContainingIgnoreCase(
                            eq(searchTerm), eq(searchTerm), any(Pageable.class))).thenReturn(page);
                case "findByStatusAndTerm" ->
                    when(contractsRepository.findByStatusAndContractNumberContainingIgnoreCaseOrStatusAndCustomerNameContainingIgnoreCase(
                            eq(status), eq(searchTerm), eq(status), eq(searchTerm), any(Pageable.class))).thenReturn(page);
                default ->
                    throw new IllegalArgumentException("Unexpected repository method: " + expectedRepoMethod);
            }

            when(contractMapper.toDTO(contract)).thenReturn(dto);

            Page<ContractDTO> result = contractService.searchPaged(searchTerm, status, 0, 10);

            assertEquals(1, result.getTotalElements());
        }

        private static Stream<Arguments> provideAdminSearchParameters() {
            return Stream.of(
                    Arguments.of(null, null, "findAllBy"),
                    Arguments.of(null, ContractStatus.ACTIVE, "findByStatus"),
                    Arguments.of("test", null, "findByTerm"),
                    Arguments.of("test", ContractStatus.ACTIVE, "findByStatusAndTerm"),
                    Arguments.of("   ", null, "findAllBy"), // blank term
                    Arguments.of("", null, "findAllBy") // empty term
            );
        }

        @Test
        @Order(28)
        @DisplayName("Search paged as ADMIN with status filter")
        void shouldSearchPagedAdminWithStatus() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            Contracts contract = Contracts.builder().id(1L).status(ContractStatus.ACTIVE).build();
            ContractDTO dto = new ContractDTO(1L, "Client", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findByStatus(eq(ContractStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(page);
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            Page<ContractDTO> result = contractService.searchPaged(null, ContractStatus.ACTIVE, 0, 10);

            assertEquals(1, result.getTotalElements());
            verify(contractsRepository).findByStatus(eq(ContractStatus.ACTIVE), any(Pageable.class));
        }

        @Test
        @Order(29)
        @DisplayName("Search paged as ADMIN with search term")
        void shouldSearchPagedAdminWithTerm() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            Contracts contract = Contracts.builder().id(1L).customerName("TestClient").build();
            ContractDTO dto = new ContractDTO(1L, "TestClient", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findByContractNumberContainingIgnoreCaseOrCustomerNameContainingIgnoreCase(
                    eq("test"), eq("test"), any(Pageable.class))).thenReturn(page);
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            Page<ContractDTO> result = contractService.searchPaged("test", null, 0, 10);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @Order(30)
        @DisplayName("Search paged as ADMIN with status and term")
        void shouldSearchPagedAdminWithStatusAndTerm() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            Contracts contract = Contracts.builder().id(1L).customerName("TestClient").build();
            ContractDTO dto = new ContractDTO(1L, "TestClient", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findByStatusAndContractNumberContainingIgnoreCaseOrStatusAndCustomerNameContainingIgnoreCase(
                    eq(ContractStatus.ACTIVE), eq("test"), eq(ContractStatus.ACTIVE), eq("test"), any(Pageable.class)))
                    .thenReturn(page);
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            Page<ContractDTO> result = contractService.searchPaged("test", ContractStatus.ACTIVE, 0, 10);

            assertEquals(1, result.getTotalElements());
        }

        @ParameterizedTest(name = "[{index}] Search paged as MANAGER: term=''{0}'', status={1}")
        @MethodSource("provideManagerSearchParameters")
        @Order(31)
        @DisplayName("Search paged as MANAGER with various filters")
        void shouldSearchPagedManager(String searchTerm, ContractStatus status, String expectedRepoMethod) {
            Managers manager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            Contracts contract = Contracts.builder()
                    .id(1L)
                    .customerName("TestClient")
                    .status(ContractStatus.ACTIVE)
                    .build();
            ContractDTO dto = new ContractDTO(1L, "TestClient", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 5L, 1L, null, null, null, null);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("manager", "MANAGER");
            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));

            Long managerId = 5L;

            // Mock repository based on expected method
            switch (expectedRepoMethod) {
                case "findByManagerId" ->
                    when(contractsRepository.findByManagerId(eq(managerId), any(Pageable.class))).thenReturn(page);
                case "findByManagerIdAndStatus" ->
                    when(contractsRepository.findByManagerIdAndStatus(
                            eq(managerId), eq(status), any(Pageable.class))).thenReturn(page);
                case "findByManagerIdAndTerm" ->
                    when(contractsRepository.findByManagerIdAndContractNumberContainingIgnoreCaseOrManagerIdAndCustomerNameContainingIgnoreCase(
                            eq(managerId), eq(searchTerm), eq(managerId), eq(searchTerm), any(Pageable.class))).thenReturn(page);
                case "findByManagerIdAndStatusAndTerm" ->
                    when(contractsRepository.findByManagerIdAndStatusAndContractNumberContainingIgnoreCaseOrManagerIdAndStatusAndCustomerNameContainingIgnoreCase(
                            eq(managerId), eq(status), eq(searchTerm), eq(managerId), eq(status), eq(searchTerm), any(Pageable.class))).thenReturn(page);
                default ->
                    throw new IllegalArgumentException("Unexpected repository method: " + expectedRepoMethod);
            }

            when(contractMapper.toDTO(contract)).thenReturn(dto);

            Page<ContractDTO> result = contractService.searchPaged(searchTerm, status, 0, 10);

            assertEquals(1, result.getTotalElements());
        }

        private static Stream<Arguments> provideManagerSearchParameters() {
            return Stream.of(
                    Arguments.of(null, null, "findByManagerId"),
                    Arguments.of(null, ContractStatus.ACTIVE, "findByManagerIdAndStatus"),
                    Arguments.of("test", null, "findByManagerIdAndTerm"),
                    Arguments.of("test", ContractStatus.ACTIVE, "findByManagerIdAndStatusAndTerm")
            );
        }

        @Test
        @Order(32)
        @DisplayName("Search paged as MANAGER with status filter")
        void shouldSearchPagedManagerWithStatus() {
            Managers manager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            Contracts contract = Contracts.builder().id(1L).status(ContractStatus.ACTIVE).build();
            ContractDTO dto = new ContractDTO(1L, "Client", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 5L, 1L, null, null, null, null);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("manager", "MANAGER");
            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(contractsRepository.findByManagerIdAndStatus(eq(5L), eq(ContractStatus.ACTIVE), any(Pageable.class)))
                    .thenReturn(page);
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            Page<ContractDTO> result = contractService.searchPaged(null, ContractStatus.ACTIVE, 0, 10);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @Order(33)
        @DisplayName("Search paged as MANAGER with search term")
        void shouldSearchPagedManagerWithTerm() {
            Managers manager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            Contracts contract = Contracts.builder().id(1L).customerName("TestClient").build();
            ContractDTO dto = new ContractDTO(1L, "TestClient", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 5L, 1L, null, null, null, null);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("manager", "MANAGER");
            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(contractsRepository.findByManagerIdAndContractNumberContainingIgnoreCaseOrManagerIdAndCustomerNameContainingIgnoreCase(
                    eq(5L), eq("test"), eq(5L), eq("test"), any(Pageable.class))).thenReturn(page);
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            Page<ContractDTO> result = contractService.searchPaged("test", null, 0, 10);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @Order(34)
        @DisplayName("Search paged as MANAGER with status and term")
        void shouldSearchPagedManagerWithStatusAndTerm() {
            Managers manager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            Contracts contract = Contracts.builder().id(1L).customerName("TestClient").build();
            ContractDTO dto = new ContractDTO(1L, "TestClient", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 5L, 1L, null, null, null, null);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("manager", "MANAGER");
            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(contractsRepository.findByManagerIdAndStatusAndContractNumberContainingIgnoreCaseOrManagerIdAndStatusAndCustomerNameContainingIgnoreCase(
                    eq(5L), eq(ContractStatus.ACTIVE), eq("test"), eq(5L), eq(ContractStatus.ACTIVE), eq("test"), any(Pageable.class)))
                    .thenReturn(page);
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            Page<ContractDTO> result = contractService.searchPaged("test", ContractStatus.ACTIVE, 0, 10);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @Order(35)
        @DisplayName("Search paged as MANAGER with null managerId returns empty page")
        void shouldSearchPagedManagerWithNullManagerId() {
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(null)
                    .build();

            mockAuthentication("manager", "MANAGER");
            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));

            Page<ContractDTO> result = contractService.searchPaged(null, null, 0, 10);

            assertEquals(0, result.getTotalElements());
        }

        /**
         * Test: Should return managerId as null when user is ADMIN in
         * searchPaged.
         */
        @Test
        @Order(36)
        @DisplayName("getAuthCtx should return null managerId for ADMIN role")
        void shouldReturnNullManagerIdForAdminInSearchPaged() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .manager(Managers.builder().id(100L).build()) // Admin has a manager but should be ignored
                    .build();

            Contracts contract = Contracts.builder().id(1L).customerName("Client").build();
            ContractDTO dto = new ContractDTO(1L, "Client", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findAllBy(any(Pageable.class))).thenReturn(page);
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            // This should trigger the ADMIN branch in getAuthCtx where managerId becomes null
            Page<ContractDTO> result = contractService.searchPaged(null, null, 0, 10);

            assertEquals(1, result.getTotalElements());
            verify(contractsRepository).findAllBy(any(Pageable.class));
        }

        @Test
        @Order(37)
        @DisplayName("Search paged with blank search term should behave as no term")
        void shouldSearchPagedWithBlankTerm() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            Contracts contract = Contracts.builder().id(1L).customerName("Client").build();
            ContractDTO dto = new ContractDTO(1L, "Client", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findAllBy(any(Pageable.class))).thenReturn(page);
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            // Testing with blank string (only spaces)
            Page<ContractDTO> result = contractService.searchPaged("   ", null, 0, 10);

            assertEquals(1, result.getTotalElements());
            verify(contractsRepository).findAllBy(any(Pageable.class));
        }

        @Test
        @Order(38)
        @DisplayName("Search paged with empty string should behave as no term")
        void shouldSearchPagedWithEmptyString() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            Contracts contract = Contracts.builder().id(1L).customerName("Client").build();
            ContractDTO dto = new ContractDTO(1L, "Client", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findAllBy(any(Pageable.class))).thenReturn(page);
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            // Testing with empty string
            Page<ContractDTO> result = contractService.searchPaged("", null, 0, 10);

            assertEquals(1, result.getTotalElements());
            verify(contractsRepository).findAllBy(any(Pageable.class));
        }

        @Test
        @Order(39)
        @DisplayName("Search paged should use isAdmin() for both ADMIN and non-ADMIN users")
        void shouldUseIsAdminMethodForBothRoles() {
            // Test as ADMIN - forces isAdmin() to return true
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            Contracts contract = Contracts.builder().id(1L).customerName("Client").build();
            ContractDTO dto = new ContractDTO(1L, "Client", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findAllBy(any(Pageable.class))).thenReturn(page);
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            // Call that uses isAdmin() -> true branch
            Page<ContractDTO> adminResult = contractService.searchPaged(null, null, 0, 10);
            assertEquals(1, adminResult.getTotalElements());

            // Clear context and test as MANAGER - forces isAdmin() to return false
            SecurityContextHolder.clearContext();

            Managers manager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            mockAuthentication("manager", "MANAGER");
            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(contractsRepository.findByManagerId(eq(5L), any(Pageable.class))).thenReturn(page);

            // Call that uses isAdmin() -> false branch
            Page<ContractDTO> managerResult = contractService.searchPaged(null, null, 0, 10);
            assertEquals(1, managerResult.getTotalElements());
        }

        @Test
        @Order(40)
        @DisplayName("AuthCtx isAdmin method should return correct values for different roles")
        void shouldTestAuthCtxIsAdminMethod() {
            // Test 1: ADMIN role
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            Contracts contract = Contracts.builder().id(1L).customerName("Client").build();
            ContractDTO dto = new ContractDTO(1L, "Client", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findAllBy(any(Pageable.class))).thenReturn(page);
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            // This forces isAdmin() to be called and return true
            contractService.searchPaged(null, null, 0, 10);

            verify(contractsRepository).findAllBy(any(Pageable.class));

            // Test 2: MANAGER role (non-admin)
            SecurityContextHolder.clearContext();

            Managers manager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            mockAuthentication("manager", "MANAGER");
            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(contractsRepository.findByManagerId(eq(5L), any(Pageable.class))).thenReturn(page);

            // This forces isAdmin() to be called and return false
            contractService.searchPaged(null, null, 0, 10);

            verify(contractsRepository).findByManagerId(eq(5L), any(Pageable.class));
        }

        @Test
        @Order(41)
        @DisplayName("Search paged should throw if user not found in getAuthCtx")
        void shouldThrowIfUserNotFoundInSearchPaged() {
            mockAuthentication("ghostuser", "USER");

            // User not found in database
            when(usersRepository.findByUsername("ghostuser")).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                    () -> contractService.searchPaged(null, null, 0, 10));

            assertEquals("Utente non trovato", ex.getMessage());
        }

        @Test
        @Order(42)
        @DisplayName("Update contract without status change should not create history")
        void shouldUpdateContractWithoutStatusChange() {
            // Mock authentication
            mockAuthentication("admin", "ADMIN");

            Roles adminRole = Roles.builder().role("ADMIN").build();
            Users adminUser = Users.builder()
                    .id(100L)
                    .username("admin")
                    .role(adminRole)
                    .build();

            Contracts existing = Contracts.builder()
                    .id(1L)
                    .customerName("OldClient")
                    .contractNumber("OLD123")
                    .wbsCode("OLDWBS")
                    .projectName("OldProject")
                    .status(ContractStatus.ACTIVE) // ← ACTIVE
                    .startDate(LocalDate.of(2027, Month.JUNE, 15))
                    .endDate(LocalDate.of(2027, Month.JUNE, 15).plusDays(10))
                    .build();

            // Update DTO with SAME status 
            ContractDTO updateDTO = new ContractDTO(1L, "UpdatedClient", "NEW123", "WBSNEW", "NewProject",
                    ContractStatus.ACTIVE,
                    LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(5), 1L, 1L, null, null, null, null);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(contractsRepository.save(existing)).thenReturn(existing);
            when(contractMapper.toDTO(existing)).thenReturn(updateDTO);
            when(businessAreasRepository.findById(1L)).thenReturn(Optional.of(BusinessAreas.builder().id(1L).name("Area").build()));

            ContractDTO result = contractService.updateContract(1L, updateDTO);

            assertEquals("UpdatedClient", result.customerName());

            // Verify history was NOT saved (status didn't change)
            verify(contractHistoryRepository, never()).save(any());
            // Verify usersRepository was NOT called (no need to fetch user)
            verify(usersRepository, never()).findByUsername(any());
        }

        @Test
        @Order(43)
        @DisplayName("Update contract with status change should throw if user not found")
        void shouldThrowWhenUpdatingContractWithStatusChangeAndUserNotFound() {
            // Mock authentication
            mockAuthentication("ghost", "ADMIN");

            Contracts existing = Contracts.builder()
                    .id(1L)
                    .customerName("Client")
                    .contractNumber("C123")
                    .wbsCode("WBS")
                    .projectName("Project")
                    .status(ContractStatus.ACTIVE)
                    .startDate(LocalDate.of(2027, Month.JUNE, 15))
                    .endDate(LocalDate.of(2027, Month.JUNE, 15).plusDays(10))
                    .build();

            // Update DTO with DIFFERENT status
            ContractDTO updateDTO = new ContractDTO(1L, "Client", "C123", "WBS", "Project",
                    ContractStatus.EXPIRED,
                    LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(10), 1L, 1L, null, null, null, null);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());
            when(businessAreasRepository.findById(1L)).thenReturn(Optional.of(BusinessAreas.builder().id(1L).name("Area").build()));

            // Should throw UserNotFoundException when trying to create history
            UserNotFoundException ex = assertThrows(UserNotFoundException.class,
                    () -> contractService.updateContract(1L, updateDTO));

            assertEquals("Utente non trovato", ex.getMessage());

            // Verify history was NOT saved (exception thrown before)
            verify(contractHistoryRepository, never()).save(any());
        }

        @Test
        @Order(44)
        @DisplayName("Get expiring contracts within 30 days")
        void shouldGetExpiringContracts() {

            LocalDate today = LocalDate.now(); //NOSONAR S6543 — must match service internal clock
            LocalDate futureDate = today.plusDays(30);

            Contracts expiring1 = Contracts.builder()
                    .id(1L)
                    .contractNumber("CNT-001")
                    .customerName("Acme Corp")
                    .status(ContractStatus.ACTIVE)
                    .endDate(today.plusDays(10))
                    .build();

            Contracts expiring2 = Contracts.builder()
                    .id(2L)
                    .contractNumber("CNT-002")
                    .customerName("TechStart Inc")
                    .status(ContractStatus.ACTIVE)
                    .endDate(today.plusDays(25))
                    .build();

            when(contractsRepository.findExpiringContracts(today, futureDate))
                    .thenReturn(List.of(expiring1, expiring2));

            when(contractMapper.toDTO(expiring1)).thenReturn(
                    new ContractDTO(1L, "Acme Corp", "CNT-001", "WBS-001", "Project A",
                            ContractStatus.ACTIVE, today, today.plusDays(10),
                            null, null, null, null, null, 10)
            );

            when(contractMapper.toDTO(expiring2)).thenReturn(
                    new ContractDTO(2L, "TechStart Inc", "CNT-002", "WBS-002", "Project B",
                            ContractStatus.ACTIVE, today, today.plusDays(25),
                            null, null, null, null, null, 25)
            );

            List<ContractDTO> result = contractService.getExpiringContracts(30);

            assertEquals(2, result.size());
            assertEquals("CNT-001", result.get(0).contractNumber());
            assertEquals("CNT-002", result.get(1).contractNumber());
            assertEquals(10, result.get(0).daysUntilExpiry());
            assertEquals(25, result.get(1).daysUntilExpiry());
        }

        @Test
        @Order(45)
        @DisplayName("Get expiring contracts returns empty list when none expiring")
        void shouldReturnEmptyListWhenNoExpiringContracts() {

            LocalDate today = LocalDate.now(); //NOSONAR S6543 — must match service internal clock
            LocalDate futureDate = today.plusDays(30);

            when(contractsRepository.findExpiringContracts(today, futureDate))
                    .thenReturn(List.of());

            List<ContractDTO> result = contractService.getExpiringContracts(30);

            assertTrue(result.isEmpty());
            verify(contractsRepository, times(1)).findExpiringContracts(today, futureDate);
        }

        @Test
        @Order(46)
        @DisplayName("Should get contracts timeline (started per month, last 12 months, zero-filled)")
        void shouldGetContractsTimeline() {
            YearMonth currentMonth = YearMonth.now(ZoneId.systemDefault());
            YearMonth oldestMonth = currentMonth.minusMonths(11);
            YearMonth sixMonthsAgo = currentMonth.minusMonths(6);
            YearMonth threeMonthsAgo = currentMonth.minusMonths(3);

            List<Object[]> mockResults = List.of(
                    new Object[]{sixMonthsAgo.getYear(), sixMonthsAgo.getMonthValue(), 5L},
                    new Object[]{threeMonthsAgo.getYear(), threeMonthsAgo.getMonthValue(), 3L},
                    new Object[]{currentMonth.getYear(), currentMonth.getMonthValue(), 7L}
            );

            when(contractsRepository.countContractsByMonth(any(LocalDate.class)))
                    .thenReturn(mockResults);

            List<ContractsTimelineDTO> result = contractService.getContractsTimeline();

            assertNotNull(result);
            assertEquals(12, result.size());
            // Chronological order: oldest month first, current month last
            assertEquals(oldestMonth.toString(), result.get(0).getMonth());
            assertEquals(currentMonth.toString(), result.get(11).getMonth());
            // Zero-filled months with no contracts started
            assertEquals(0L, result.get(0).getCount());
            // Real counts land on the correct chronological index
            assertEquals(5L, result.get(5).getCount());
            assertEquals(sixMonthsAgo.toString(), result.get(5).getMonth());
            assertEquals(3L, result.get(8).getCount());
            assertEquals(threeMonthsAgo.toString(), result.get(8).getMonth());
            assertEquals(7L, result.get(11).getCount());

            verify(contractsRepository, times(1)).countContractsByMonth(any(LocalDate.class));
        }

        @Test
        @Order(47)
        @DisplayName("Should get contracts by area")
        void shouldGetContractsByArea() {

            List<ContractsByAreaDTO> mockResults = List.of(
                    new ContractsByAreaDTO("IT", 10L),
                    new ContractsByAreaDTO("Finance", 7L),
                    new ContractsByAreaDTO("HR", 3L)
            );

            when(contractsRepository.countContractsByArea()).thenReturn(mockResults);

            List<ContractsByAreaDTO> result = contractService.getContractsByArea();

            assertNotNull(result);
            assertEquals(3, result.size());
            assertEquals("IT", result.get(0).getAreaName());
            assertEquals(10L, result.get(0).getCount());

            verify(contractsRepository, times(1)).countContractsByArea();
        }

        @Test
        @Order(48)
        @DisplayName("Should get top managers")
        void shouldGetTopManagers() {

            List<TopManagerDTO> mockResults = List.of(
                    new TopManagerDTO(1L, "John Doe", 15L),
                    new TopManagerDTO(2L, "Jane Smith", 12L),
                    new TopManagerDTO(3L, "Bob Johnson", 8L)
            );

            when(contractsRepository.findTopManagers(any(Pageable.class)))
                    .thenReturn(mockResults);

            List<TopManagerDTO> result = contractService.getTopManagers();

            assertNotNull(result);
            assertEquals(3, result.size());
            assertEquals("John Doe", result.get(0).getManagerName());
            assertEquals(15L, result.get(0).getContractsCount());

            verify(contractsRepository, times(1)).findTopManagers(any(Pageable.class));
        }

        @Test
        @Order(49)
        @DisplayName("Should zero-fill all 12 months when no contracts were started in the window")
        void shouldReturnEmptyListWhenNoTimelineData() {

            when(contractsRepository.countContractsByMonth(any(LocalDate.class)))
                    .thenReturn(List.of());

            List<ContractsTimelineDTO> result = contractService.getContractsTimeline();

            assertNotNull(result);
            assertEquals(12, result.size());
            assertTrue(result.stream().allMatch(dto -> dto.getCount() == 0L));

            verify(contractsRepository, times(1)).countContractsByMonth(any(LocalDate.class));
        }

        @Test
        @Order(50)
        @DisplayName("Get all contracts as MANAGER with no manager assigned returns empty list")
        void shouldReturnEmptyListWhenManagerHasNoManagerAssigned() {
            Roles managerRole = Roles.builder().role("MANAGER").build();
            Users managerUser = Users.builder()
                    .username("unassigned")
                    .role(managerRole)
                    .manager(null)
                    .build();

            mockAuthentication("unassigned", "MANAGER");
            when(usersRepository.findByUsername("unassigned")).thenReturn(Optional.of(managerUser));

            List<ContractDTO> result = contractService.getAllContracts();

            assertTrue(result.isEmpty());
        }

        @Test
        @Order(57)
        @DisplayName("createContract with TenantContext sets organization on entity")
        void shouldCreateContractWithOrgFilter() {
            Contracts entity = Contracts.builder().id(1L).customerName("OrgClient").build();
            ContractDTO dto = new ContractDTO(1L, "OrgClient", "CTR-ORG", null, null,
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), null, 1L, null, null, null, null, 0);

            TenantContext.set(10L);
            try {
                when(contractMapper.toEntity(any(ContractDTO.class))).thenReturn(entity);
                when(contractsRepository.save(entity)).thenReturn(entity);
                when(contractMapper.toDTO(entity)).thenReturn(dto);

                ContractDTO result = contractService.createContract(dto);

                assertEquals("OrgClient", result.customerName());
                org.junit.jupiter.api.Assertions.assertNotNull(entity.getOrganization());
                assertEquals(10L, entity.getOrganization().getId());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(58)
        @DisplayName("createContract throws IllegalArgumentException when the mapped entity is null")
        void shouldRejectNullContractData() {
            when(contractMapper.toEntity(null)).thenReturn(null);

            assertThrows(IllegalArgumentException.class, () -> contractService.createContract(null));
            verify(contractsRepository, never()).save(any());
        }

        @Test
        @Order(52)
        @DisplayName("getContractStats with TenantContext uses org-filtered repository methods")
        void shouldGetContractStatsWithOrgFilter() {
            TenantContext.set(1L);
            try {
                when(contractsRepository.countAllContractsByOrg(1L)).thenReturn(10);
                when(contractsRepository.countActiveContractsByOrg(1L)).thenReturn(5);
                when(contractsRepository.countExpiringContractsByOrg(any(LocalDate.class), eq(1L))).thenReturn(2);
                when(contractsRepository.countExpiredContractsByOrg(1L)).thenReturn(3);
                when(contractsRepository.countDraftContractsByOrg(1L)).thenReturn(1);

                ContractStatsResponse result = contractService.getContractStats();

                assertEquals(10, result.getTotal());
                assertEquals(5, result.getActive());
                assertEquals(1, result.getDraft());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(53)
        @DisplayName("getExpiringContracts with TenantContext uses org-filtered query")
        void shouldGetExpiringContractsWithOrgFilter() {
            TenantContext.set(2L);
            try {
                Contracts c = Contracts.builder().id(1L).customerName("Org Client").build();
                ContractDTO dto = new ContractDTO(1L, "Org Client", "C001", null, null,
                        ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), null, 1L, null, null, null, null, 5);
                when(contractsRepository.findExpiringContractsByOrg(any(LocalDate.class), any(LocalDate.class), eq(2L)))
                        .thenReturn(List.of(c));
                when(contractMapper.toDTO(c)).thenReturn(dto);

                List<ContractDTO> result = contractService.getExpiringContracts(30);

                assertEquals(1, result.size());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(54)
        @DisplayName("getContractsByArea with TenantContext uses org-filtered query")
        void shouldGetContractsByAreaWithOrgFilter() {
            TenantContext.set(3L);
            try {
                when(contractsRepository.countContractsByAreaAndOrg(3L))
                        .thenReturn(List.of(new ContractsByAreaDTO("IT", 5L)));

                List<ContractsByAreaDTO> result = contractService.getContractsByArea();

                assertEquals(1, result.size());
                verify(contractsRepository).countContractsByAreaAndOrg(3L);
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(55)
        @DisplayName("getContractsTimeline with TenantContext uses org-filtered query")
        void shouldGetContractsTimelineWithOrgFilter() {
            TenantContext.set(4L);
            try {
                YearMonth currentMonth = YearMonth.now(ZoneId.systemDefault());
                java.util.ArrayList<Object[]> rows = new java.util.ArrayList<>();
                rows.add(new Object[]{currentMonth.getYear(), currentMonth.getMonthValue(), 3L});
                when(contractsRepository.countContractsByMonthAndOrg(any(LocalDate.class), eq(4L)))
                        .thenReturn(rows);

                List<ContractsTimelineDTO> result = contractService.getContractsTimeline();

                assertNotNull(result);
                assertEquals(12, result.size());
                assertEquals(3L, result.get(11).getCount());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(56)
        @DisplayName("getTopManagers with TenantContext uses org-filtered query")
        void shouldGetTopManagersWithOrgFilter() {
            TenantContext.set(5L);
            try {
                when(contractsRepository.findTopManagersByOrg(any(Pageable.class), eq(5L)))
                        .thenReturn(List.of(new TopManagerDTO(1L, "Mario", 10L)));

                List<TopManagerDTO> result = contractService.getTopManagers();

                assertEquals(1, result.size());
                verify(contractsRepository).findTopManagersByOrg(any(Pageable.class), eq(5L));
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(51)
        @DisplayName("Get contracts by status as MANAGER with no manager assigned returns empty list")
        void shouldReturnEmptyListByStatusWhenManagerHasNoManagerAssigned() {
            Roles managerRole = Roles.builder().role("MANAGER").build();
            Users managerUser = Users.builder()
                    .username("unassigned2")
                    .role(managerRole)
                    .manager(null)
                    .build();

            mockAuthentication("unassigned2", "MANAGER");
            when(usersRepository.findByUsername("unassigned2")).thenReturn(Optional.of(managerUser));

            List<ContractDTO> result = contractService.getContractsByStatus(ContractStatus.ACTIVE);

            assertTrue(result.isEmpty());
        }

        @Test
        @Order(57)
        @DisplayName("getContractById with TenantContext uses org-scoped repository")
        void shouldGetContractByIdWithTenantContext() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();
            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

            Contracts contract = Contracts.builder().id(1L).contractNumber("ABC123").customerName("Mario").build();
            ContractDTO dto = new ContractDTO(1L, "Mario", "ABC123", "WBS001", "Progetto",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(10), 1L, 1L, null, null, null, null);

            TenantContext.set(11L);
            try {
                when(contractsRepository.findByIdAndOrganization_Id(1L, 11L)).thenReturn(Optional.of(contract));
                when(contractMapper.toDTO(contract)).thenReturn(dto);

                ContractDTO result = contractService.getContractById(1L);

                assertEquals("Mario", result.customerName());
                verify(contractsRepository).findByIdAndOrganization_Id(1L, 11L);
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(58)
        @DisplayName("getAllContracts as ADMIN with TenantContext uses org-scoped repository")
        void shouldGetAllContractsWithOrgFilter() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();
            Contracts entity = Contracts.builder().id(1L).customerName("OrgClient").build();
            ContractDTO dto = new ContractDTO(1L, "OrgClient", "C001", null, null,
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), null, 1L, null, null, null, null, 0);

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

            TenantContext.set(20L);
            try {
                when(contractsRepository.findByOrganization_Id(20L)).thenReturn(List.of(entity));
                when(contractMapper.toDTO(entity)).thenReturn(dto);

                List<ContractDTO> result = contractService.getAllContracts();

                assertEquals(1, result.size());
                verify(contractsRepository).findByOrganization_Id(20L);
                verify(contractsRepository, never()).findAll();
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(59)
        @DisplayName("getContractsByStatus as ADMIN with TenantContext uses org-scoped repository")
        void shouldGetContractsByStatusWithOrgFilter() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();
            Contracts entity = Contracts.builder().id(1L).customerName("OrgClient").build();
            ContractDTO dto = new ContractDTO(1L, "OrgClient", "C001", null, null,
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), null, 1L, null, null, null, null, 0);

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

            TenantContext.set(21L);
            try {
                when(contractsRepository.findByStatusAndOrganization_Id(ContractStatus.ACTIVE, 21L))
                        .thenReturn(List.of(entity));
                when(contractMapper.toDTO(entity)).thenReturn(dto);

                List<ContractDTO> result = contractService.getContractsByStatus(ContractStatus.ACTIVE);

                assertEquals(1, result.size());
                verify(contractsRepository).findByStatusAndOrganization_Id(ContractStatus.ACTIVE, 21L);
                verify(contractsRepository, never()).findByStatus(any(ContractStatus.class));
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(60)
        @DisplayName("searchPaged as ADMIN with TenantContext and no filter uses org-scoped repository")
        void shouldSearchPagedAdminWithOrgFilterNoTerm() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();
            Contracts contract = Contracts.builder().id(1L).customerName("OrgClient").build();
            ContractDTO dto = new ContractDTO(1L, "OrgClient", "C001", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);
            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

            TenantContext.set(22L);
            try {
                when(contractsRepository.findByOrganization_Id(eq(22L), any(Pageable.class))).thenReturn(page);
                when(contractMapper.toDTO(contract)).thenReturn(dto);

                Page<ContractDTO> result = contractService.searchPaged(null, null, 0, 10);

                assertEquals(1, result.getTotalElements());
                verify(contractsRepository).findByOrganization_Id(eq(22L), any(Pageable.class));
                verify(contractsRepository, never()).findAllBy(any(Pageable.class));
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(61)
        @DisplayName("searchPaged as ADMIN with TenantContext and status filter uses org-scoped repository")
        void shouldSearchPagedAdminWithOrgFilterAndStatus() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();
            Contracts contract = Contracts.builder().id(1L).status(ContractStatus.ACTIVE).build();
            ContractDTO dto = new ContractDTO(1L, "Client", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);
            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

            TenantContext.set(23L);
            try {
                when(contractsRepository.findByStatusAndOrganization_Id(eq(ContractStatus.ACTIVE), eq(23L), any(Pageable.class)))
                        .thenReturn(page);
                when(contractMapper.toDTO(contract)).thenReturn(dto);

                Page<ContractDTO> result = contractService.searchPaged(null, ContractStatus.ACTIVE, 0, 10);

                assertEquals(1, result.getTotalElements());
                verify(contractsRepository).findByStatusAndOrganization_Id(eq(ContractStatus.ACTIVE), eq(23L), any(Pageable.class));
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(62)
        @DisplayName("searchPaged as ADMIN with TenantContext and search term uses org-scoped repository")
        void shouldSearchPagedAdminWithOrgFilterAndTerm() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();
            Contracts contract = Contracts.builder().id(1L).customerName("TestClient").build();
            ContractDTO dto = new ContractDTO(1L, "TestClient", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);
            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

            TenantContext.set(24L);
            try {
                when(contractsRepository.findByOrgAndTerm(eq(24L), eq("test"), any(Pageable.class))).thenReturn(page);
                when(contractMapper.toDTO(contract)).thenReturn(dto);

                Page<ContractDTO> result = contractService.searchPaged("test", null, 0, 10);

                assertEquals(1, result.getTotalElements());
                verify(contractsRepository).findByOrgAndTerm(eq(24L), eq("test"), any(Pageable.class));
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(63)
        @DisplayName("searchPaged as ADMIN with TenantContext, status and term uses org-scoped repository")
        void shouldSearchPagedAdminWithOrgFilterStatusAndTerm() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();
            Contracts contract = Contracts.builder().id(1L).customerName("TestClient").build();
            ContractDTO dto = new ContractDTO(1L, "TestClient", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.of(2027, Month.JUNE, 15), LocalDate.of(2027, Month.JUNE, 15).plusDays(30), 1L, 1L, null, null, null, null);
            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

            TenantContext.set(25L);
            try {
                when(contractsRepository.findByOrgAndStatusAndTerm(eq(25L), eq(ContractStatus.ACTIVE), eq("test"), any(Pageable.class)))
                        .thenReturn(page);
                when(contractMapper.toDTO(contract)).thenReturn(dto);

                Page<ContractDTO> result = contractService.searchPaged("test", ContractStatus.ACTIVE, 0, 10);

                assertEquals(1, result.getTotalElements());
                verify(contractsRepository).findByOrgAndStatusAndTerm(eq(25L), eq(ContractStatus.ACTIVE), eq("test"), any(Pageable.class));
            } finally {
                TenantContext.clear();
            }
        }
    }
}
