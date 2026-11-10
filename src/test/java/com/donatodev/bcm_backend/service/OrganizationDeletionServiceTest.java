package com.donatodev.bcm_backend.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.OrganizationNotFoundException;
import com.donatodev.bcm_backend.repository.OrganizationDeletionCodeRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * The paths OrganizationDeletionControllerTest can't reach over HTTP: the
 * controller tests always log in as a user that belongs to an organization.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationDeletionServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationDeletionCodeRepository codeRepository;
    @Mock private UsersRepository usersRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private IEmailService emailService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private OrganizationDeletionService deletionService;

    @Test
    @DisplayName("A user without an organization cannot ask for a deletion, and nothing is touched")
    void userWithoutOrganizationCannotRequestDeletion() {
        when(currentUserResolver.resolve()).thenReturn(Users.builder().username("orphan").build());

        assertThrows(OrganizationNotFoundException.class, () -> deletionService.request("password", "Acme"));

        verifyNoInteractions(passwordEncoder, codeRepository, emailService, organizationRepository);
    }
}
