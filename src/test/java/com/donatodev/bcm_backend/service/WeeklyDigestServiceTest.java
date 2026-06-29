package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class WeeklyDigestServiceTest {

    @Mock OrganizationRepository organizationRepository;
    @Mock ContractsRepository contractsRepository;
    @Mock UsersRepository usersRepository;
    @Mock IEmailService emailService;

    @InjectMocks WeeklyDigestService weeklyDigestService;

    private Organization org(long id) {
        Organization o = new Organization();
        o.setId(id);
        o.setName("Acme S.r.l.");
        return o;
    }

    private Users admin(String email) {
        Managers m = new Managers();
        m.setEmail(email);
        Users u = new Users();
        u.setManager(m);
        return u;
    }

    private Users adminNoManager() {
        return new Users();
    }

    private Contracts contract(String number, String customer, LocalDate endDate) {
        Contracts c = new Contracts();
        c.setContractNumber(number);
        c.setCustomerName(customer);
        c.setProjectName("Progetto Test");
        c.setEndDate(endDate);
        c.setStatus(ContractStatus.ACTIVE);
        return c;
    }

    @Nested
    @DisplayName("sendDigestForOrg()")
    class SendDigestForOrg {

        @Test
        @DisplayName("Sends one email per admin when expiring contracts exist")
        void sendsEmailPerAdmin() {
            Organization o = org(1L);
            LocalDate today = LocalDate.now();
            when(contractsRepository.findExpiringContractsByOrg(
                    today, today.plusDays(30), 1L))
                    .thenReturn(List.of(contract("C-001", "Rossi S.r.l.", today.plusDays(5))));
            when(usersRepository.findByOrganizationIdAndRoleRole(1L, "ADMIN"))
                    .thenReturn(List.of(admin("admin1@example.com"), admin("admin2@example.com")));

            int sent = weeklyDigestService.sendDigestForOrg(o);

            assertThat(sent).isEqualTo(2);
            verify(emailService, times(2)).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Sends no emails when there are no admin users")
        void sendsNothingWhenNoAdmins() {
            Organization o = org(2L);
            LocalDate today = LocalDate.now();
            when(contractsRepository.findExpiringContractsByOrg(
                    today, today.plusDays(30), 2L))
                    .thenReturn(List.of());
            when(usersRepository.findByOrganizationIdAndRoleRole(2L, "ADMIN"))
                    .thenReturn(List.of());

            int sent = weeklyDigestService.sendDigestForOrg(o);

            assertThat(sent).isZero();
            verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Skips admin users with no linked manager")
        void skipsUserWithNoManager() {
            Organization o = org(3L);
            LocalDate today = LocalDate.now();
            when(contractsRepository.findExpiringContractsByOrg(
                    today, today.plusDays(30), 3L))
                    .thenReturn(List.of());
            when(usersRepository.findByOrganizationIdAndRoleRole(3L, "ADMIN"))
                    .thenReturn(List.of(adminNoManager()));

            int sent = weeklyDigestService.sendDigestForOrg(o);

            assertThat(sent).isZero();
        }

        @Test
        @DisplayName("Subject mentions expiring count")
        void subjectMentionsCount() {
            Organization o = org(4L);
            LocalDate today = LocalDate.now();
            when(contractsRepository.findExpiringContractsByOrg(
                    today, today.plusDays(30), 4L))
                    .thenReturn(List.of(
                            contract("C-001", "ClienteA", today.plusDays(3)),
                            contract("C-002", "ClienteB", today.plusDays(10))));
            when(usersRepository.findByOrganizationIdAndRoleRole(4L, "ADMIN"))
                    .thenReturn(List.of(admin("admin@test.com")));

            weeklyDigestService.sendDigestForOrg(o);

            verify(emailService).sendEmail(
                    eq("admin@test.com"),
                    contains("2 contracts expiring soon"),
                    anyString());
        }

        @Test
        @DisplayName("Subject says no expiring when list is empty")
        void subjectNoExpiring() {
            Organization o = org(5L);
            LocalDate today = LocalDate.now();
            when(contractsRepository.findExpiringContractsByOrg(
                    today, today.plusDays(30), 5L))
                    .thenReturn(List.of());
            when(usersRepository.findByOrganizationIdAndRoleRole(5L, "ADMIN"))
                    .thenReturn(List.of(admin("admin@test.com")));

            weeklyDigestService.sendDigestForOrg(o);

            verify(emailService).sendEmail(
                    anyString(),
                    contains("No expiring contracts"),
                    anyString());
        }
    }

    @Nested
    @DisplayName("buildHtmlBody()")
    class BuildHtmlBody {

        @Test
        @DisplayName("Contains org name in header")
        void containsOrgName() {
            String html = weeklyDigestService.buildHtmlBody(
                    "Acme S.r.l.", LocalDate.now(), List.of());
            assertThat(html).contains("Acme S.r.l.");
        }

        @Test
        @DisplayName("Contains empty state message when no expiring contracts")
        void emptyStateWhenNoExpiring() {
            String html = weeklyDigestService.buildHtmlBody(
                    "Acme", LocalDate.now(), List.of());
            assertThat(html).contains("Nessun contratto in scadenza");
        }

        @Test
        @DisplayName("Shows contract rows when expiring contracts exist")
        void showsContractRows() {
            LocalDate today = LocalDate.now();
            List<Contracts> expiring = List.of(
                    contract("C-001", "Rossi S.r.l.", today.plusDays(5)));
            String html = weeklyDigestService.buildHtmlBody("Acme", today, expiring);
            assertThat(html).contains("C-001").contains("Rossi S.r.l.");
        }

        @Test
        @DisplayName("Shows critical section for contracts <= 7 days")
        void showsCriticalSection() {
            LocalDate today = LocalDate.now();
            List<Contracts> expiring = List.of(
                    contract("C-CRIT", "Urgente S.r.l.", today.plusDays(3)));
            String html = weeklyDigestService.buildHtmlBody("Acme", today, expiring);
            assertThat(html).contains("critici").contains("C-CRIT");
        }

        @Test
        @DisplayName("Escapes HTML special characters in customer name")
        void escapesHtml() {
            LocalDate today = LocalDate.now();
            List<Contracts> expiring = List.of(
                    contract("C-XSS", "<script>alert('xss')</script>", today.plusDays(10)));
            String html = weeklyDigestService.buildHtmlBody("Acme", today, expiring);
            assertThat(html)
                    .doesNotContain("<script>")
                    .contains("&lt;script&gt;");
        }

        @Test
        @DisplayName("Shows overflow message when more than 5 contracts")
        void showsOverflowMessage() {
            LocalDate today = LocalDate.now();
            List<Contracts> expiring = List.of(
                    contract("C-1", "A", today.plusDays(20)),
                    contract("C-2", "B", today.plusDays(21)),
                    contract("C-3", "C", today.plusDays(22)),
                    contract("C-4", "D", today.plusDays(23)),
                    contract("C-5", "E", today.plusDays(24)),
                    contract("C-6", "F", today.plusDays(25)));
            String html = weeklyDigestService.buildHtmlBody("Acme", today, expiring);
            assertThat(html).contains("+ 1 altri contratti");
        }
    }

    @Nested
    @DisplayName("sendWeeklyDigests()")
    class SendWeeklyDigests {

        @Test
        @DisplayName("Processes all organizations and continues on failure")
        void processesAllOrgsAndContinuesOnFailure() {
            Organization o1 = org(1L);
            Organization o2 = org(2L);
            when(organizationRepository.findAll()).thenReturn(List.of(o1, o2));

            LocalDate today = LocalDate.now();

            // org 1: throws
            when(contractsRepository.findExpiringContractsByOrg(
                    today, today.plusDays(30), 1L))
                    .thenThrow(new RuntimeException("DB error"));

            // org 2: succeeds
            when(contractsRepository.findExpiringContractsByOrg(
                    today, today.plusDays(30), 2L))
                    .thenReturn(List.of());
            when(usersRepository.findByOrganizationIdAndRoleRole(2L, "ADMIN"))
                    .thenReturn(List.of(admin("admin2@test.com")));

            weeklyDigestService.sendWeeklyDigests();

            // Org 2 email should still be sent despite org 1 failing
            verify(emailService, times(1)).sendEmail(anyString(), anyString(), anyString());
        }
    }
}
