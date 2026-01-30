package com.donatodev.bcm_backend.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.FinancialValueDTO;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.FinancialValueNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.FinancialValueMapper;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Unit tests for the {@link FinancialValueService} class.
 * <p>
 * This test class verifies the core business logic of financial value
 * operations, including access control, CRUD functionality, and exception
 * handling. It mocks dependencies like {@code FinancialValuesRepository},
 * {@code UsersRepository}, and {@code FinancialValueMapper} using Mockito and
 * uses {@code @ExtendWith(MockitoExtension.class)} to enable Spring-style unit
 * testing with security context support.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class FinancialValueServiceTest {

    private static final String TEST_PASSWORD = "pwd";

    @Mock
    private FinancialValuesRepository valuesRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private FinancialValueMapper mapper;

    @InjectMocks
    private FinancialValueService service;

    @AfterEach
    @SuppressWarnings("unused")
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: FinancialValueService")
    @SuppressWarnings("unused")
    class VerifyFinancialValueService {

        /**
         * Test: Should return all financial values when the authenticated user
         * is an ADMIN.
         */
        @Test
        @Order(1)
        @DisplayName("Get all values as ADMIN")
        void shouldGetAllValuesAsAdmin() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("admin")
                    .password(TEST_PASSWORD)
                    .roles("ADMIN")
                    .build();

            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();
            FinancialValues entity = FinancialValues.builder().id(1L).build();
            FinancialValueDTO dto = new FinancialValueDTO(1L, 1, 2024, 500.0, 1L, 1L, 1L, "Type", "Area", "Contract");

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(valuesRepository.findAll()).thenReturn(List.of(entity));
            when(mapper.toDTO(entity)).thenReturn(dto);

            List<FinancialValueDTO> result = service.getAllValues();

            assertEquals(1, result.size());
            assertEquals(500.0, result.get(0).financialAmount());
        }

        /**
         * Test: Should return the financial value DTO when searched by ID as
         * ADMIN.
         */
        @Test
        @Order(2)
        @DisplayName("Get value by ID returns DTO")
        void shouldGetValueById() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("admin")
                    .password(TEST_PASSWORD)
                    .roles("ADMIN")
                    .build();

            FinancialValues entity = FinancialValues.builder()
                    .id(1L)
                    .contract(Contracts.builder().manager(Managers.builder().id(1L).build()).build())
                    .build();

            FinancialValueDTO dto = new FinancialValueDTO(1L, 1, 2024, 1000.0, 1L, 1L, 1L, "Type", "Area", "Contract");
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(valuesRepository.findById(1L)).thenReturn(Optional.of(entity));
            when(mapper.toDTO(entity)).thenReturn(dto);

            FinancialValueDTO result = service.getValueById(1L);

            assertEquals(1000.0, result.financialAmount());
        }

        /**
         * Test: Should delete a financial value if found and user is ADMIN.
         */
        @Test
        @Order(3)
        @DisplayName("Delete value calls repository")
        void shouldDeleteValue() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("admin")
                    .password(TEST_PASSWORD)
                    .roles("ADMIN")
                    .build();

            FinancialValues entity = FinancialValues.builder()
                    .id(1L)
                    .contract(Contracts.builder().manager(Managers.builder().id(1L).build()).build())
                    .build();

            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(valuesRepository.findById(1L)).thenReturn(Optional.of(entity));

            service.deleteValue(1L);

            verify(valuesRepository).delete(entity);
        }

        /**
         * Test: Should throw FinancialValueNotFoundException if value is not
         * found by ID.
         */
        @ParameterizedTest
        @Order(4)
        @ValueSource(longs = {999L, 1000L})
        @DisplayName("Delete should throw FinancialValueNotFoundException for missing IDs")
        void shouldThrowWhenDeletingMissing(long id) {
            when(valuesRepository.findById(id)).thenReturn(Optional.empty());
            FinancialValueNotFoundException ex
                    = assertThrows(FinancialValueNotFoundException.class, () -> service.deleteValue(id));
            assertEquals("Financial value ID " + id + " not found", ex.getMessage());
        }

        /**
         * Test: Should create a new financial value and return its DTO.
         */
        @Test
        @Order(5)
        @DisplayName("Create value returns saved DTO")
        void shouldCreateValue() {
            FinancialValueDTO dto = new FinancialValueDTO(null, 1, 2024, 300.0, 1L, 1L, 1L, "Type", "Area", "Contract");
            FinancialValues entity = FinancialValues.builder().build();
            FinancialValues saved = FinancialValues.builder().id(1L).build();
            FinancialValueDTO savedDTO = new FinancialValueDTO(1L, 1, 2024, 300.0, 1L, 1L, 1L, "Type", "Area", "Contract");

            when(mapper.toEntity(dto)).thenReturn(entity);
            when(valuesRepository.save(entity)).thenReturn(saved);
            when(mapper.toDTO(saved)).thenReturn(savedDTO);

            FinancialValueDTO result = service.createValue(dto);

            assertEquals(1L, result.id());
            assertEquals(300.0, result.financialAmount());
        }

        /**
         * Test: Should return only the manager's own financial values when user
         * is MANAGER.
         */
        @Test
        @Order(6)
        @DisplayName("Get all values as MANAGER shows only own contracts")
        void shouldGetAllValuesAsManager() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("manager1")
                    .password(TEST_PASSWORD)
                    .roles("MANAGER")
                    .build();

            Managers manager = Managers.builder().id(10L).build();
            Users managerUser = Users.builder()
                    .username("manager1")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            FinancialValues value = FinancialValues.builder()
                    .id(1L)
                    .contract(Contracts.builder().manager(manager).build())
                    .build();

            FinancialValueDTO dto = new FinancialValueDTO(1L, 1, 2024, 800.0, 1L, 1L, 1L, "Type", "Area", "Contract");

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(managerUser));
            when(valuesRepository.findByContract_Manager_Id(10L)).thenReturn(List.of(value));
            when(mapper.toDTO(value)).thenReturn(dto);

            List<FinancialValueDTO> result = service.getAllValues();
            assertEquals(1, result.size());
            assertEquals(800.0, result.get(0).financialAmount());
        }

        /**
         * Test: Should throw SecurityException if a MANAGER tries to access a
         * value not assigned to them.
         */
        @Test
        @Order(7)
        @DisplayName("Get value by ID throws if manager is not owner")
        void shouldDenyAccessIfManagerNotOwner() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("managerX")
                    .password(TEST_PASSWORD)
                    .roles("MANAGER")
                    .build();

            // Value owned by manager ID 99
            FinancialValues entity = FinancialValues.builder()
                    .id(1L)
                    .contract(Contracts.builder().manager(Managers.builder().id(99L).build()).build())
                    .build();

            // Authenticated user is manager with ID 100
            Users managerUser = Users.builder()
                    .username("managerX")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(Managers.builder().id(100L).build())
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("managerX")).thenReturn(Optional.of(managerUser));
            when(valuesRepository.findById(1L)).thenReturn(Optional.of(entity));

            SecurityException ex = assertThrows(SecurityException.class, () -> service.getValueById(1L));
            assertTrue(ex.getMessage().contains("Access denied"));
        }

        @Test
        @Order(8)
        @DisplayName("getAuthenticatedUsername should return null if authentication is null")
        void shouldReturnNullIfAuthenticationIsNull() {
            SecurityContextHolder.clearContext();
            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> service.getAllValues());
            assertEquals("User not found", ex.getMessage());
        }

        /**
         * Test: Should throw UserNotFoundException if no authentication context
         * is set.
         */
        @Test
        @Order(9)
        @DisplayName("getAuthenticatedUsername should return principal.toString() if not UserDetails")
        void shouldReturnPrincipalToStringIfNotUserDetails() {
            Authentication auth = new UsernamePasswordAuthenticationToken("rawPrincipal", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);

            String result = invokePrivateGetAuthenticatedUsername(service);
            assertEquals("rawPrincipal", result);
        }

        /**
         * Test: Should throw the right exception when user not found in
         * getAllValues.
         */
        @Test
        @Order(10)
        @DisplayName("getAllValues should throw UserNotFoundException if user not found")
        void shouldThrowIfUserNotFoundInGetAllValues() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("ghost")
                    .password(TEST_PASSWORD)
                    .roles("ADMIN")
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> service.getAllValues());
            assertEquals("User not found", ex.getMessage());
        }

        /**
         * Test: Should throw FinancialValueNotFoundException when a financial
         * value is not found by ID.
         */
        @Test
        @Order(11)
        @DisplayName("getValueById should throw if value not found")
        void shouldThrowIfValueNotFound() {
            when(valuesRepository.findById(999L)).thenReturn(Optional.empty());

            FinancialValueNotFoundException ex
                    = assertThrows(FinancialValueNotFoundException.class, () -> service.getValueById(999L));
            assertEquals("Financial value ID 999 not found", ex.getMessage());
        }

        /**
         * Test: Should throw SecurityException when a MANAGER tries to delete a
         * value not assigned to their contract.
         */
        @Test
        @Order(12)
        @DisplayName("deleteValue should throw if manager not assigned to contract")
        void shouldThrowOnDeleteIfManagerIsNotOwner() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("managerX")
                    .password(TEST_PASSWORD)
                    .roles("MANAGER")
                    .build();

            Users managerUser = Users.builder()
                    .username("managerX")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(Managers.builder().id(100L).build())
                    .build();

            FinancialValues entity = FinancialValues.builder()
                    .id(1L)
                    .contract(Contracts.builder().manager(Managers.builder().id(99L).build()).build())
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("managerX")).thenReturn(Optional.of(managerUser));
            when(valuesRepository.findById(1L)).thenReturn(Optional.of(entity));

            SecurityException ex = assertThrows(SecurityException.class, () -> service.deleteValue(1L));
            assertTrue(ex.getMessage().contains("Access denied"));
        }

        /**
         * Test: Should throw UserNotFoundException if authentication exists but
         * the user is not authenticated.
         */
        @Test
        @Order(13)
        @DisplayName("getAuthenticatedUsername should return null if not authenticated")
        void shouldReturnNullIfNotAuthenticated() {
            Authentication auth = mock(Authentication.class);
            when(auth.isAuthenticated()).thenReturn(false);

            SecurityContextHolder.getContext().setAuthentication(auth);

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> service.getAllValues());
            assertEquals("User not found", ex.getMessage());
        }

        /**
         * Test: Should throw UserNotFoundException when
         * checkAccessToFinancialValue is invoked and user is not found.
         */
        @Test
        @Order(14)
        @DisplayName("checkAccessToFinancialValue should throw if user not found")
        void shouldThrowUserNotFoundFromCheckAccess() throws Exception {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("ghostUser")
                    .password(TEST_PASSWORD)
                    .roles("MANAGER")
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("ghostUser")).thenReturn(Optional.empty());

            FinancialValues value = FinancialValues.builder()
                    .contract(Contracts.builder().manager(Managers.builder().id(1L).build()).build())
                    .build();

            Method method = FinancialValueService.class.getDeclaredMethod("checkAccessToFinancialValue", FinancialValues.class);
            method.setAccessible(true);

            InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(service, value));
            Throwable cause = thrown.getCause();
            assertTrue(cause instanceof UserNotFoundException);
        }

        /**
         * Test: Should throw SecurityException when updating a financial value
         * with a manager not assigned to the contract.
         */
        @Test
        @Order(15)
        @DisplayName("updateValue should throw SecurityException if manager not owner")
        void shouldDenyUpdateIfManagerNotAssigned() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("managerX")
                    .password(TEST_PASSWORD)
                    .roles("MANAGER")
                    .build();

            Users managerUser = Users.builder()
                    .username("managerX")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(Managers.builder().id(100L).build())
                    .build();

            FinancialValues entity = FinancialValues.builder()
                    .id(1L)
                    .contract(Contracts.builder().manager(Managers.builder().id(99L).build()).build())
                    .build();

            FinancialValueDTO dto = new FinancialValueDTO(1L, 1, 2024, 100.0, 1L, 1L, 1L, "Type", "Area", "Contract");

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("managerX")).thenReturn(Optional.of(managerUser));
            when(valuesRepository.findById(1L)).thenReturn(Optional.of(entity));

            SecurityException ex = assertThrows(SecurityException.class, () -> service.updateValue(1L, dto));
            assertTrue(ex.getMessage().contains("Access denied"));
        }

        /**
         * Test: Should throw FinancialValueNotFoundException if updating a
         * financial value that does not exist.
         */
        @Test
        @Order(16)
        @DisplayName("updateValue should throw FinancialValueNotFoundException if ID not found")
        void shouldThrowFinancialValueNotFoundException() {
            when(valuesRepository.findById(999L)).thenReturn(Optional.empty());

            FinancialValueDTO dto = new FinancialValueDTO(999L, 1, 2024, 100.0, 1L, 1L, 1L, "Type", "Area", "Contract");

            FinancialValueNotFoundException ex
                    = assertThrows(FinancialValueNotFoundException.class, () -> service.updateValue(999L, dto));
            assertEquals("Financial value ID 999 not found", ex.getMessage());
        }

        /**
         * Test: Should throw FinancialValueNotFoundException when deleting a
         * financial value that does not exist.
         */
        @Test
        @Order(17)
        @DisplayName("deleteValue should throw FinancialValueNotFoundException if ID not found")
        void shouldThrowWhenDeletingNonexistentValue() {
            when(valuesRepository.findById(999L)).thenReturn(Optional.empty());

            FinancialValueNotFoundException ex
                    = assertThrows(FinancialValueNotFoundException.class, () -> service.deleteValue(999L));
            assertEquals("Financial value ID 999 not found", ex.getMessage());
        }

        /**
         * Test: Should throw UserNotFoundException if username is not found in
         * the user repository.
         */
        @Test
        @Order(18)
        @DisplayName("getAllValues should throw UserNotFoundException if username not found")
        void shouldThrowUserNotFoundWithoutReflection() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("ghost")
                    .password(TEST_PASSWORD)
                    .roles("ADMIN")
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> service.getAllValues());
            assertTrue(ex.getMessage().contains("User not found"));
        }

        /**
         * Test: Should allow manager to access financial value when the
         * contract is assigned to them.
         */
        @Test
        @Order(19)
        @DisplayName("checkAccessToFinancialValue should NOT throw if manager is assigned")
        void shouldAllowManagerAccessToOwnContract() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("manager1")
                    .password(TEST_PASSWORD)
                    .roles("MANAGER")
                    .build();

            Long sharedId = 10L;

            Users managerUser = Users.builder()
                    .username("manager1")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(Managers.builder().id(sharedId).build())
                    .build();

            FinancialValues value = FinancialValues.builder()
                    .contract(Contracts.builder()
                            .manager(Managers.builder().id(sharedId).build())
                            .build())
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(managerUser));

            assertDoesNotThrow(() -> {
                Method method = FinancialValueService.class.getDeclaredMethod("checkAccessToFinancialValue", FinancialValues.class);
                method.setAccessible(true);
                method.invoke(service, value);
            });
        }

        private String invokePrivateGetAuthenticatedUsername(FinancialValueService service) {
            try {
                Method method = FinancialValueService.class.getDeclaredMethod("getAuthenticatedUsername");
                method.setAccessible(true);
                return (String) method.invoke(service);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getTargetException());
            }
        }

        /**
         * Test: Should return null when principal is null in authentication.
         */
        @Test
        @Order(20)
        @DisplayName("getAuthenticatedUsername should return null when principal is null")
        void shouldReturnNullWhenPrincipalIsNull() {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(null, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);

            // This will cause getAllValues to throw UserNotFoundException because username will be null
            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> service.getAllValues());
            assertEquals("User not found", ex.getMessage());
        }
    }
}
