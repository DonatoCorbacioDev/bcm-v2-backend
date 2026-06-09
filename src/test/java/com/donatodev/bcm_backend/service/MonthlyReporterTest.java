package com.donatodev.bcm_backend.service;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class MonthlyReporterTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private ContractsRepository contractsRepository;
    @Mock private FinancialValuesRepository financialValuesRepository;
    @Mock private UsersRepository usersRepository;
    @Mock private IEmailService emailService;

    @InjectMocks private MonthlyReporter monthlyReporter;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: MonthlyReporter")
    @SuppressWarnings("unused")
    class VerifyMonthlyReporter {

        @Test
        @Order(1)
        @DisplayName("Should send monthly report to admin for each organization")
        void shouldSendReportForEachOrg() {
            Organization org = Organization.builder().id(1L).name("TestOrg")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            Managers manager = new Managers();
            manager.setId(1L);
            manager.setEmail("admin@testorg.com");
            Users admin = Users.builder().id(1L).username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .manager(manager).organization(org).build();

            when(organizationRepository.findAll()).thenReturn(List.of(org));
            when(contractsRepository.countAllContractsByOrg(1L)).thenReturn(10);
            when(contractsRepository.countActiveContractsByOrg(1L)).thenReturn(7);
            when(contractsRepository.countExpiredContractsByOrg(1L)).thenReturn(2);
            when(contractsRepository.countNewContractsByOrgAndYearMonth(eq(1L), any(Integer.class), any(Integer.class))).thenReturn(3L);
            when(financialValuesRepository.sumFinancialAmountByOrgAndYearMonth(eq(1L), any(Integer.class), any(Integer.class))).thenReturn(50000.0);
            when(usersRepository.findByOrganizationIdAndRoleRole(1L, "ADMIN")).thenReturn(List.of(admin));

            monthlyReporter.sendMonthlyReports();

            verify(emailService).sendEmail(eq("admin@testorg.com"), contains("[BCM] Monthly Report"), anyString());
        }

        @Test
        @Order(2)
        @DisplayName("Should skip organization with no admins")
        void shouldSkipOrgWithNoAdmins() {
            Organization org = Organization.builder().id(1L).name("NoAdminOrg")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            when(organizationRepository.findAll()).thenReturn(List.of(org));
            when(contractsRepository.countAllContractsByOrg(1L)).thenReturn(5);
            when(contractsRepository.countActiveContractsByOrg(1L)).thenReturn(3);
            when(contractsRepository.countExpiredContractsByOrg(1L)).thenReturn(1);
            when(contractsRepository.countNewContractsByOrgAndYearMonth(eq(1L), any(Integer.class), any(Integer.class))).thenReturn(1L);
            when(financialValuesRepository.sumFinancialAmountByOrgAndYearMonth(eq(1L), any(Integer.class), any(Integer.class))).thenReturn(0.0);
            when(usersRepository.findByOrganizationIdAndRoleRole(1L, "ADMIN")).thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> monthlyReporter.sendMonthlyReports());

            verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @Order(3)
        @DisplayName("Should continue processing other orgs when one fails")
        void shouldContinueWhenOneOrgFails() {
            Organization org1 = Organization.builder().id(1L).name("Org One")
                    .subscriptionTier(SubscriptionTier.FREE).build();
            Organization org2 = Organization.builder().id(2L).name("Org Two")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            Managers manager2 = new Managers();
            manager2.setId(2L);
            manager2.setEmail("admin2@orgtwo.com");
            Users admin2 = Users.builder().id(2L).username("admin2")
                    .role(Roles.builder().role("ADMIN").build())
                    .manager(manager2).organization(org2).build();

            when(organizationRepository.findAll()).thenReturn(List.of(org1, org2));
            when(contractsRepository.countAllContractsByOrg(1L)).thenThrow(new RuntimeException("DB error"));
            when(contractsRepository.countAllContractsByOrg(2L)).thenReturn(3);
            when(contractsRepository.countActiveContractsByOrg(2L)).thenReturn(2);
            when(contractsRepository.countExpiredContractsByOrg(2L)).thenReturn(1);
            when(contractsRepository.countNewContractsByOrgAndYearMonth(eq(2L), any(Integer.class), any(Integer.class))).thenReturn(1L);
            when(financialValuesRepository.sumFinancialAmountByOrgAndYearMonth(eq(2L), any(Integer.class), any(Integer.class))).thenReturn(1000.0);
            when(usersRepository.findByOrganizationIdAndRoleRole(2L, "ADMIN")).thenReturn(List.of(admin2));

            assertDoesNotThrow(() -> monthlyReporter.sendMonthlyReports());

            verify(emailService).sendEmail(eq("admin2@orgtwo.com"), anyString(), anyString());
        }

        @Test
        @Order(4)
        @DisplayName("Should skip admin with no manager email")
        void shouldSkipAdminWithNoManagerEmail() {
            Organization org = Organization.builder().id(1L).name("TestOrg")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            Users adminNoEmail = Users.builder().id(1L).username("noemail")
                    .role(Roles.builder().role("ADMIN").build())
                    .manager(null).organization(org).build();

            when(organizationRepository.findAll()).thenReturn(List.of(org));
            when(contractsRepository.countAllContractsByOrg(1L)).thenReturn(5);
            when(contractsRepository.countActiveContractsByOrg(1L)).thenReturn(3);
            when(contractsRepository.countExpiredContractsByOrg(1L)).thenReturn(1);
            when(contractsRepository.countNewContractsByOrgAndYearMonth(eq(1L), any(Integer.class), any(Integer.class))).thenReturn(1L);
            when(financialValuesRepository.sumFinancialAmountByOrgAndYearMonth(eq(1L), any(Integer.class), any(Integer.class))).thenReturn(0.0);
            when(usersRepository.findByOrganizationIdAndRoleRole(1L, "ADMIN")).thenReturn(List.of(adminNoEmail));

            assertDoesNotThrow(() -> monthlyReporter.sendMonthlyReports());

            verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @Order(5)
        @DisplayName("Should send reports to all admins when multiple admins in org")
        void shouldSendToAllAdmins() {
            Organization org = Organization.builder().id(1L).name("MultiAdmin")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            Managers m1 = new Managers();
            m1.setId(1L);
            m1.setEmail("admin1@org.com");
            Managers m2 = new Managers();
            m2.setId(2L);
            m2.setEmail("admin2@org.com");

            Users admin1 = Users.builder().id(1L).username("a1").manager(m1).organization(org)
                    .role(Roles.builder().role("ADMIN").build()).build();
            Users admin2 = Users.builder().id(2L).username("a2").manager(m2).organization(org)
                    .role(Roles.builder().role("ADMIN").build()).build();

            when(organizationRepository.findAll()).thenReturn(List.of(org));
            when(contractsRepository.countAllContractsByOrg(1L)).thenReturn(5);
            when(contractsRepository.countActiveContractsByOrg(1L)).thenReturn(3);
            when(contractsRepository.countExpiredContractsByOrg(1L)).thenReturn(1);
            when(contractsRepository.countNewContractsByOrgAndYearMonth(eq(1L), any(Integer.class), any(Integer.class))).thenReturn(2L);
            when(financialValuesRepository.sumFinancialAmountByOrgAndYearMonth(eq(1L), any(Integer.class), any(Integer.class))).thenReturn(20000.0);
            when(usersRepository.findByOrganizationIdAndRoleRole(1L, "ADMIN")).thenReturn(List.of(admin1, admin2));

            monthlyReporter.sendMonthlyReports();

            verify(emailService, times(2)).sendEmail(anyString(), anyString(), anyString());
            verify(emailService).sendEmail(eq("admin1@org.com"), anyString(), anyString());
            verify(emailService).sendEmail(eq("admin2@org.com"), anyString(), anyString());
        }

        @Test
        @Order(6)
        @DisplayName("Should do nothing when no organizations exist")
        void shouldDoNothingWithNoOrganizations() {
            when(organizationRepository.findAll()).thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> monthlyReporter.sendMonthlyReports());

            verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @Order(7)
        @DisplayName("Should skip admin when manager is not null but email is null")
        void shouldSkipAdminWhenManagerEmailIsNull() {
            Organization org = Organization.builder().id(1L).name("TestOrg")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            Managers managerNoEmail = new Managers();
            managerNoEmail.setId(1L);
            managerNoEmail.setEmail(null);

            Users admin = Users.builder().id(1L).username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .manager(managerNoEmail).organization(org).build();

            when(organizationRepository.findAll()).thenReturn(List.of(org));
            when(contractsRepository.countAllContractsByOrg(1L)).thenReturn(5);
            when(contractsRepository.countActiveContractsByOrg(1L)).thenReturn(3);
            when(contractsRepository.countExpiredContractsByOrg(1L)).thenReturn(1);
            when(contractsRepository.countNewContractsByOrgAndYearMonth(eq(1L), any(Integer.class), any(Integer.class))).thenReturn(1L);
            when(financialValuesRepository.sumFinancialAmountByOrgAndYearMonth(eq(1L), any(Integer.class), any(Integer.class))).thenReturn(0.0);
            when(usersRepository.findByOrganizationIdAndRoleRole(1L, "ADMIN")).thenReturn(List.of(admin));

            assertDoesNotThrow(() -> monthlyReporter.sendMonthlyReports());

            verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @Order(8)
        @DisplayName("Should handle email send failure gracefully")
        void shouldHandleEmailSendFailure() {
            Organization org = Organization.builder().id(1L).name("TestOrg")
                    .subscriptionTier(SubscriptionTier.FREE).build();

            Managers manager = new Managers();
            manager.setId(1L);
            manager.setEmail("admin@testorg.com");
            Users admin = Users.builder().id(1L).username("admin")
                    .role(Roles.builder().role("ADMIN").build())
                    .manager(manager).organization(org).build();

            when(organizationRepository.findAll()).thenReturn(List.of(org));
            when(contractsRepository.countAllContractsByOrg(1L)).thenReturn(10);
            when(contractsRepository.countActiveContractsByOrg(1L)).thenReturn(7);
            when(contractsRepository.countExpiredContractsByOrg(1L)).thenReturn(2);
            when(contractsRepository.countNewContractsByOrgAndYearMonth(eq(1L), any(Integer.class), any(Integer.class))).thenReturn(3L);
            when(financialValuesRepository.sumFinancialAmountByOrgAndYearMonth(eq(1L), any(Integer.class), any(Integer.class))).thenReturn(50000.0);
            when(usersRepository.findByOrganizationIdAndRoleRole(1L, "ADMIN")).thenReturn(List.of(admin));
            doThrow(new RuntimeException("SMTP error")).when(emailService).sendEmail(anyString(), anyString(), anyString());

            assertDoesNotThrow(() -> monthlyReporter.sendMonthlyReports());

            verify(emailService).sendEmail(eq("admin@testorg.com"), anyString(), anyString());
        }
    }
}
