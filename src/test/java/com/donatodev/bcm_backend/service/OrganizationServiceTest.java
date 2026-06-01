package com.donatodev.bcm_backend.service;

import java.util.Optional;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.dto.OrganizationDTO;
import com.donatodev.bcm_backend.dto.OrganizationRegistrationRequest;
import com.donatodev.bcm_backend.dto.UpdateOrganizationRequest;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.RefreshToken;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.OrganizationNotFoundException;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class OrganizationServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private UsersRepository usersRepository;
    @Mock private ManagersRepository managersRepository;
    @Mock private RolesRepository rolesRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private RefreshTokenService refreshTokenService;

    @InjectMocks
    private OrganizationService organizationService;

    private OrganizationRegistrationRequest validRequest() {
        return new OrganizationRegistrationRequest(
                "Acme Corp", "acme-admin", "secret123",
                "admin@acme.com", "John", "Doe");
    }

    private Organization savedOrg() {
        return Organization.builder().id(1L).name("Acme Corp").slug("acme-corp")
                .subscriptionTier(SubscriptionTier.FREE).build();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: OrganizationService")
    @SuppressWarnings("unused")
    class VerifyOrganizationService {

        @Test
        @Order(1)
        @DisplayName("registerOrganization creates org + admin and returns tokens")
        void shouldRegisterOrganizationAndReturnTokens() {
            Organization org = savedOrg();
            Managers manager = Managers.builder().id(1L).email("admin@acme.com").build();
            Users admin = Users.builder().id(1L).username("acme-admin").organization(org).build();
            RefreshToken rt = RefreshToken.builder().token("refresh-token").build();

            when(usersRepository.existsByUsername("acme-admin")).thenReturn(false);
            when(managersRepository.existsByEmail("admin@acme.com")).thenReturn(false);
            when(organizationRepository.findBySlug(anyString())).thenReturn(Optional.empty());
            when(organizationRepository.save(any())).thenReturn(org);
            when(managersRepository.save(any())).thenReturn(manager);
            when(rolesRepository.findByRole("ADMIN"))
                    .thenReturn(Optional.of(Roles.builder().role("ADMIN").build()));
            when(passwordEncoder.encode("secret123")).thenReturn("hashed");
            when(usersRepository.save(any())).thenReturn(admin);
            when(jwtUtils.generateToken(admin)).thenReturn("access-token");
            when(refreshTokenService.createRefreshToken(admin)).thenReturn(rt);

            var response = organizationService.registerOrganization(validRequest());

            assertEquals("access-token", response.token());
            assertEquals("refresh-token", response.refreshToken());
        }

        @Test
        @Order(2)
        @DisplayName("registerOrganization throws when username already exists")
        void shouldThrowWhenUsernameExists() {
            when(usersRepository.existsByUsername("acme-admin")).thenReturn(true);
            var request = validRequest();
            assertThrows(IllegalArgumentException.class,
                    () -> organizationService.registerOrganization(request));
        }

        @Test
        @Order(3)
        @DisplayName("registerOrganization throws when email already in use")
        void shouldThrowWhenEmailExists() {
            when(usersRepository.existsByUsername("acme-admin")).thenReturn(false);
            when(managersRepository.existsByEmail("admin@acme.com")).thenReturn(true);
            var request = validRequest();
            assertThrows(IllegalArgumentException.class,
                    () -> organizationService.registerOrganization(request));
        }

        @Test
        @Order(4)
        @DisplayName("registerOrganization appends suffix when slug already exists")
        void shouldGenerateUniqueSlugWhenBaseSlugTaken() {
            Organization org = savedOrg();
            Managers manager = Managers.builder().id(1L).build();
            Users admin = Users.builder().id(1L).username("acme-admin").organization(org).build();
            RefreshToken rt = RefreshToken.builder().token("rt").build();

            when(usersRepository.existsByUsername(anyString())).thenReturn(false);
            when(managersRepository.existsByEmail(anyString())).thenReturn(false);
            when(organizationRepository.findBySlug(anyString())).thenAnswer(inv -> {
                String s = inv.getArgument(0);
                return s.equals("acme-corp") ? Optional.of(org) : Optional.empty();
            });
            when(organizationRepository.save(any())).thenReturn(org);
            when(managersRepository.save(any())).thenReturn(manager);
            when(rolesRepository.findByRole("ADMIN"))
                    .thenReturn(Optional.of(Roles.builder().role("ADMIN").build()));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(usersRepository.save(any())).thenReturn(admin);
            when(jwtUtils.generateToken(any())).thenReturn("token");
            when(refreshTokenService.createRefreshToken(any())).thenReturn(rt);

            var response = organizationService.registerOrganization(validRequest());
            assertNotNull(response.token());
        }

        @Test
        @Order(5)
        @DisplayName("updateMyOrganization updates name and tier via TenantContext")
        void shouldUpdateOrganization() {
            Organization org = Organization.builder()
                    .id(1L).name("Old Name").slug("old-name")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            com.donatodev.bcm_backend.config.TenantContext.set(1L);
            try {
                when(organizationRepository.findById(1L)).thenReturn(Optional.of(org));
                when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                UpdateOrganizationRequest req = new UpdateOrganizationRequest("New Name", SubscriptionTier.PRO);
                OrganizationDTO result = organizationService.updateMyOrganization(req);

                assertEquals("New Name", result.name());
                assertEquals(SubscriptionTier.PRO, result.subscriptionTier());
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(6)
        @DisplayName("getMyOrganization throws OrganizationNotFoundException when org not found")
        void shouldThrowWhenOrgNotFound() {
            com.donatodev.bcm_backend.config.TenantContext.set(99L);
            try {
                when(organizationRepository.findById(99L)).thenReturn(Optional.empty());
                assertThrows(OrganizationNotFoundException.class,
                        () -> organizationService.getMyOrganization());
            } finally {
                com.donatodev.bcm_backend.config.TenantContext.clear();
            }
        }

        @Test
        @Order(7)
        @DisplayName("getMyOrganization via SecurityContext fallback returns org")
        void shouldGetMyOrganizationViaSecurityContextFallback() {
            // TenantContext is null → fallback to SecurityContextHolder
            Organization org = Organization.builder().id(1L).name("Test").slug("test")
                    .subscriptionTier(SubscriptionTier.FREE).build();
            Users user = Users.builder().username("admin").organization(org).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null, java.util.List.of()));
            try {
                when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(user));

                OrganizationDTO result = organizationService.getMyOrganization();

                assertEquals("Test", result.name());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @Order(8)
        @DisplayName("getMyOrganization via SecurityContext throws when user has no org")
        void shouldThrowWhenUserHasNoOrganizationInFallback() {
            Users userWithoutOrg = Users.builder().username("noorg").build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("noorg", null, java.util.List.of()));
            try {
                when(usersRepository.findByUsername("noorg")).thenReturn(Optional.of(userWithoutOrg));

                assertThrows(OrganizationNotFoundException.class,
                        () -> organizationService.getMyOrganization());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @Order(9)
        @DisplayName("getMyOrganization via SecurityContext throws when user not found")
        void shouldThrowWhenAuthenticatedUserNotFoundInFallback() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("ghost", null, java.util.List.of()));
            try {
                when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

                assertThrows(OrganizationNotFoundException.class,
                        () -> organizationService.getMyOrganization());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
