package com.donatodev.bcm_backend.service;

import java.time.LocalDateTime;
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

    @Mock
    private ContractHistoryRepository historyRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private ContractHistoryMapper historyMapper;

    @InjectMocks
    private ContractHistoryService historyService;

    @AfterEach
    @SuppressWarnings("unused")
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
            ContractHistory entity = ContractHistory.builder().id(1L)
                    .contract(Contracts.builder().id(1L).build())
                    .modifiedBy(Users.builder().id(2L).build())
                    .modificationDate(LocalDateTime.now())
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.EXPIRED)
                    .build();
            ContractHistoryDTO dto = new ContractHistoryDTO(1L, 1L, 2L, LocalDateTime.now(), ContractStatus.ACTIVE, ContractStatus.EXPIRED);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            new User("admin", "pwd", List.of(() -> "ROLE_ADMIN")), null, List.of(() -> "ROLE_ADMIN")
                    )
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(historyRepository.findAll()).thenReturn(List.of(entity));
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
                    .id(1L)
                    .contract(Contracts.builder().id(1L).manager(Managers.builder().id(1L).build()).build())
                    .modifiedBy(Users.builder().id(2L).build())
                    .modificationDate(LocalDateTime.now())
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.CANCELLED)
                    .build();
            ContractHistoryDTO dto = new ContractHistoryDTO(1L, 1L, 2L, LocalDateTime.now(), ContractStatus.ACTIVE, ContractStatus.CANCELLED);

            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            new User("admin", "pwd", List.of(() -> "ROLE_ADMIN")), null, List.of(() -> "ROLE_ADMIN")
                    )
            );

            when(historyRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(historyMapper.toDTO(entity)).thenReturn(dto);

            ContractHistoryDTO result = historyService.getById(1L);
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
                    .id(1L)
                    .contract(Contracts.builder().manager(Managers.builder().id(1L).build()).build())
                    .modifiedBy(Users.builder().id(2L).build())
                    .modificationDate(LocalDateTime.now())
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.CANCELLED)
                    .build();

            Users manager = Users.builder()
                    .username("manager1")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(Managers.builder().id(2L).build())
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(new User("manager1", "pwd", List.of()), null)
            );

            when(historyRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(manager));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> historyService.getById(1L));
            assertNotNull(ex);
        }

        /**
         * Tests the creation of a new contract history record.
         */
        @Test
        @Order(4)
        @DisplayName("Create history returns saved DTO")
        void shouldCreateHistory() {
            ContractHistoryDTO dto = new ContractHistoryDTO(null, 1L, 2L, LocalDateTime.now(), ContractStatus.ACTIVE, ContractStatus.CANCELLED);
            ContractHistory entity = ContractHistory.builder()
                    .contract(Contracts.builder().id(1L).build())
                    .modifiedBy(Users.builder().id(2L).build())
                    .modificationDate(LocalDateTime.now())
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.CANCELLED)
                    .build();
            ContractHistory saved = ContractHistory.builder()
                    .id(1L)
                    .contract(Contracts.builder().id(1L).build())
                    .modifiedBy(Users.builder().id(2L).build())
                    .modificationDate(LocalDateTime.now())
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.CANCELLED)
                    .build();
            ContractHistoryDTO savedDTO = new ContractHistoryDTO(1L, 1L, 2L, LocalDateTime.now(), ContractStatus.ACTIVE, ContractStatus.CANCELLED);

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
            when(historyRepository.existsById(1L)).thenReturn(true);
            historyService.delete(1L);
            verify(historyRepository).deleteById(1L);
        }

        /**
         * Tests that deleting a non-existent contract history record
         */
        @Test
        @Order(6)
        @DisplayName("Delete history throws if not found")
        void shouldThrowWhenDeletingNonExistent() {
            when(historyRepository.existsById(999L)).thenReturn(false);
            ContractHistoryNotFoundException ex = assertThrows(ContractHistoryNotFoundException.class, () -> historyService.delete(999L));
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
                    .id(1L)
                    .contract(Contracts.builder().id(1L).build())
                    .modifiedBy(Users.builder().id(2L).build())
                    .modificationDate(LocalDateTime.now())
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.EXPIRED)
                    .build();

            ContractHistoryDTO dto = new ContractHistoryDTO(1L, 1L, 2L, LocalDateTime.now(), ContractStatus.ACTIVE, ContractStatus.EXPIRED);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            new User("admin", "pwd", List.of(() -> "ROLE_ADMIN")),
                            null,
                            List.of(() -> "ROLE_ADMIN")
                    )
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(historyRepository.findByContractId(1L)).thenReturn(List.of(entity));
            when(historyMapper.toDTO(entity)).thenReturn(dto);

            List<ContractHistoryDTO> result = historyService.getByContractId(1L);
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
                    .manager(Managers.builder().id(2L).build())
                    .build();

            ContractHistory entity = ContractHistory.builder()
                    .id(1L)
                    .contract(Contracts.builder().id(1L)
                            .manager(Managers.builder().id(999L).build())
                            .build())
                    .modifiedBy(Users.builder().id(2L).build())
                    .modificationDate(LocalDateTime.now())
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.CANCELLED)
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(new User("manager1", "pwd", List.of()), null)
            );

            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(manager));
            when(historyRepository.findByContractId(1L)).thenReturn(List.of(entity));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> historyService.getByContractId(1L));
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
            Managers manager = Managers.builder().id(2L).build();
            Users user = Users.builder()
                    .username("manager1")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            ContractHistory entity = ContractHistory.builder()
                    .id(1L)
                    .contract(Contracts.builder().id(1L).manager(manager).build())
                    .modifiedBy(Users.builder().id(2L).build())
                    .modificationDate(LocalDateTime.now())
                    .previousStatus(ContractStatus.ACTIVE)
                    .newStatus(ContractStatus.CANCELLED)
                    .build();

            ContractHistoryDTO dto = new ContractHistoryDTO(1L, 1L, 2L, LocalDateTime.now(), ContractStatus.ACTIVE, ContractStatus.CANCELLED);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager1", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(user));
            when(historyRepository.findByContractId(1L)).thenReturn(List.of(entity));
            when(historyMapper.toDTO(entity)).thenReturn(dto);

            List<ContractHistoryDTO> result = historyService.getByContractId(1L);

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
            Managers manager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            ContractHistory entity = ContractHistory.builder()
                    .id(1L)
                    .contract(Contracts.builder().id(1L).manager(manager).build())
                    .build();

            ContractHistoryDTO dto = new ContractHistoryDTO(1L, 1L, null, null, null, null);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(historyRepository.findAll()).thenReturn(List.of(entity));
            when(historyMapper.toDTO(entity)).thenReturn(dto);

            List<ContractHistoryDTO> result = historyService.getAll();
            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).id());
        }

        /**
         * Tests that a MANAGER can access a specific contract history if they
         * are assigned to the contract.
         */
        @Test
        @Order(11)
        @DisplayName("Get history by ID as MANAGER authorized")
        void shouldGetByIdAsManager() {
            Managers manager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            ContractHistory entity = ContractHistory.builder()
                    .id(1L)
                    .contract(Contracts.builder().id(1L).manager(manager).build())
                    .build();

            ContractHistoryDTO dto = new ContractHistoryDTO(1L, 1L, null, null, null, null);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(historyRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(historyMapper.toDTO(entity)).thenReturn(dto);

            ContractHistoryDTO result = historyService.getById(1L);
            assertEquals(1L, result.id());
        }

        /**
         * Tests that a MANAGER receives an exception when trying to access
         * contract histories of a contract they do not own.
         */
        @Test
        @Order(12)
        @DisplayName("Get histories by contract ID where manager is not owner (denied)")
        void shouldThrowIfGetByContractIdIsOwnerFalse() {
            Managers wrongManager = Managers.builder().id(9L).build();
            Users managerUser = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(wrongManager)
                    .build();

            ContractHistory h1 = ContractHistory.builder()
                    .contract(Contracts.builder().manager(Managers.builder().id(5L).build()).build())
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(historyRepository.findByContractId(1L)).thenReturn(List.of(h1));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> historyService.getByContractId(1L));
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
            Managers wrongManager = Managers.builder().id(3L).build();
            Managers historyManager = Managers.builder().id(99L).build();
            Users user = Users.builder()
                    .username("manager")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(wrongManager)
                    .build();

            ContractHistory entity = ContractHistory.builder()
                    .id(1L)
                    .contract(Contracts.builder().manager(historyManager).build())
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(user));
            when(historyRepository.findById(1L)).thenReturn(Optional.of(entity));

            RuntimeException ex = assertThrows(RuntimeException.class, () -> historyService.getById(1L));
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
            ContractHistory entity = ContractHistory.builder().id(1L).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("ghost", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(historyRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> historyService.getById(1L));
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

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> historyService.getByContractId(1L));
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
    }
}
