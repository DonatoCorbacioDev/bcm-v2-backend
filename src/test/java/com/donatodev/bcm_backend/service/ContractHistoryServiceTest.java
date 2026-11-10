package com.donatodev.bcm_backend.service;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ContractHistoryDTO;
import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractHistoryNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.ContractHistoryMapper;
import com.donatodev.bcm_backend.repository.ContractHistoryRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Unit tests for {@link ContractHistoryService}.
 * <p>
 * Covers CRUD operations, access control based on user roles (ADMIN and
 * MANAGER), and exception handling related to contract history records.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ContractHistoryServiceTest {

    /** The organization every request carries (its token's); a test that needs another sets its own. */
    private static final UUID TENANT = uid(1);

    @BeforeEach
    void enterTenant() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    @SuppressWarnings("unused")
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Mock
    private ContractHistoryRepository historyRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private ContractHistoryMapper historyMapper;

    @Mock private ContractAccessGuard contractAccessGuard;

    @InjectMocks
    private ContractHistoryService historyService;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: ContractHistoryService")
    @SuppressWarnings("unused")
    class VerifyContractHistoryService {

        /**
         * Tests that an ADMIN user can retrieve all contract history records.
         */
        @Test
        @Order(1)
        @DisplayName("Get all histories as ADMIN")
        void shouldGetAllAsAdmin() {
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();
            ContractHistory entity = ContractHistory.builder().id(uid(1))
                    .contract(Contracts.builder().id(uid(1)).build())
                    .modifiedBy(Users.builder().id(uid(2)).build())
                    .modificationDate(LocalDateTime.of(2027, Month.JUNE, 15, 12, 0))
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.EXPIRED)
                    .build();
            ContractHistoryDTO dto = new ContractHistoryDTO(uid(1), uid(1), uid(2), LocalDateTime.of(2027, Month.JUNE, 15, 12, 0), ContractStatus.ACTIVE, ContractStatus.EXPIRED);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            new User("admin", "pwd", List.of(() -> "ROLE_ADMIN")), null, List.of(() -> "ROLE_ADMIN")
                    )
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(historyRepository.findByContract_Organization_Id(TENANT)).thenReturn(List.of(entity));
            when(historyMapper.toDTO(entity)).thenReturn(dto);

            List<ContractHistoryDTO> result = historyService.getAll();
            assertEquals(1, result.size());
            assertEquals(ContractStatus.EXPIRED, result.get(0).newStatus());
        }

        /**
         * Tests that an ADMIN user can retrieve a contract history by its ID.
         */
        @Test
        @Order(2)
        @DisplayName("Get history by ID as ADMIN")
        void shouldGetByIdAsAdmin() {
            ContractHistory entity = ContractHistory.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().id(uid(1)).manager(Managers.builder().id(uid(1)).build()).build())
                    .modifiedBy(Users.builder().id(uid(2)).build())
                    .modificationDate(LocalDateTime.of(2027, Month.JUNE, 15, 12, 0))
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.CANCELLED)
                    .build();
            ContractHistoryDTO dto = new ContractHistoryDTO(uid(1), uid(1), uid(2), LocalDateTime.of(2027, Month.JUNE, 15, 12, 0), ContractStatus.ACTIVE, ContractStatus.CANCELLED);

            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            new User("admin", "pwd", List.of(() -> "ROLE_ADMIN")), null, List.of(() -> "ROLE_ADMIN")
                    )
            );

            when(historyRepository.findByIdAndContract_Organization_Id(uid(1), TENANT)).thenReturn(Optional.of(entity));
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(historyMapper.toDTO(entity)).thenReturn(dto);

            ContractHistoryDTO result = historyService.getById(uid(1));
            assertEquals(ContractStatus.CANCELLED, result.newStatus());
        }

        /**
         * Tests that a MANAGER user cannot access a contract history record
         */
        @Test
        @Order(3)
        @DisplayName("Get history by ID throws if not authorized")
        void shouldThrowIfManagerNotAuthorized() {
            ContractHistory entity = ContractHistory.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(1)).build()).build())
                    .modifiedBy(Users.builder().id(uid(2)).build())
                    .modificationDate(LocalDateTime.of(2027, Month.JUNE, 15, 12, 0))
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.CANCELLED)
                    .build();

            Users manager = Users.builder()
                    .username("manager1")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(Managers.builder().id(uid(2)).build())
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(new User("manager1", "pwd", List.of()), null)
            );

            when(historyRepository.findByIdAndContract_Organization_Id(uid(1), TENANT)).thenReturn(Optional.of(entity));
            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(manager));

            UUID id1 = uid(1);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> historyService.getById(id1));
            assertNotNull(ex);
        }

        /**
         * Tests the creation of a new contract history record.
         */
        @Test
        @Order(4)
        @DisplayName("Create history returns saved DTO")
        void shouldCreateHistory() {
            ContractHistoryDTO dto = new ContractHistoryDTO(null, uid(1), uid(2), LocalDateTime.of(2027, Month.JUNE, 15, 12, 0), ContractStatus.ACTIVE, ContractStatus.CANCELLED);
            ContractHistory entity = ContractHistory.builder()
                    .contract(Contracts.builder().id(uid(1)).build())
                    .modifiedBy(Users.builder().id(uid(2)).build())
                    .modificationDate(LocalDateTime.of(2027, Month.JUNE, 15, 12, 0))
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.CANCELLED)
                    .build();
            ContractHistory saved = ContractHistory.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().id(uid(1)).build())
                    .modifiedBy(Users.builder().id(uid(2)).build())
                    .modificationDate(LocalDateTime.of(2027, Month.JUNE, 15, 12, 0))
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.CANCELLED)
                    .build();
            ContractHistoryDTO savedDTO = new ContractHistoryDTO(uid(1), uid(1), uid(2), LocalDateTime.of(2027, Month.JUNE, 15, 12, 0), ContractStatus.ACTIVE, ContractStatus.CANCELLED);

            when(historyMapper.toEntity(dto)).thenReturn(entity);
            when(historyRepository.save(entity)).thenReturn(saved);
            when(historyMapper.toDTO(saved)).thenReturn(savedDTO);

            ContractHistoryDTO result = historyService.create(dto);
            assertEquals(ContractStatus.CANCELLED, result.newStatus());
        }

        /**
         * Tests the deletion of a contract history record by ID.
         */
        @Test
        @Order(5)
        @DisplayName("Delete history removes record")
        void shouldDeleteHistory() {
            ContractHistory entity = ContractHistory.builder().id(uid(1)).build();
            when(historyRepository.findByIdAndContract_Organization_Id(uid(1), TENANT)).thenReturn(Optional.of(entity));
            historyService.delete(uid(1));
            verify(historyRepository).delete(entity);
        }

        /**
         * Tests that deleting a non-existent contract history record
         */
        @Test
        @Order(6)
        @DisplayName("Delete history throws if not found")
        void shouldThrowWhenDeletingNonExistent() {
            when(historyRepository.findByIdAndContract_Organization_Id(uid(999), TENANT)).thenReturn(Optional.empty());
            UUID id999 = uid(999);
            ContractHistoryNotFoundException ex = assertThrows(ContractHistoryNotFoundException.class, () -> historyService.delete(id999));
            assertNotNull(ex);
        }

        /**
         * Tests that an ADMIN user can retrieve contract history entries
         */
        @Test
        @Order(7)
        @DisplayName("Get histories by contract ID as ADMIN")
        void shouldGetByContractIdAsAdmin() {
            Users admin = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();

            ContractHistory entity = ContractHistory.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().id(uid(1)).build())
                    .modifiedBy(Users.builder().id(uid(2)).build())
                    .modificationDate(LocalDateTime.of(2027, Month.JUNE, 15, 12, 0))
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.EXPIRED)
                    .build();

            ContractHistoryDTO dto = new ContractHistoryDTO(uid(1), uid(1), uid(2), LocalDateTime.of(2027, Month.JUNE, 15, 12, 0), ContractStatus.ACTIVE, ContractStatus.EXPIRED);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            new User("admin", "pwd", List.of(() -> "ROLE_ADMIN")),
                            null,
                            List.of(() -> "ROLE_ADMIN")
                    )
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractAccessGuard.getContractInScope(uid(1))).thenReturn(entity.getContract());
            when(historyRepository.findByContractIdAndContract_Organization_Id(uid(1), TENANT)).thenReturn(List.of(entity));
            when(historyMapper.toDTO(entity)).thenReturn(dto);

            List<ContractHistoryDTO> result = historyService.getByContractId(uid(1));
            assertEquals(1, result.size());
            assertEquals(ContractStatus.EXPIRED, result.get(0).newStatus());
        }

        /**
         * Tests that a MANAGER cannot access contract history by contract ID
         */
        @Test
        @Order(8)
        @DisplayName("Get histories by contract ID throws if manager not authorized")
        void shouldThrowIfManagerNotOwner() {
            Users manager = Users.builder()
                    .username("manager1")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(Managers.builder().id(uid(2)).build())
                    .build();

            ContractHistory entity = ContractHistory.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().id(uid(1))
                            .manager(Managers.builder().id(uid(999)).build())
                            .build())
                    .modifiedBy(Users.builder().id(uid(2)).build())
                    .modificationDate(LocalDateTime.of(2027, Month.JUNE, 15, 12, 0))
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.CANCELLED)
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(new User("manager1", "pwd", List.of()), null)
            );

            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(manager));
            when(contractAccessGuard.getContractInScope(uid(1))).thenReturn(entity.getContract());

            UUID id1 = uid(1);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> historyService.getByContractId(id1));
            assertNotNull(ex);
        }

        /**
         * Tests that a MANAGER can retrieve contract history entries by
         * contract ID if they are the owner of the contract.
         */
        @Test
        @Order(9)
        @DisplayName("Get histories by contract ID as MANAGER")
        void shouldGetByContractIdAsManager() {
            Managers manager = Managers.builder().id(uid(2)).build();
            Users user = Users.builder()
                    .username("manager1")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            ContractHistory entity = ContractHistory.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().id(uid(1)).manager(manager).build())
                    .modifiedBy(Users.builder().id(uid(2)).build())
                    .modificationDate(LocalDateTime.of(2027, Month.JUNE, 15, 12, 0))
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.CANCELLED)
                    .build();

            ContractHistoryDTO dto = new ContractHistoryDTO(uid(1), uid(1), uid(2), LocalDateTime.of(2027, Month.JUNE, 15, 12, 0), ContractStatus.ACTIVE, ContractStatus.CANCELLED);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager1", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(user));
            when(contractAccessGuard.getContractInScope(uid(1))).thenReturn(entity.getContract());
            when(historyRepository.findByContractIdAndContract_Organization_Id(uid(1), TENANT)).thenReturn(List.of(entity));
            when(historyMapper.toDTO(entity)).thenReturn(dto);

            List<ContractHistoryDTO> result = historyService.getByContractId(uid(1));

            assertEquals(1, result.size());
            assertEquals(ContractStatus.CANCELLED, result.get(0).newStatus());
        }

        /**
         * Tests that a MANAGER can retrieve all contract histories where the
         * contracts belong to them.
         */
        @Test
        @Order(10)
        @DisplayName("Get all histories as MANAGER")
        void shouldGetAllAsManager() {
            Managers manager = Managers.builder().id(uid(5)).build();
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            ContractHistory entity = ContractHistory.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().id(uid(1)).manager(manager).build())
                    .build();

            ContractHistoryDTO dto = new ContractHistoryDTO(uid(1), uid(1), null, null, null, null);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(historyRepository.findByContractManagerId(uid(5))).thenReturn(List.of(entity));
            when(historyMapper.toDTO(entity)).thenReturn(dto);

            List<ContractHistoryDTO> result = historyService.getAll();
            assertEquals(1, result.size());
            assertEquals(uid(1), result.get(0).id());
        }

        @Test
        @Order(10)
        @DisplayName("Get all histories as MANAGER without a manager profile returns an empty list")
        void shouldReturnEmptyHistoryForManagerWithoutProfile() {
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager", null, List.of(() -> "ROLE_MANAGER"))
            );
            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));

            assertEquals(List.of(), historyService.getAll());
            org.mockito.Mockito.verifyNoInteractions(historyRepository);
        }

        /**
         * Tests that a MANAGER can access a specific contract history if they
         * are assigned to the contract.
         */
        @Test
        @Order(11)
        @DisplayName("Get history by ID as MANAGER authorized")
        void shouldGetByIdAsManager() {
            Managers manager = Managers.builder().id(uid(5)).build();
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            ContractHistory entity = ContractHistory.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().id(uid(1)).manager(manager).build())
                    .build();

            ContractHistoryDTO dto = new ContractHistoryDTO(uid(1), uid(1), null, null, null, null);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(historyRepository.findByIdAndContract_Organization_Id(uid(1), TENANT)).thenReturn(Optional.of(entity));
            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(historyMapper.toDTO(entity)).thenReturn(dto);

            ContractHistoryDTO result = historyService.getById(uid(1));
            assertEquals(uid(1), result.id());
        }

        /**
         * Tests that a MANAGER receives an exception when trying to access
         * contract histories of a contract they do not own.
         */
        @Test
        @Order(12)
        @DisplayName("Get histories by contract ID where manager is not owner (denied)")
        void shouldThrowIfGetByContractIdIsOwnerFalse() {
            Managers wrongManager = Managers.builder().id(uid(9)).build();
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(wrongManager)
                    .build();

            ContractHistory h1 = ContractHistory.builder()
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(5)).build()).build())
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(contractAccessGuard.getContractInScope(uid(1))).thenReturn(h1.getContract());

            UUID id1 = uid(1);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> historyService.getByContractId(id1));
            assertNotNull(ex);
        }

        /**
         * Tests that the service throws UserNotFoundException when the
         * authenticated username does not exist in the database.
         */
        @Test
        @Order(13)
        @DisplayName("Should throw UserNotFoundException if user not found")
        void shouldThrowUserNotFoundException() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("ghost", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> historyService.getAll());
            assertNotNull(ex);
        }

        /**
         * Tests that a MANAGER cannot access a contract history record if the
         * contract is managed by a different manager.
         */
        @Test
        @Order(14)
        @DisplayName("Should throw if manager tries to access history by ID not his")
        void shouldThrowIfManagerAccessesOtherHistory() {
            Managers wrongManager = Managers.builder().id(uid(3)).build();
            Managers historyManager = Managers.builder().id(uid(99)).build();
            Users user = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(wrongManager)
                    .build();

            ContractHistory entity = ContractHistory.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().manager(historyManager).build())
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(user));
            when(historyRepository.findByIdAndContract_Organization_Id(uid(1), TENANT)).thenReturn(Optional.of(entity));

            UUID id1 = uid(1);
            RuntimeException ex = assertThrows(RuntimeException.class, () -> historyService.getById(id1));
            assertNotNull(ex);
        }

        /**
         * Tests that the service throws UserNotFoundException if the user is
         * not authenticated or if the authentication object is null or
         * explicitly unauthenticated.
         */
        @Test
        @Order(15)
        @DisplayName("Should throw UserNotFoundException if authentication is null or not authenticated")
        void shouldHandleInvalidAuthentication() {
            SecurityContextHolder.clearContext();
            UserNotFoundException ex1 = assertThrows(UserNotFoundException.class, () -> historyService.getAll());
            assertNotNull(ex1);

            UsernamePasswordAuthenticationToken token
                    = new UsernamePasswordAuthenticationToken("user", null, List.of());
            token.setAuthenticated(false);
            SecurityContextHolder.getContext().setAuthentication(token);

            UserNotFoundException ex2 = assertThrows(UserNotFoundException.class, () -> historyService.getAll());
            assertNotNull(ex2);
        }

        /**
         * Tests that getById throws UserNotFoundException when the
         * authenticated user does not exist in the system.
         */
        @Test
        @Order(16)
        @DisplayName("Should throw UserNotFoundException in getById if user not found")
        void shouldThrowUserNotFoundInGetById() {
            ContractHistory entity = ContractHistory.builder().id(uid(1)).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("ghost", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(historyRepository.findByIdAndContract_Organization_Id(uid(1), TENANT)).thenReturn(Optional.of(entity));
            when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UUID id1 = uid(1);
            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> historyService.getById(id1));
            assertNotNull(ex);
        }

        /**
         * Tests that getByContractId throws UserNotFoundException when the
         * authenticated user does not exist in the database.
         */
        @Test
        @Order(17)
        @DisplayName("Should throw UserNotFoundException in getByContractId if user not found")
        void shouldThrowUserNotFoundInGetByContractId() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("ghost", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UUID id1 = uid(1);
            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> historyService.getByContractId(id1));
            assertNotNull(ex);
        }

        /**
         * Test: Should return null when principal is null in authentication.
         */
        @Test
        @Order(18)
        @DisplayName("getAuthenticatedUsername should return null when principal is null")
        void shouldReturnNullWhenPrincipalIsNull() {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(null, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);

            // This should trigger UserNotFoundException when calling getAll()
            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> historyService.getAll());
            assertNotNull(ex);
        }

        @Test
        @Order(19)
        @DisplayName("getAll with TenantContext uses org-filtered repository for ADMIN")
        void shouldGetAllWithTenantContext() {
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();
            ContractHistory entity = ContractHistory.builder().id(uid(1))
                    .contract(Contracts.builder().id(uid(1)).build())
                    .build();
            ContractHistoryDTO dto = new ContractHistoryDTO(uid(1), uid(1), null, null, null, null);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            new User("admin", "pwd", List.of(() -> "ROLE_ADMIN")), null, List.of(() -> "ROLE_ADMIN")
                    )
            );

            TenantContext.set(uid(2));
            try {
                when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
                when(historyRepository.findByContract_Organization_Id(uid(2))).thenReturn(List.of(entity));
                when(historyMapper.toDTO(entity)).thenReturn(dto);

                List<ContractHistoryDTO> result = historyService.getAll();

                assertEquals(1, result.size());
                verify(historyRepository).findByContract_Organization_Id(uid(2));
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(20)
        @DisplayName("getById with TenantContext uses org-filtered repository")
        void shouldGetByIdWithTenantContext() {
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();
            ContractHistory entity = ContractHistory.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().id(uid(1)).manager(Managers.builder().id(uid(1)).build()).build())
                    .build();
            ContractHistoryDTO dto = new ContractHistoryDTO(uid(1), uid(1), null, null, null, null);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            new User("admin", "pwd", List.of(() -> "ROLE_ADMIN")), null, List.of(() -> "ROLE_ADMIN")
                    )
            );

            TenantContext.set(uid(2));
            try {
                when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
                when(historyRepository.findByIdAndContract_Organization_Id(uid(1), uid(2))).thenReturn(Optional.of(entity));
                when(historyMapper.toDTO(entity)).thenReturn(dto);

                ContractHistoryDTO result = historyService.getById(uid(1));

                assertEquals(uid(1), result.id());
                verify(historyRepository).findByIdAndContract_Organization_Id(uid(1), uid(2));
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(21)
        @DisplayName("getById with TenantContext throws if not found in organization")
        void shouldThrowGetByIdWithTenantContextWhenNotFound() {
            TenantContext.set(uid(2));
            try {
                when(historyRepository.findByIdAndContract_Organization_Id(uid(999), uid(2))).thenReturn(Optional.empty());

                UUID id999 = uid(999);
                ContractHistoryNotFoundException ex = assertThrows(ContractHistoryNotFoundException.class,
                        () -> historyService.getById(id999));
                assertEquals("Voce storico ID " + uid(999) + " non trovata", ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(22)
        @DisplayName("getByContractId with TenantContext uses org-filtered repository")
        void shouldGetByContractIdWithTenantContext() {
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();
            ContractHistory entity = ContractHistory.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().id(uid(1)).build())
                    .build();
            ContractHistoryDTO dto = new ContractHistoryDTO(uid(1), uid(1), null, null, null, null);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            new User("admin", "pwd", List.of(() -> "ROLE_ADMIN")), null, List.of(() -> "ROLE_ADMIN")
                    )
            );

            TenantContext.set(uid(2));
            try {
                when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
                when(contractAccessGuard.getContractInScope(uid(1))).thenReturn(entity.getContract());
            when(historyRepository.findByContractIdAndContract_Organization_Id(uid(1), uid(2))).thenReturn(List.of(entity));
                when(historyMapper.toDTO(entity)).thenReturn(dto);

                List<ContractHistoryDTO> result = historyService.getByContractId(uid(1));

                assertEquals(1, result.size());
                verify(historyRepository).findByContractIdAndContract_Organization_Id(uid(1), uid(2));
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(23)
        @DisplayName("delete with TenantContext uses org-filtered repository")
        void shouldDeleteWithTenantContext() {
            ContractHistory entity = ContractHistory.builder().id(uid(1)).build();

            TenantContext.set(uid(2));
            try {
                when(historyRepository.findByIdAndContract_Organization_Id(uid(1), uid(2))).thenReturn(Optional.of(entity));

                historyService.delete(uid(1));

                verify(historyRepository).delete(entity);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
