package com.donatodev.bcm_backend.service;

import static org.mockito.ArgumentMatchers.any;
import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import com.donatodev.bcm_backend.config.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.security.access.AccessDeniedException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.donatodev.bcm_backend.entity.FinancialCategory;
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

    private static final String TEST_PASSWORD = "pwd";

    @Mock
    private FinancialValuesRepository valuesRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private FinancialValueMapper mapper;

    @Mock
    private MlCacheService mlCacheService;

    @Mock
    private ContractAccessGuard contractAccessGuard;

    @InjectMocks
    private FinancialValueService service;

    @Test
    void shouldAuthorizeReplacementContractBeforeMutatingOrSavingValue() {
        Managers owner = Managers.builder().id(uid(7)).build();
        Contracts original = Contracts.builder().id(uid(1)).manager(owner).build();
        Contracts destination = Contracts.builder().id(uid(2)).build();
        FinancialValues value = FinancialValues.builder().id(uid(1)).contract(original)
                .month(1).year(2026).financialAmount(500).build();
        Users caller = Users.builder().username("manager").manager(owner)
                .role(Roles.builder().role("MANAGER").build()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", null, List.of()));
        when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(caller));
        when(valuesRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(value));
        when(contractAccessGuard.getContractInScope(uid(2))).thenReturn(destination);
        doThrow(new AccessDeniedException("non autorizzato ad accedere al contratto"))
                .when(contractAccessGuard).checkManagerCanAccess(destination);
        FinancialValueDTO dto = new FinancialValueDTO(null, 2, 2027, 999, uid(1), uid(1), uid(2),
                null, null, null, FinancialCategory.REVENUE);

        assertThrows(AccessDeniedException.class, () -> service.updateValue(uid(1), dto));

        assertEquals(500, value.getFinancialAmount());
        assertEquals(1, value.getMonth());
        assertEquals(2026, value.getYear());
        assertEquals(original, value.getContract());
        verifyNoInteractions(mapper, mlCacheService);
        verify(valuesRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotReadValuesWhenContractIsOutsideTenant() {
        when(contractAccessGuard.getContractInScope(uid(42))).thenThrow(
                new com.donatodev.bcm_backend.exception.ContractNotFoundException("Contratto non trovato"));

        assertThrows(com.donatodev.bcm_backend.exception.ContractNotFoundException.class,
                () -> service.getValuesByContractId(uid(42)));
        verifyNoInteractions(valuesRepository, mapper);
    }

    @Test
    void shouldNotReadValuesWhenManagerAccessIsDenied() {
        Contracts contract = Contracts.builder().id(uid(42)).build();
        when(contractAccessGuard.getContractInScope(uid(42))).thenReturn(contract);
        doThrow(new AccessDeniedException("non autorizzato ad accedere al contratto"))
                .when(contractAccessGuard).checkManagerCanAccess(contract);

        assertThrows(AccessDeniedException.class, () -> service.getValuesByContractId(uid(42)));
        verifyNoInteractions(valuesRepository, mapper);
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
            FinancialValues entity = FinancialValues.builder().id(uid(1)).build();
            FinancialValueDTO dto = new FinancialValueDTO(uid(1), 1, 2024, 500.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(valuesRepository.findAllByOrganizationId(TENANT)).thenReturn(List.of(entity));
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
                    .id(uid(1))
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(1)).build()).build())
                    .build();

            FinancialValueDTO dto = new FinancialValueDTO(uid(1), 1, 2024, 1000.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(valuesRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));
            when(mapper.toDTO(entity)).thenReturn(dto);

            FinancialValueDTO result = service.getValueById(uid(1));

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
                    .id(uid(1))
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(1)).build()).build())
                    .build();

            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(valuesRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));

            service.deleteValue(uid(1));

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
        void shouldThrowWhenDeletingMissing(long number) {
            UUID id = uid(number);
            when(valuesRepository.findByIdAndOrganizationId(id, TENANT)).thenReturn(Optional.empty());
            FinancialValueNotFoundException ex
                    = assertThrows(FinancialValueNotFoundException.class, () -> service.deleteValue(id));
            assertEquals("ID valore finanziario " + id + " non trovato", ex.getMessage());
        }

        /**
         * Test: Should create a new financial value and return its DTO.
         */
        @Test
        @Order(5)
        @DisplayName("Create value returns saved DTO")
        void shouldCreateValue() {
            FinancialValueDTO dto = new FinancialValueDTO(null, 1, 2024, 300.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);
            FinancialValues entity = FinancialValues.builder().build();
            FinancialValues saved = FinancialValues.builder().id(uid(1)).build();
            FinancialValueDTO savedDTO = new FinancialValueDTO(uid(1), 1, 2024, 300.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);

            when(mapper.toEntity(dto, TENANT)).thenReturn(entity);
            when(valuesRepository.save(entity)).thenReturn(saved);
            when(mapper.toDTO(saved)).thenReturn(savedDTO);

            FinancialValueDTO result = service.createValue(dto);

            assertEquals(uid(1), result.id());
            assertEquals(300.0, result.financialAmount());
        }

        @Test
        @Order(5)
        @DisplayName("createValue should throw AccessDeniedException if MANAGER is not assigned to contract")
        void shouldDenyCreateIfManagerNotAssigned() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("managerX")
                    .password(TEST_PASSWORD)
                    .roles("MANAGER")
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

            Managers contractManager = Managers.builder().id(uid(99)).build();
            Managers userManager = Managers.builder().id(uid(1)).build();
            Users managerUser = Users.builder()
                    .username("managerX")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(userManager)
                    .build();

            FinancialValueDTO dto = new FinancialValueDTO(null, 1, 2024, 300.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);
            FinancialValues entity = FinancialValues.builder()
                    .contract(Contracts.builder().manager(contractManager).build())
                    .build();

            when(mapper.toEntity(dto, TENANT)).thenReturn(entity);
            when(usersRepository.findByUsername("managerX")).thenReturn(Optional.of(managerUser));

            assertThrows(AccessDeniedException.class, () -> service.createValue(dto));
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

            Managers manager = Managers.builder().id(uid(10)).build();
            Users managerUser = Users.builder()
                    .username("manager1")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();

            FinancialValues value = FinancialValues.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().manager(manager).build())
                    .build();

            FinancialValueDTO dto = new FinancialValueDTO(uid(1), 1, 2024, 800.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("manager1")).thenReturn(Optional.of(managerUser));
            when(valuesRepository.findByContract_Manager_Id(uid(10))).thenReturn(List.of(value));
            when(mapper.toDTO(value)).thenReturn(dto);

            List<FinancialValueDTO> result = service.getAllValues();
            assertEquals(1, result.size());
            assertEquals(800.0, result.get(0).financialAmount());
        }

        /**
         * Test: Should throw AccessDeniedException if a MANAGER tries to access a
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
                    .id(uid(1))
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(99)).build()).build())
                    .build();

            // Authenticated user is manager with ID 100
            Users managerUser = Users.builder()
                    .username("managerX")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(Managers.builder().id(uid(100)).build())
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("managerX")).thenReturn(Optional.of(managerUser));
            when(valuesRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> service.getValueById(uid(1)));
            assertTrue(ex.getMessage().contains("non sei assegnato"));
        }

        /**
         * Test: a MANAGER can never access a value on an unassigned contract
         * (Contracts.manager is nullable) — regression test for the NPE this
         * null-guard fixes.
         */
        @Test
        @Order(38)
        @DisplayName("Get value by ID throws if the contract has no manager assigned")
        void shouldDenyAccessIfContractHasNoManager() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("managerX")
                    .password(TEST_PASSWORD)
                    .roles("MANAGER")
                    .build();

            FinancialValues entity = FinancialValues.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().manager(null).build())
                    .build();

            Users managerUser = Users.builder()
                    .username("managerX")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(Managers.builder().id(uid(100)).build())
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("managerX")).thenReturn(Optional.of(managerUser));
            when(valuesRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> service.getValueById(uid(1)));
            assertTrue(ex.getMessage().contains("non sei assegnato"));
        }

        @Test
        @Order(39)
        @DisplayName("Get value by ID succeeds when the MANAGER is the contract's own manager")
        void shouldAllowAccessIfManagerIsOwner() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("managerX")
                    .password(TEST_PASSWORD)
                    .roles("MANAGER")
                    .build();

            FinancialValues entity = FinancialValues.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(100)).build()).build())
                    .build();
            FinancialValueDTO dto = new FinancialValueDTO(uid(1), 1, 2024, 500.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);

            Users managerUser = Users.builder()
                    .username("managerX")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(Managers.builder().id(uid(100)).build())
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("managerX")).thenReturn(Optional.of(managerUser));
            when(valuesRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));
            when(mapper.toDTO(entity)).thenReturn(dto);

            FinancialValueDTO result = service.getValueById(uid(1));

            assertEquals(500.0, result.financialAmount());
        }

        @Test
        @Order(8)
        @DisplayName("getAuthenticatedUsername should return null if authentication is null")
        void shouldReturnNullIfAuthenticationIsNull() {
            SecurityContextHolder.clearContext();
            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> service.getAllValues());
            assertEquals("Utente non trovato", ex.getMessage());
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
            assertEquals("Utente non trovato", ex.getMessage());
        }

        /**
         * Test: Should throw FinancialValueNotFoundException when a financial
         * value is not found by ID.
         */
        @Test
        @Order(11)
        @DisplayName("getValueById should throw if value not found")
        void shouldThrowIfValueNotFound() {
            when(valuesRepository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());

            FinancialValueNotFoundException ex
                    = assertThrows(FinancialValueNotFoundException.class, () -> service.getValueById(uid(999)));
            assertEquals("ID valore finanziario " + uid(999) + " non trovato", ex.getMessage());
        }

        /**
         * Test: Should throw AccessDeniedException when a MANAGER tries to delete a
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
                    .manager(Managers.builder().id(uid(100)).build())
                    .build();

            FinancialValues entity = FinancialValues.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(99)).build()).build())
                    .build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("managerX")).thenReturn(Optional.of(managerUser));
            when(valuesRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> service.deleteValue(uid(1)));
            assertTrue(ex.getMessage().contains("non sei assegnato"));
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
            assertEquals("Utente non trovato", ex.getMessage());
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
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(1)).build()).build())
                    .build();

            Method method = FinancialValueService.class.getDeclaredMethod("checkAccessToFinancialValue", FinancialValues.class);
            method.setAccessible(true);

            InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(service, value));
            Throwable cause = thrown.getCause();
            assertTrue(cause instanceof UserNotFoundException);
        }

        /**
         * Test: Should update all fields including financial type when updateValue is called.
         */
        @Test
        @Order(15)
        @DisplayName("updateValue should update all fields including financialType")
        void shouldUpdateAllFieldsIncludingRelations() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("admin")
                    .password(TEST_PASSWORD)
                    .roles("ADMIN")
                    .build();

            FinancialValues entity = FinancialValues.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(1)).build()).build())
                    .build();
            FinancialValues saved = FinancialValues.builder().id(uid(1)).build();
            FinancialValueDTO dto = new FinancialValueDTO(uid(1), 3, 2025, 750.0, uid(2), uid(2), uid(2), "Sales", "Area2", "Contract2", FinancialCategory.REVENUE);
            FinancialValueDTO savedDTO = new FinancialValueDTO(uid(1), 3, 2025, 750.0, uid(2), uid(2), uid(2), "Sales", "Area2", "Contract2", FinancialCategory.REVENUE);
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(valuesRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));
            when(valuesRepository.save(entity)).thenReturn(saved);
            when(mapper.toDTO(saved)).thenReturn(savedDTO);

            FinancialValueDTO result = service.updateValue(uid(1), dto);

            verify(mapper).updateEntity(entity, dto, TENANT);
            verify(valuesRepository).save(entity);
            assertEquals(uid(2), result.financialTypeId());
            assertEquals(750.0, result.financialAmount());
        }

        /**
         * Test: Should throw SecurityException when updating a financial value
         * with a manager not assigned to the contract.
         */
        @Test
        @Order(16)
        @DisplayName("updateValue should throw AccessDeniedException if manager not owner")
        void shouldDenyUpdateIfManagerNotAssigned() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("managerX")
                    .password(TEST_PASSWORD)
                    .roles("MANAGER")
                    .build();

            Users managerUser = Users.builder()
                    .username("managerX")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(Managers.builder().id(uid(100)).build())
                    .build();

            FinancialValues entity = FinancialValues.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(99)).build()).build())
                    .build();

            FinancialValueDTO dto = new FinancialValueDTO(uid(1), 1, 2024, 100.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            when(usersRepository.findByUsername("managerX")).thenReturn(Optional.of(managerUser));
            when(valuesRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> service.updateValue(uid(1), dto));
            assertTrue(ex.getMessage().contains("non sei assegnato"));
        }

        /**
         * Test: Should throw FinancialValueNotFoundException if updating a
         * financial value that does not exist.
         */
        @Test
        @Order(17)
        @DisplayName("updateValue should throw FinancialValueNotFoundException if ID not found")
        void shouldThrowFinancialValueNotFoundException() {
            when(valuesRepository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());

            FinancialValueDTO dto = new FinancialValueDTO(uid(999), 1, 2024, 100.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);

            FinancialValueNotFoundException ex
                    = assertThrows(FinancialValueNotFoundException.class, () -> service.updateValue(uid(999), dto));
            assertEquals("ID valore finanziario " + uid(999) + " non trovato", ex.getMessage());
        }

        /**
         * Test: Should throw FinancialValueNotFoundException when deleting a
         * financial value that does not exist.
         */
        @Test
        @Order(18)
        @DisplayName("deleteValue should throw FinancialValueNotFoundException if ID not found")
        void shouldThrowWhenDeletingNonexistentValue() {
            when(valuesRepository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());

            FinancialValueNotFoundException ex
                    = assertThrows(FinancialValueNotFoundException.class, () -> service.deleteValue(uid(999)));
            assertEquals("ID valore finanziario " + uid(999) + " non trovato", ex.getMessage());
        }

        /**
         * Test: Should throw UserNotFoundException if username is not found in
         * the user repository.
         */
        @Test
        @Order(19)
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
            assertTrue(ex.getMessage().contains("Utente non trovato"));
        }

        /**
         * Test: Should allow manager to access financial value when the
         * contract is assigned to them.
         */
        @Test
        @Order(20)
        @DisplayName("checkAccessToFinancialValue should NOT throw if manager is assigned")
        void shouldAllowManagerAccessToOwnContract() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("manager1")
                    .password(TEST_PASSWORD)
                    .roles("MANAGER")
                    .build();

            UUID sharedId = uid(10);

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
        @Order(21)
        @DisplayName("getAuthenticatedUsername should return null when principal is null")
        void shouldReturnNullWhenPrincipalIsNull() {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(null, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> service.getAllValues());
            assertEquals("Utente non trovato", ex.getMessage());
        }

        @Test
        @Order(26)
        @DisplayName("createValue with TenantContext sets organization on entity")
        void shouldCreateValueWithOrgFilter() {
            FinancialValues entity = FinancialValues.builder().id(uid(1)).build();
            FinancialValues saved = FinancialValues.builder().id(uid(1)).build();
            FinancialValueDTO dto = new FinancialValueDTO(null, 1, 2024, 100.0, uid(1), uid(1), uid(1), "T", "A", "C", FinancialCategory.REVENUE);
            FinancialValueDTO savedDto = new FinancialValueDTO(uid(1), 1, 2024, 100.0, uid(1), uid(1), uid(1), "T", "A", "C", FinancialCategory.REVENUE);

            com.donatodev.bcm_backend.config.TenantContext.set(uid(8));
            try {
                when(mapper.toEntity(dto, uid(8))).thenReturn(entity);
                when(valuesRepository.save(entity)).thenReturn(saved);
                when(mapper.toDTO(saved)).thenReturn(savedDto);

                FinancialValueDTO result = service.createValue(dto);

                assertEquals(uid(1), result.id());
                org.junit.jupiter.api.Assertions.assertNotNull(entity.getOrganization());
                assertEquals(uid(8), entity.getOrganization().getId());
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(27)
        @DisplayName("getAuthenticatedUsername returns null when authentication is not authenticated")
        void shouldReturnNullWhenNotAuthenticated() {
            // Auth exists but isAuthenticated() returns false
            UsernamePasswordAuthenticationToken unauth =
                    new UsernamePasswordAuthenticationToken("user", "pass");
            // unauth.isAuthenticated() == false (no authorities)
            SecurityContextHolder.getContext().setAuthentication(unauth);

            String result = invokePrivateGetAuthenticatedUsername(service);

            org.junit.jupiter.api.Assertions.assertNull(result);
        }

        @Test
        @Order(22)
        @DisplayName("getAuthenticatedUsername returns null when SecurityContext has no authentication")
        void shouldReturnNullWhenNoAuthentication() {
            SecurityContextHolder.clearContext(); // no auth set at all

            String result = invokePrivateGetAuthenticatedUsername(service);

            org.junit.jupiter.api.Assertions.assertNull(result);
        }

        @Test
        @Order(23)
        @DisplayName("getAuthenticatedUsername returns toString when principal is a non-UserDetails String")
        void shouldReturnToStringWhenPrincipalIsString() {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken("plain_username", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);

            String result = invokePrivateGetAuthenticatedUsername(service);

            assertEquals("plain_username", result);
        }

        @Test
        @Order(24)
        @DisplayName("getAllValues with TenantContext uses org-filtered repository for ADMIN")
        void shouldGetAllValuesWithOrgFilter() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("admin").password("pwd").roles("ADMIN").build();
            Users admin = Users.builder().username("admin")
                    .role(Roles.builder().role("ADMIN").build()).build();
            FinancialValues entity = FinancialValues.builder().id(uid(1)).build();
            FinancialValueDTO dto = new FinancialValueDTO(uid(1), 1, 2024, 500.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
            com.donatodev.bcm_backend.config.TenantContext.set(uid(3));
            try {
                when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
                when(valuesRepository.findAllByOrganizationId(uid(3))).thenReturn(List.of(entity));
                when(mapper.toDTO(entity)).thenReturn(dto);

                List<FinancialValueDTO> result = service.getAllValues();

                assertEquals(1, result.size());
                verify(valuesRepository).findAllByOrganizationId(uid(3));
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(25)
        @DisplayName("getValuesByContractId with TenantContext uses org-filtered query")
        void shouldGetValuesByContractIdWithOrgFilter() {
            FinancialValues entity = FinancialValues.builder().id(uid(1)).build();
            FinancialValueDTO dto = new FinancialValueDTO(uid(1), 1, 2024, 100.0, uid(1), uid(1), uid(1), "T", "A", "C", FinancialCategory.REVENUE);

            com.donatodev.bcm_backend.config.TenantContext.set(uid(5));
            try {
                Contracts contract = Contracts.builder().id(uid(42)).build();
                when(contractAccessGuard.getContractInScope(uid(42))).thenReturn(contract);
                when(valuesRepository.findByContractIdAndOrganizationId(uid(42), uid(5)))
                        .thenReturn(List.of(entity));
                when(mapper.toDTO(entity)).thenReturn(dto);

                List<FinancialValueDTO> result = service.getValuesByContractId(uid(42));

                assertEquals(1, result.size());
                verify(valuesRepository).findByContractIdAndOrganizationId(uid(42), uid(5));
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(28)
        @DisplayName("getValueById with TenantContext uses org-filtered repository")
        void shouldGetValueByIdWithTenantContext() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("admin")
                    .password(TEST_PASSWORD)
                    .roles("ADMIN")
                    .build();

            FinancialValues entity = FinancialValues.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(1)).build()).build())
                    .build();

            FinancialValueDTO dto = new FinancialValueDTO(uid(1), 1, 2024, 1000.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            com.donatodev.bcm_backend.config.TenantContext.set(uid(8));
            try {
                when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
                when(valuesRepository.findByIdAndOrganizationId(uid(1), uid(8))).thenReturn(Optional.of(entity));
                when(mapper.toDTO(entity)).thenReturn(dto);

                FinancialValueDTO result = service.getValueById(uid(1));

                assertEquals(1000.0, result.financialAmount());
                verify(valuesRepository).findByIdAndOrganizationId(uid(1), uid(8));
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(29)
        @DisplayName("getValueById with TenantContext throws if not found in organization")
        void shouldThrowGetValueByIdWithTenantContextWhenNotFound() {
            com.donatodev.bcm_backend.config.TenantContext.set(uid(8));
            try {
                when(valuesRepository.findByIdAndOrganizationId(uid(999), uid(8))).thenReturn(Optional.empty());

                FinancialValueNotFoundException ex = assertThrows(FinancialValueNotFoundException.class,
                        () -> service.getValueById(uid(999)));
                assertEquals("ID valore finanziario " + uid(999) + " non trovato", ex.getMessage());
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(30)
        @DisplayName("deleteValue with TenantContext uses org-filtered repository")
        void shouldDeleteValueWithTenantContext() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("admin")
                    .password(TEST_PASSWORD)
                    .roles("ADMIN")
                    .build();

            FinancialValues entity = FinancialValues.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(1)).build()).build())
                    .build();

            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );

            com.donatodev.bcm_backend.config.TenantContext.set(uid(8));
            try {
                when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
                when(valuesRepository.findByIdAndOrganizationId(uid(1), uid(8))).thenReturn(Optional.of(entity));

                service.deleteValue(uid(1));

                verify(valuesRepository).delete(entity);
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        /*
         * ML cache invalidation (evictAllForOrg). None of createValue/updateValue/
         * deleteValue are @Transactional today, so each repository.save()/delete()
         * call is already its own committed transaction by the time it returns â€”
         * calling evictAllForOrg() right after is safe and equivalent to an
         * after-commit hook. (MlCacheService.evictAllForOrg itself carries
         * @Transactional, since the derived deleteByOrgId query needs an active
         * EntityManager transaction that no caller here provides â€” verified by
         * a real jakarta.persistence.TransactionRequiredException in end-to-end
         * testing before that annotation was added. This is internal to
         * MlCacheService and does not affect the transactionality of the three
         * methods below.) IMPORTANT: if these methods are ever made
         * @Transactional (e.g. to batch several writes atomically), the eviction
         * call must move to a post-commit hook (e.g.
         * TransactionSynchronizationManager.registerSynchronization(afterCommit))
         * instead of running inline, otherwise a rollback would leave the cache
         * wrongly invalidated for data that was never actually persisted.
         */

        @Test
        @Order(31)
        @DisplayName("createValue evicts the ML cache for the correct organization exactly once after save succeeds")
        void shouldEvictCacheOnCreateValue() {
            FinancialValueDTO dto = new FinancialValueDTO(null, 1, 2024, 300.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);
            FinancialValues entity = FinancialValues.builder().build();
            FinancialValues saved = FinancialValues.builder().id(uid(1)).build();
            FinancialValueDTO savedDTO = new FinancialValueDTO(uid(1), 1, 2024, 300.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);

            com.donatodev.bcm_backend.config.TenantContext.set(uid(36));
            try {
                when(mapper.toEntity(dto, uid(36))).thenReturn(entity);
                when(valuesRepository.save(entity)).thenReturn(saved);
                when(mapper.toDTO(saved)).thenReturn(savedDTO);

                service.createValue(dto);

                verify(mlCacheService, times(1)).evictAllForOrg(uid(36));
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(32)
        @DisplayName("updateValue evicts the ML cache for the correct organization exactly once after save succeeds")
        void shouldEvictCacheOnUpdateValue() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("admin")
                    .password(TEST_PASSWORD)
                    .roles("ADMIN")
                    .build();

            FinancialValues entity = FinancialValues.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(1)).build()).build())
                    .build();
            FinancialValues saved = FinancialValues.builder().id(uid(1)).build();
            FinancialValueDTO dto = new FinancialValueDTO(uid(1), 3, 2025, 750.0, uid(2), uid(2), uid(2), "Sales", "Area2", "Contract2", FinancialCategory.REVENUE);
            FinancialValueDTO savedDTO = new FinancialValueDTO(uid(1), 3, 2025, 750.0, uid(2), uid(2), uid(2), "Sales", "Area2", "Contract2", FinancialCategory.REVENUE);
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );
            com.donatodev.bcm_backend.config.TenantContext.set(uid(36));
            try {
                when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
                when(valuesRepository.findByIdAndOrganizationId(uid(1), uid(36))).thenReturn(Optional.of(entity));
                when(valuesRepository.save(entity)).thenReturn(saved);
                when(mapper.toDTO(saved)).thenReturn(savedDTO);

                service.updateValue(uid(1), dto);

                verify(mlCacheService, times(1)).evictAllForOrg(uid(36));
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(33)
        @DisplayName("deleteValue evicts the ML cache for the correct organization exactly once after delete succeeds")
        void shouldEvictCacheOnDeleteValue() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("admin")
                    .password(TEST_PASSWORD)
                    .roles("ADMIN")
                    .build();

            FinancialValues entity = FinancialValues.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(1)).build()).build())
                    .build();
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
            );
            com.donatodev.bcm_backend.config.TenantContext.set(uid(36));
            try {
                when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
                when(valuesRepository.findByIdAndOrganizationId(uid(1), uid(36))).thenReturn(Optional.of(entity));

                service.deleteValue(uid(1));

                verify(mlCacheService, times(1)).evictAllForOrg(uid(36));
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(34)
        @DisplayName("createValue does NOT evict the ML cache when the repository save fails")
        void shouldNotEvictCacheWhenCreateSaveFails() {
            FinancialValueDTO dto = new FinancialValueDTO(null, 1, 2024, 300.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);
            FinancialValues entity = FinancialValues.builder().build();

            when(mapper.toEntity(dto, TENANT)).thenReturn(entity);
            when(valuesRepository.save(entity)).thenThrow(new RuntimeException("db unavailable"));

            assertThrows(RuntimeException.class, () -> service.createValue(dto));

            verify(mlCacheService, never()).evictAllForOrg(any(UUID.class));
        }

        @Test
        @Order(35)
        @DisplayName("deleteValue does NOT evict the ML cache when the repository delete fails")
        void shouldNotEvictCacheWhenDeleteFails() {
            FinancialValues entity = FinancialValues.builder()
                    .id(uid(1))
                    .contract(Contracts.builder().manager(Managers.builder().id(uid(1)).build()).build())
                    .build();

            when(valuesRepository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));
            doThrow(new RuntimeException("db unavailable")).when(valuesRepository).delete(entity);

            assertThrows(RuntimeException.class, () -> service.deleteValue(uid(1)));

            verify(mlCacheService, never()).evictAllForOrg(any(UUID.class));
        }

        @Test
        @Order(36)
        @DisplayName("createValue does NOT evict the ML cache when access validation fails before the write")
        void shouldNotEvictCacheWhenCreateAccessDenied() {
            UserDetails userDetails = org.springframework.security.core.userdetails.User
                    .withUsername("managerX")
                    .password(TEST_PASSWORD)
                    .roles("MANAGER")
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));

            Managers contractManager = Managers.builder().id(uid(99)).build();
            Managers userManager = Managers.builder().id(uid(1)).build();
            Users managerUser = Users.builder()
                    .username("managerX")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(userManager)
                    .build();

            FinancialValueDTO dto = new FinancialValueDTO(null, 1, 2024, 300.0, uid(1), uid(1), uid(1), "Type", "Area", "Contract", FinancialCategory.REVENUE);
            FinancialValues entity = FinancialValues.builder()
                    .contract(Contracts.builder().manager(contractManager).build())
                    .build();

            when(mapper.toEntity(dto, TENANT)).thenReturn(entity);
            when(usersRepository.findByUsername("managerX")).thenReturn(Optional.of(managerUser));

            assertThrows(AccessDeniedException.class, () -> service.createValue(dto));

            verify(valuesRepository, never()).save(org.mockito.ArgumentMatchers.any());
            verifyNoInteractions(mlCacheService);
        }

        @Test
        @Order(37)
        @DisplayName("deleteValue does NOT evict the ML cache when the value lookup fails before the write")
        void shouldNotEvictCacheWhenDeleteNotFound() {
            when(valuesRepository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());

            assertThrows(FinancialValueNotFoundException.class, () -> service.deleteValue(uid(999)));

            verify(valuesRepository, never()).delete(org.mockito.ArgumentMatchers.any());
            verifyNoInteractions(mlCacheService);
        }
    }
}
