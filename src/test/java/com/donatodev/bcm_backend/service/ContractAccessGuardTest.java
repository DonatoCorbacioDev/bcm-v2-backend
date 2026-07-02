package com.donatodev.bcm_backend.service;

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

    @Mock private ContractsRepository contractsRepository;
    @Mock private UsersRepository usersRepository;

    @InjectMocks
    private ContractAccessGuard contractAccessGuard;

    private static final long CONTRACT_ID = 1L;

    private Contracts fakeContract() {
        Contracts c = new Contracts();
        c.setId(CONTRACT_ID);
        return c;
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
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
            TenantContext.set(12L);

            when(contractsRepository.findByIdAndOrganization_Id(CONTRACT_ID, 12L)).thenReturn(Optional.of(contract));

            Contracts result = contractAccessGuard.getContractInScope(CONTRACT_ID);

            assertEquals(contract, result);
            verify(contractsRepository).findByIdAndOrganization_Id(CONTRACT_ID, 12L);
        }

        @Test
        @Order(2)
        @DisplayName("falls back to unscoped lookup when TenantContext has no organization ID")
        void fallsBackToUnscopedLookupWithoutTenantContext() {
            Contracts contract = fakeContract();

            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));

            Contracts result = contractAccessGuard.getContractInScope(CONTRACT_ID);

            assertEquals(contract, result);
            verify(contractsRepository).findById(CONTRACT_ID);
        }

        @Test
        @Order(3)
        @DisplayName("throws ContractNotFoundException when the contract does not exist")
        void throwsWhenContractMissing() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.empty());

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
        @DisplayName("does nothing when there is no authentication context")
        void skipsCheckWhenUnauthenticated() {
            assertDoesNotThrow(() -> contractAccessGuard.checkManagerCanAccess(fakeContract()));
        }

        @Test
        @Order(2)
        @DisplayName("does nothing for an unauthenticated (but non-null) authentication")
        void skipsCheckWhenNotAuthenticated() {
            User principal = new User("mgr", "x", List.of(() -> "ROLE_MANAGER"));
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            auth.setAuthenticated(false);
            SecurityContextHolder.getContext().setAuthentication(auth);

            assertDoesNotThrow(() -> contractAccessGuard.checkManagerCanAccess(fakeContract()));
        }

        @Test
        @Order(3)
        @DisplayName("does nothing for the anonymous user")
        void skipsCheckForAnonymousUser() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("anonymousUser", null,
                            List.of(() -> "ROLE_ANONYMOUS")));

            assertDoesNotThrow(() -> contractAccessGuard.checkManagerCanAccess(fakeContract()));
        }

        @Test
        @Order(4)
        @DisplayName("does nothing when TenantContext has no organization ID")
        void skipsCheckWithoutTenantContext() {
            User principal = new User("mgr", "x", List.of(() -> "ROLE_MANAGER"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

            assertDoesNotThrow(() -> contractAccessGuard.checkManagerCanAccess(fakeContract()));
        }

        @Test
        @Order(5)
        @DisplayName("allows a MANAGER assigned to the contract")
        void allowsManagerAssignedToContract() {
            Managers manager = Managers.builder().id(7L).build();
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
            TenantContext.set(1L);

            when(usersRepository.findByUsernameAndOrganizationId("mgr", 1L)).thenReturn(Optional.of(managerUser));

            assertDoesNotThrow(() -> contractAccessGuard.checkManagerCanAccess(contract));
        }

        @Test
        @Order(6)
        @DisplayName("denies a MANAGER not assigned to the contract")
        void deniesManagerNotAssignedToContract() {
            Managers contractManager = Managers.builder().id(99L).build();
            Managers userManager = Managers.builder().id(7L).build();
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
            TenantContext.set(1L);

            when(usersRepository.findByUsernameAndOrganizationId("mgr", 1L)).thenReturn(Optional.of(managerUser));

            assertThrows(AccessDeniedException.class,
                    () -> contractAccessGuard.checkManagerCanAccess(contract));
        }

        @Test
        @Order(7)
        @DisplayName("allows ADMIN users regardless of contract assignment")
        void allowsAdminRegardlessOfAssignment() {
            Contracts contract = fakeContract();
            contract.setManager(Managers.builder().id(99L).build());

            Users adminUser = Users.builder()
                    .username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .build();
            User principal = new User("admin", "x", List.of(() -> "ROLE_ADMIN"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            TenantContext.set(1L);

            when(usersRepository.findByUsernameAndOrganizationId("admin", 1L)).thenReturn(Optional.of(adminUser));

            assertDoesNotThrow(() -> contractAccessGuard.checkManagerCanAccess(contract));
        }

        @Test
        @Order(8)
        @DisplayName("does nothing when the authenticated principal has no matching user record")
        void skipsCheckWhenUserNotFound() {
            User principal = new User("ghost", "x", List.of(() -> "ROLE_MANAGER"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            TenantContext.set(1L);

            when(usersRepository.findByUsernameAndOrganizationId("ghost", 1L)).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> contractAccessGuard.checkManagerCanAccess(fakeContract()));
        }

        @Test
        @Order(9)
        @DisplayName("denies a MANAGER with no manager profile linked to their user")
        void deniesManagerWithoutLinkedProfile() {
            Contracts contract = fakeContract();
            contract.setManager(Managers.builder().id(99L).build());

            Users managerUser = Users.builder()
                    .username("mgr")
                    .role(Roles.builder().role("MANAGER").build())
                    .build();
            User principal = new User("mgr", "x", List.of(() -> "ROLE_MANAGER"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            TenantContext.set(1L);

            when(usersRepository.findByUsernameAndOrganizationId("mgr", 1L)).thenReturn(Optional.of(managerUser));

            assertThrows(AccessDeniedException.class,
                    () -> contractAccessGuard.checkManagerCanAccess(contract));
        }

        @Test
        @Order(10)
        @DisplayName("denies a MANAGER when the contract has no manager assigned at all")
        void deniesManagerWhenContractHasNoManager() {
            Managers userManager = Managers.builder().id(7L).build();
            Contracts contract = fakeContract();

            Users managerUser = Users.builder()
                    .username("mgr")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(userManager)
                    .build();
            User principal = new User("mgr", "x", List.of(() -> "ROLE_MANAGER"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            TenantContext.set(1L);

            when(usersRepository.findByUsernameAndOrganizationId("mgr", 1L)).thenReturn(Optional.of(managerUser));

            assertThrows(AccessDeniedException.class,
                    () -> contractAccessGuard.checkManagerCanAccess(contract));
        }
    }
}
