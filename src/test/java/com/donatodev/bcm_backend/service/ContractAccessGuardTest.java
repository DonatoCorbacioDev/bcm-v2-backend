package com.donatodev.bcm_backend.service;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Unit tests for {@link ContractAccessGuard} — the tenant-scoping and
 * manager-access logic shared by {@code ContractDocumentService} and
 * {@code ElectronicInvoiceService}.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ContractAccessGuardTest {

    /** The organization every request carries (its token's); a test that needs another sets its own. */
    private static final UUID TENANT = uid(1);

    @BeforeEach
    void enterTenant() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void leaveTenant() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Mock private ContractsRepository contractsRepository;
    @Mock private UsersRepository usersRepository;

    @InjectMocks
    private ContractAccessGuard contractAccessGuard;

    private static final UUID CONTRACT_ID = uid(1);

    private Contracts fakeContract() {
        Contracts c = new Contracts();
        c.setId(CONTRACT_ID);
        return c;
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("getContractInScope")
    @SuppressWarnings("unused")
    class GetContractInScope {

        @Test
        @Order(1)
        @DisplayName("uses org-scoped lookup when TenantContext carries an organization ID")
        void usesOrgScopedLookupWithTenantContext() {
            Contracts contract = fakeContract();
            TenantContext.set(uid(12));

            when(contractsRepository.findByIdAndOrganization_Id(CONTRACT_ID, uid(12))).thenReturn(Optional.of(contract));

            Contracts result = contractAccessGuard.getContractInScope(CONTRACT_ID);

            assertEquals(contract, result);
            verify(contractsRepository).findByIdAndOrganization_Id(CONTRACT_ID, uid(12));
        }

        @Test
        @Order(2)
        @DisplayName("refuses a lookup without an organization instead of searching every one")
        void refusesLookupWithoutTenant() {
            TenantContext.clear();

            assertThrows(com.donatodev.bcm_backend.exception.MissingTenantException.class,
                    () -> contractAccessGuard.getContractInScope(uid(1)));
            verify(contractsRepository, never()).findById(uid(1));
        }

        @Test
        @Order(3)
        @DisplayName("throws ContractNotFoundException when the contract does not exist")
        void throwsWhenContractMissing() {
            when(contractsRepository.findByIdAndOrganization_Id(CONTRACT_ID, TENANT)).thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> contractAccessGuard.getContractInScope(CONTRACT_ID));
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("checkManagerCanAccess")
    @SuppressWarnings("unused")
    class CheckManagerCanAccess {

        @Test
        @Order(1)
        @DisplayName("refuses when there is no authentication context (fails closed)")
        void refusesWithoutAuthentication() {
            Contracts contract = fakeContract();
            assertThrows(AccessDeniedException.class, () -> contractAccessGuard.checkManagerCanAccess(contract));
        }

        @Test
        @Order(2)
        @DisplayName("refuses an unauthenticated (but non-null) authentication")
        void refusesUnauthenticatedAuthentication() {
            User principal = new User("mgr", "x", List.of(() -> "ROLE_MANAGER"));
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            auth.setAuthenticated(false);
            SecurityContextHolder.getContext().setAuthentication(auth);

            Contracts contract = fakeContract();
            assertThrows(AccessDeniedException.class, () -> contractAccessGuard.checkManagerCanAccess(contract));
        }

        @Test
        @Order(3)
        @DisplayName("refuses the anonymous user")
        void refusesAnonymousUser() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("anonymousUser", null,
                            List.of(() -> "ROLE_ANONYMOUS")));

            Contracts contract = fakeContract();
            assertThrows(AccessDeniedException.class, () -> contractAccessGuard.checkManagerCanAccess(contract));
        }

        @Test
        @Order(4)
        @DisplayName("refuses when the request carries no organization")
        void refusesWithoutTenantContext() {
            User principal = new User("mgr", "x", List.of(() -> "ROLE_MANAGER"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

            TenantContext.clear();
            Contracts contract = fakeContract();
            assertThrows(AccessDeniedException.class, () -> contractAccessGuard.checkManagerCanAccess(contract));
        }

        @Test
        @Order(5)
        @DisplayName("allows a MANAGER assigned to the contract")
        void allowsManagerAssignedToContract() {
            Managers manager = Managers.builder().id(uid(7)).build();
            Contracts contract = fakeContract();
            contract.setManager(manager);

            Users managerUser = Users.builder()
                    .username("mgr")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();
            User principal = new User("mgr", "x", List.of(() -> "ROLE_MANAGER"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            TenantContext.set(uid(1));

            when(usersRepository.findByUsernameAndOrganizationId("mgr", uid(1))).thenReturn(Optional.of(managerUser));

            assertDoesNotThrow(() -> contractAccessGuard.checkManagerCanAccess(contract));
        }

        @Test
        @Order(6)
        @DisplayName("denies a MANAGER not assigned to the contract")
        void deniesManagerNotAssignedToContract() {
            Managers contractManager = Managers.builder().id(uid(99)).build();
            Managers userManager = Managers.builder().id(uid(7)).build();
            Contracts contract = fakeContract();
            contract.setManager(contractManager);

            Users managerUser = Users.builder()
                    .username("mgr")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(userManager)
                    .build();
            User principal = new User("mgr", "x", List.of(() -> "ROLE_MANAGER"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            TenantContext.set(uid(1));

            when(usersRepository.findByUsernameAndOrganizationId("mgr", uid(1))).thenReturn(Optional.of(managerUser));

            assertThrows(AccessDeniedException.class,
                    () -> contractAccessGuard.checkManagerCanAccess(contract));
        }

        @Test
        @Order(7)
        @DisplayName("allows ADMIN users regardless of contract assignment")
        void allowsAdminRegardlessOfAssignment() {
            Contracts contract = fakeContract();
            contract.setManager(Managers.builder().id(uid(99)).build());

            Users adminUser = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();
            User principal = new User("admin", "x", List.of(() -> "ROLE_ADMIN"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            TenantContext.set(uid(1));

            when(usersRepository.findByUsernameAndOrganizationId("admin", uid(1))).thenReturn(Optional.of(adminUser));

            assertDoesNotThrow(() -> contractAccessGuard.checkManagerCanAccess(contract));
        }

        @Test
        @Order(8)
        @DisplayName("refuses a user who is not in the organization")
        void refusesUserNotInOrganization() {
            User principal = new User("ghost", "x", List.of(() -> "ROLE_MANAGER"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            TenantContext.set(uid(1));

            when(usersRepository.findByUsernameAndOrganizationId("ghost", uid(1))).thenReturn(Optional.empty());

            Contracts contract = fakeContract();
            assertThrows(AccessDeniedException.class, () -> contractAccessGuard.checkManagerCanAccess(contract));
        }

        @Test
        @Order(9)
        @DisplayName("denies a MANAGER with no manager profile linked to their user")
        void deniesManagerWithoutLinkedProfile() {
            Contracts contract = fakeContract();
            contract.setManager(Managers.builder().id(uid(99)).build());

            Users managerUser = Users.builder()
                    .username("mgr")
                    .role(Roles.builder().role("MANAGER").build())
                    .build();
            User principal = new User("mgr", "x", List.of(() -> "ROLE_MANAGER"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            TenantContext.set(uid(1));

            when(usersRepository.findByUsernameAndOrganizationId("mgr", uid(1))).thenReturn(Optional.of(managerUser));

            assertThrows(AccessDeniedException.class,
                    () -> contractAccessGuard.checkManagerCanAccess(contract));
        }

        @Test
        @Order(10)
        @DisplayName("denies a MANAGER when the contract has no manager assigned at all")
        void deniesManagerWhenContractHasNoManager() {
            Managers userManager = Managers.builder().id(uid(7)).build();
            Contracts contract = fakeContract();

            Users managerUser = Users.builder()
                    .username("mgr")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(userManager)
                    .build();
            User principal = new User("mgr", "x", List.of(() -> "ROLE_MANAGER"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            TenantContext.set(uid(1));

            when(usersRepository.findByUsernameAndOrganizationId("mgr", uid(1))).thenReturn(Optional.of(managerUser));

            assertThrows(AccessDeniedException.class,
                    () -> contractAccessGuard.checkManagerCanAccess(contract));
        }
    }
}
