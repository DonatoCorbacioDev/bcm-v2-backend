package com.donatodev.bcm_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.OrganizationDeletionCode;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.AuditLogRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationDeletionCodeRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.DummyEmailService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationDeletionControllerTest {

    private static final String DELETION_URL = "/organizations/me/deletion";
    private static final String CONFIRM_URL = "/organizations/me/deletion/confirm";
    private static final String PASSWORD = "Secret#2026";
    private static final String ORG_NAME = "Delete Corp";

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private ManagersRepository managersRepository;
    @Autowired private RolesRepository rolesRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private OrganizationDeletionCodeRepository codeRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoSpyBean private DummyEmailService emailService;

    private Organization org;

    @BeforeEach
    void setup() {
        clearInvocations(emailService);
        codeRepository.deleteAll();
        auditLogRepository.deleteAll();
        usersRepository.deleteAll();
        managersRepository.deleteAll();
        rolesRepository.deleteAll();
        organizationRepository.deleteAll();

        org = organizationRepository.save(Organization.builder()
                .name(ORG_NAME).slug("delete-corp").subscriptionTier(SubscriptionTier.FREE).build());
        Roles admin = rolesRepository.save(Roles.builder().role("ADMIN").build());
        saveAdmin("delete-admin", "admin@delete.test", admin);
        saveAdmin("second-admin", "second@delete.test", admin);
    }

    private void saveAdmin(String username, String email, Roles role) {
        Managers manager = managersRepository.save(Managers.builder()
                .firstName("Admin").lastName(username).email(email).organization(org).build());
        usersRepository.save(Users.builder().username(username).passwordHash(passwordEncoder.encode(PASSWORD))
                .verified(true).role(role).manager(manager).organization(org).build());
    }

    private ResultActions requestDeletion(String password, String name) throws Exception {
        return mockMvc.perform(post(DELETION_URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"%s\",\"organizationName\":\"%s\"}".formatted(password, name)));
    }

    private ResultActions confirm(String code) throws Exception {
        return mockMvc.perform(post(CONFIRM_URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code)));
    }

    /** Requests the deletion and returns the code the (spied) email service was given. */
    private String requestAndCaptureCode() throws Exception {
        requestDeletion(PASSWORD, ORG_NAME).andExpect(status().isAccepted());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOrganizationDeletionCode(eq("admin@delete.test"), eq(ORG_NAME), code.capture(), eq(15));
        return code.getValue();
    }

    private Organization reloaded() {
        return organizationRepository.findById(org.getId()).orElseThrow();
    }

    /** Lets a new code be requested right away, as if the cooldown had passed. */
    private void expireCooldown() {
        OrganizationDeletionCode code = codeRepository.findByOrganizationId(org.getId()).orElseThrow();
        code.setCodeSentAt(code.getCodeSentAt().minusMinutes(2));
        codeRepository.save(code);
    }

    @Nested
    @DisplayName("POST /organizations/me/deletion")
    @SuppressWarnings("unused")
    class Request {

        @Test
        @WithMockUser(username = "delete-admin", roles = "ADMIN")
        @DisplayName("Emails a code to the requesting admin and stores only its hash")
        void emailsCode() throws Exception {
            requestDeletion(PASSWORD, ORG_NAME)
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.expiresInMinutes").value(15))
                    .andExpect(jsonPath("$.resendCooldownSeconds").value(60));

            ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendOrganizationDeletionCode(eq("admin@delete.test"), eq(ORG_NAME), code.capture(), eq(15));
            OrganizationDeletionCode stored = codeRepository.findByOrganizationId(org.getId()).orElseThrow();
            assertThat(code.getValue()).matches("\\d{6}");
            assertThat(stored.getCodeHash()).hasSize(64).doesNotContain(code.getValue());
            assertThat(reloaded().getDeletionScheduledFor()).isNull();
        }

        @Test
        @WithMockUser(username = "delete-admin", roles = "ADMIN")
        @DisplayName("A wrong password sends nothing")
        void rejectsWrongPassword() throws Exception {
            requestDeletion("wrong", ORG_NAME)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Password non corretta."));
            verify(emailService, never()).sendOrganizationDeletionCode(anyString(), anyString(), anyString(), anyInt());
        }

        @Test
        @WithMockUser(username = "delete-admin", roles = "ADMIN")
        @DisplayName("The organization name must be typed exactly")
        void rejectsWrongName() throws Exception {
            requestDeletion(PASSWORD, "delete corp")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Il nome dell'organizzazione non corrisponde."));
        }

        @Test
        @WithMockUser(username = "delete-admin", roles = "ADMIN")
        @DisplayName("An active Stripe subscription must be cancelled first")
        void blocksPaidSubscription() throws Exception {
            org.setSubscriptionTier(SubscriptionTier.PRO);
            org.setStripeCustomerId("cus_test");
            organizationRepository.save(org);

            requestDeletion(PASSWORD, ORG_NAME)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("abbonamento")));
        }

        @Test
        @WithMockUser(username = "delete-admin", roles = "ADMIN")
        @DisplayName("A plan granted by hand (no Stripe customer) does not block")
        void allowsManualPlan() throws Exception {
            org.setSubscriptionTier(SubscriptionTier.PRO);
            organizationRepository.save(org);

            requestDeletion(PASSWORD, ORG_NAME).andExpect(status().isAccepted());
        }

        @Test
        @WithMockUser(username = "delete-admin", roles = "ADMIN")
        @DisplayName("A new code can't be asked for within the cooldown")
        void enforcesCooldown() throws Exception {
            requestDeletion(PASSWORD, ORG_NAME).andExpect(status().isAccepted());

            requestDeletion(PASSWORD, ORG_NAME).andExpect(status().isBadRequest());
            verify(emailService, times(1)).sendOrganizationDeletionCode(anyString(), anyString(), anyString(), anyInt());
        }

        @Test
        @WithMockUser(username = "no-email-admin", roles = "ADMIN")
        @DisplayName("An admin with no email address can't receive the code")
        void requiresAnEmailAddress() throws Exception {
            usersRepository.save(Users.builder().username("no-email-admin")
                    .passwordHash(passwordEncoder.encode(PASSWORD)).verified(true)
                    .role(rolesRepository.findByRole("ADMIN").orElseThrow()).organization(org).build());

            requestDeletion(PASSWORD, ORG_NAME)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("indirizzo email")));
            assertThat(codeRepository.findByOrganizationId(org.getId())).isEmpty();
        }

        @Test
        @WithMockUser(username = "orphan-admin", roles = "ADMIN")
        @DisplayName("A user with no organization gets 404")
        void requiresAnOrganization() throws Exception {
            usersRepository.save(Users.builder().username("orphan-admin")
                    .passwordHash(passwordEncoder.encode(PASSWORD)).verified(true)
                    .role(rolesRepository.findByRole("ADMIN").orElseThrow()).build());

            requestDeletion(PASSWORD, ORG_NAME).andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(username = "delete-admin", roles = "MANAGER")
        @DisplayName("A manager can't delete the organization")
        void forbidsManager() throws Exception {
            requestDeletion(PASSWORD, ORG_NAME).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /organizations/me/deletion/confirm")
    @SuppressWarnings("unused")
    class Confirm {

        @Test
        @WithMockUser(username = "delete-admin", roles = "ADMIN")
        @DisplayName("The right code schedules the deletion 30 days ahead and tells every admin")
        void schedulesDeletion() throws Exception {
            // An admin without an email address is skipped, not a reason to fail.
            usersRepository.save(Users.builder().username("silent-admin")
                    .passwordHash(passwordEncoder.encode(PASSWORD)).verified(true)
                    .role(rolesRepository.findByRole("ADMIN").orElseThrow()).organization(org).build());
            String code = requestAndCaptureCode();

            confirm(code).andExpect(status().isOk()).andExpect(jsonPath("$.scheduledFor").exists());

            Organization saved = reloaded();
            assertThat(saved.getDeletionScheduledFor())
                    .isCloseTo(LocalDateTime.now().plusDays(30), within(1, ChronoUnit.MINUTES));
            assertThat(saved.getDeletionRequestedBy()).isEqualTo("delete-admin");
            assertThat(codeRepository.findByOrganizationId(org.getId())).isEmpty();
            LocalDate day = saved.getDeletionScheduledFor().toLocalDate();
            verify(emailService).sendOrganizationDeletionScheduled("admin@delete.test", ORG_NAME, "delete-admin", day);
            verify(emailService).sendOrganizationDeletionScheduled("second@delete.test", ORG_NAME, "delete-admin", day);
            verify(emailService, times(2)).sendOrganizationDeletionScheduled(anyString(), anyString(), anyString(), any());
            assertThat(auditLogRepository.findAll()).isNotEmpty()
                    .anyMatch(log -> log.getAction().equals("ORGANIZATION_DELETION_SCHEDULED"));
        }

        @Test
        @WithMockUser(username = "delete-admin", roles = "ADMIN")
        @DisplayName("GET /organizations/me then shows when the deletion will run")
        void exposesScheduleOnOrganization() throws Exception {
            confirm(requestAndCaptureCode()).andExpect(status().isOk());

            mockMvc.perform(get("/organizations/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionScheduledFor").isNotEmpty());
        }

        @Test
        @WithMockUser(username = "delete-admin", roles = "ADMIN")
        @DisplayName("A wrong code is counted; the fifth drops the request")
        void countsWrongCodes() throws Exception {
            String code = requestAndCaptureCode();
            String wrong = code.equals("000000") ? "111111" : "000000";

            for (int i = 1; i <= 4; i++) {
                confirm(wrong).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").value("Codice non corretto."));
            }
            assertThat(codeRepository.findByOrganizationId(org.getId()).orElseThrow().getFailedAttempts()).isEqualTo(4);

            confirm(wrong).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(startsWith("Troppi tentativi")));
            confirm(code).andExpect(status().isBadRequest());
            assertThat(reloaded().getDeletionScheduledFor()).isNull();
        }

        @Test
        @WithMockUser(username = "delete-admin", roles = "ADMIN")
        @DisplayName("An expired code is refused")
        void rejectsExpiredCode() throws Exception {
            String code = requestAndCaptureCode();
            OrganizationDeletionCode stored = codeRepository.findByOrganizationId(org.getId()).orElseThrow();
            stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            codeRepository.save(stored);

            confirm(code).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("scaduto")));
            assertThat(reloaded().getDeletionScheduledFor()).isNull();
        }

        @Test
        @WithMockUser(username = "delete-admin", roles = "ADMIN")
        @DisplayName("A new request replaces the previous code")
        void newRequestReplacesCode() throws Exception {
            String first = requestAndCaptureCode();
            expireCooldown();
            clearInvocations(emailService);
            String second = requestAndCaptureCode();

            if (!first.equals(second)) {
                confirm(first).andExpect(status().isBadRequest());
            }
            confirm(second).andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "second-admin", roles = "ADMIN")
        @DisplayName("Only the admin who asked can confirm")
        void otherAdminCannotConfirm() throws Exception {
            Users requester = usersRepository.findByUsername("delete-admin").orElseThrow();
            codeRepository.save(OrganizationDeletionCode.builder().organizationId(org.getId())
                    .userId(requester.getId())
                    .codeHash(DigestUtils.sha256Hex("delete:" + org.getId() + ":123456"))
                    .codeSentAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusMinutes(15)).build());

            confirm("123456").andExpect(status().isBadRequest());
            assertThat(reloaded().getDeletionScheduledFor()).isNull();
        }

        @Test
        @WithMockUser(username = "delete-admin", roles = "ADMIN")
        @DisplayName("A deletion already scheduled can't be requested again")
        void rejectsSecondRequest() throws Exception {
            confirm(requestAndCaptureCode()).andExpect(status().isOk());

            requestDeletion(PASSWORD, ORG_NAME).andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("DELETE /organizations/me/deletion")
    @SuppressWarnings("unused")
    class Cancel {

        @Test
        @WithMockUser(username = "second-admin", roles = "ADMIN")
        @DisplayName("Any admin can cancel; every admin is told")
        void cancels() throws Exception {
            org.setDeletionScheduledFor(LocalDateTime.now().plusDays(30));
            org.setDeletionRequestedAt(LocalDateTime.now());
            org.setDeletionRequestedBy("delete-admin");
            organizationRepository.save(org);

            mockMvc.perform(delete(DELETION_URL)).andExpect(status().isNoContent());

            Organization saved = reloaded();
            assertThat(saved.getDeletionScheduledFor()).isNull();
            assertThat(saved.getDeletionRequestedBy()).isNull();
            verify(emailService).sendOrganizationDeletionCancelled("admin@delete.test", ORG_NAME, "second-admin");
            verify(emailService).sendOrganizationDeletionCancelled("second@delete.test", ORG_NAME, "second-admin");
        }

        @Test
        @WithMockUser(username = "delete-admin", roles = "ADMIN")
        @DisplayName("Nothing to cancel answers 400")
        void nothingToCancel() throws Exception {
            mockMvc.perform(delete(DELETION_URL)).andExpect(status().isBadRequest());
            verify(emailService, never()).sendOrganizationDeletionCancelled(any(), any(), any());
        }
    }
}
