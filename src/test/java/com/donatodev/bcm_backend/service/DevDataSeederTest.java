package com.donatodev.bcm_backend.service;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test: DevDataSeeder")
class DevDataSeederTest {

    @Mock private UsersRepository usersRepository;
    @Mock private RolesRepository rolesRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private ManagersRepository managersRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DevDataSeeder devDataSeeder;

    @Nested
    @DisplayName("run()")
    class Run {

        @Test
        @DisplayName("does nothing when the demo account already exists")
        void skipsWhenAlreadySeeded() throws Exception {
            when(usersRepository.findByUsername(DevDataSeeder.DEMO_USERNAME))
                    .thenReturn(Optional.of(new Users()));

            devDataSeeder.run(null);

            verify(usersRepository, never()).save(any());
            verify(organizationRepository, never()).findFirstByOrderByIdAsc();
        }

        @Test
        @DisplayName("does nothing when there is no organization to attach the account to")
        void skipsWhenOrgMissing() throws Exception {
            when(usersRepository.findByUsername(DevDataSeeder.DEMO_USERNAME)).thenReturn(Optional.empty());
            when(organizationRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
            when(rolesRepository.findByRole("ADMIN")).thenReturn(Optional.of(new Roles()));

            devDataSeeder.run(null);

            verify(usersRepository, never()).save(any());
        }

        @Test
        @DisplayName("does nothing when the ADMIN role is missing")
        void skipsWhenRoleMissing() throws Exception {
            when(usersRepository.findByUsername(DevDataSeeder.DEMO_USERNAME)).thenReturn(Optional.empty());
            when(organizationRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(new Organization()));
            when(rolesRepository.findByRole("ADMIN")).thenReturn(Optional.empty());

            devDataSeeder.run(null);

            verify(usersRepository, never()).save(any());
        }

        @Test
        @DisplayName("creates a verified admin user (with its required manager row) in the default organization")
        void seedsDemoAccount() throws Exception {
            Organization defaultOrg = Organization.builder().id(1L).name("Default Organization").slug("default").build();
            Roles adminRole = Roles.builder().id(1L).role("ADMIN").build();
            Managers savedManager = Managers.builder().id(1L).firstName("Demo").lastName("Admin").build();

            when(usersRepository.findByUsername(DevDataSeeder.DEMO_USERNAME)).thenReturn(Optional.empty());
            when(organizationRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(defaultOrg));
            when(rolesRepository.findByRole("ADMIN")).thenReturn(Optional.of(adminRole));
            when(managersRepository.save(any(Managers.class))).thenReturn(savedManager);
            when(passwordEncoder.encode(DevDataSeeder.DEMO_PASSWORD)).thenReturn("hashed-password");

            devDataSeeder.run(null);

            ArgumentCaptor<Users> captor = ArgumentCaptor.forClass(Users.class);
            verify(usersRepository).save(captor.capture());
            Users saved = captor.getValue();

            assertTrue(saved.isVerified());
            assertEquals(DevDataSeeder.DEMO_USERNAME, saved.getUsername());
            assertEquals("hashed-password", saved.getPasswordHash());
            assertEquals(defaultOrg, saved.getOrganization());
            assertEquals(adminRole, saved.getRole());
            assertEquals(savedManager, saved.getManager());
        }
    }
}
