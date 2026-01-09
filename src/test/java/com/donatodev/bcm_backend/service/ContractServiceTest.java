package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.ManagerNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.ContractMapper;
import com.donatodev.bcm_backend.repository.ContractManagerRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
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
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), 2L, 3L);
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
         * Tests retrieving a contract by its ID returns the corresponding DTO.
         */
        @Test
        @Order(2)
        @DisplayName("Get contract by ID returns DTO")
        void shouldGetContractById() {
            Contracts contract = Contracts.builder().id(1L).contractNumber("ABC123").customerName("Mario").build();
            ContractDTO dto = new ContractDTO(1L, "Mario", "ABC123", "WBS001", "Progetto",
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(10), 1L, 1L);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            ContractDTO result = contractService.getContractById(1L);

            assertEquals("Mario", result.customerName());
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
            assertEquals("Contract ID 999 not found", ex.getMessage());
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
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(10), 1L, 1L);

            Contracts entity = Contracts.builder().customerName("NewClient").build();
            Contracts saved = Contracts.builder().id(1L).customerName("NewClient").build();
            ContractDTO savedDTO = new ContractDTO(1L, "NewClient", "ABC123", "WBS001", "NewProject",
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(10), 1L, 1L);

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
            Contracts existing = Contracts.builder()
                    .id(1L)
                    .customerName("OldClient")
                    .contractNumber("OLD123")
                    .wbsCode("OLDWBS")
                    .projectName("OldProject")
                    .status(ContractStatus.ACTIVE)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusDays(10))
                    .build();

            ContractDTO updateDTO = new ContractDTO(1L, "UpdatedClient", "NEW123", "WBSNEW", "NewProject",
                    ContractStatus.EXPIRED, LocalDate.now(), LocalDate.now().plusDays(5), 1L, 1L);

            when(contractsRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(contractsRepository.save(existing)).thenReturn(existing);
            when(contractMapper.toDTO(existing)).thenReturn(updateDTO);

            ContractDTO result = contractService.updateContract(1L, updateDTO);

            assertEquals("UpdatedClient", result.customerName());
            assertEquals("NEW123", result.contractNumber());
        }

        /**
         * Tests that the repository's deleteById method is called when deleting
         * a contract.
         */
        @Test
        @Order(6)
        @DisplayName("Delete contract calls repository")
        void shouldDeleteContract() {
            contractService.deleteContract(1L);
            verify(contractsRepository, times(1)).deleteById(1L);
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
            assertEquals("User not found", ex.getMessage());
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
                    LocalDate.now(), LocalDate.now().plusDays(30), 5L, 1L);

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
                    LocalDate.now(), LocalDate.now().plusDays(20), 7L, 1L);

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
            assertEquals("No authenticated user", ex.getMessage());
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
            assertEquals("No authenticated user", ex.getMessage());
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
            assertEquals("No authenticated user", ex.getMessage());
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
            assertEquals("No authenticated user", ex.getMessage());
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
            assertEquals("User not found", ex.getMessage());
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
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(5), 1L, 1L);

            when(contractsRepository.findById(contractId)).thenReturn(Optional.empty());

            ContractNotFoundException ex
                    = assertThrows(ContractNotFoundException.class, () -> contractService.updateContract(contractId, dto));
            assertEquals("Contract not found", ex.getMessage());
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
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), 1L, 1L);

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

            ContractStatsResponse result = contractService.getContractStats();

            assertEquals(100, result.getTotal());
            assertEquals(50, result.getActive());
            assertEquals(30, result.getExpiring());
            assertEquals(20, result.getExpired());
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
            assertTrue(ex.getMessage().contains("Contract not found"));
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
            assertTrue(ex.getMessage().contains("Manager not found"));
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
            assertTrue(ex.getMessage().contains("Contract not found"));
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
            assertTrue(ex.getMessage().contains("Contract not found"));
        }

        @Test
        @Order(27)
        @DisplayName("Search paged as ADMIN without filters returns all contracts")
        void shouldSearchPagedAdminWithoutFilters() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            Contracts contract = Contracts.builder().id(1L).customerName("Client").build();
            ContractDTO dto = new ContractDTO(1L, "Client", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), 1L, 1L);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("admin", "ADMIN");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findAllBy(any(Pageable.class))).thenReturn(page);
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            Page<ContractDTO> result = contractService.searchPaged(null, null, 0, 10);

            assertEquals(1, result.getTotalElements());
            verify(contractsRepository).findAllBy(any(Pageable.class));
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
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), 1L, 1L);

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
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), 1L, 1L);

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
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), 1L, 1L);

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

        @Test
        @Order(31)
        @DisplayName("Search paged as MANAGER without filters")
        void shouldSearchPagedManagerWithoutFilters() {
            Managers manager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            Contracts contract = Contracts.builder().id(1L).customerName("Client").build();
            ContractDTO dto = new ContractDTO(1L, "Client", "C123", "WBS", "Proj",
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), 5L, 1L);

            Page<Contracts> page = new PageImpl<>(List.of(contract));

            mockAuthentication("manager", "MANAGER");
            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(contractsRepository.findByManagerId(eq(5L), any(Pageable.class))).thenReturn(page);
            when(contractMapper.toDTO(contract)).thenReturn(dto);

            Page<ContractDTO> result = contractService.searchPaged(null, null, 0, 10);

            assertEquals(1, result.getTotalElements());
            verify(contractsRepository).findByManagerId(eq(5L), any(Pageable.class));
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
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), 5L, 1L);

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
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), 5L, 1L);

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
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), 5L, 1L);

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
                    ContractStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(30), 1L, 1L);

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
    }
}
