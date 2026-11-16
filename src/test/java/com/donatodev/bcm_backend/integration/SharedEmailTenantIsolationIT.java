package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.NotificationRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.AgentNotificationService;
import com.donatodev.bcm_backend.service.NotificationService;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;

/**
 * OWASP Multi-Tenant Security Cheat Sheet, against the real MySQL schema: a
 * manager's email is unique only within an organization
 * ({@code uq_mgr_email_org}), so one accountant can follow two companies under
 * the same address. One company's contract notifications must never reach the
 * account the accountant holds in the other.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ AgentNotificationService.class, NotificationService.class })
@DisplayName("Integration Test: one email in two organizations, against real MySQL")
class SharedEmailTenantIsolationIT extends AbstractMySQLIntegrationTest {

    private static final String SHARED_EMAIL = "studio.rossi@example.com";

    @Autowired private AgentNotificationService agentNotificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ManagersRepository managersRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private RolesRepository rolesRepository;
    @Autowired private ContractsRepository contractsRepository;
    @Autowired private BusinessAreasRepository businessAreasRepository;
    @Autowired private CounterpartiesRepository counterpartiesRepository;

    private Organization alfa;
    private Organization beta;
    private Managers accountantInAlfa;
    private Managers accountantInBeta;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        alfa = organizationRepository.save(Organization.builder().name("Alfa Srl").slug("shared-email-it-alfa").build());
        beta = organizationRepository.save(Organization.builder().name("Beta Spa").slug("shared-email-it-beta").build());
        // The schema allows it: the same person, followed by both companies.
        accountantInAlfa = manager(alfa);
        accountantInBeta = manager(beta);
    }

    @Test
    @DisplayName("only the other company holds an account: Alfa's contract tells no one there")
    void anAccountOnlyInTheOtherCompanyHearsNothing() {
        Users inBeta = account(accountantInBeta, beta, "rossi-beta");

        agentNotificationService.notifyExpiringContract(expiringContract(alfa, accountantInAlfa));

        assertThat(notificationRepository.findByUserIdAndOrgIdOrderByCreatedAtDesc(inBeta.getId(), beta.getId()))
                .isEmpty();
        assertThat(notificationRepository.findAllByOrgId(alfa.getId())).isEmpty();
    }

    @Test
    @DisplayName("an account in each company: Alfa's contract reaches only the Alfa account, with no error")
    void eachCompanyTellsItsOwnAccount() {
        Users inAlfa = account(accountantInAlfa, alfa, "rossi-alfa");
        Users inBeta = account(accountantInBeta, beta, "rossi-beta");

        agentNotificationService.notifyExpiringContract(expiringContract(alfa, accountantInAlfa));

        assertThat(notificationRepository.findByUserIdAndOrgIdOrderByCreatedAtDesc(inAlfa.getId(), alfa.getId()))
                .hasSize(1);
        assertThat(notificationRepository.findAllByOrgId(beta.getId())).isEmpty();
        assertThat(inBeta.getId()).isNotEqualTo(inAlfa.getId());
    }

    @Test
    @DisplayName("forgot password finds both accounts at the shared address, one per organization")
    void forgotPasswordFindsEveryAccountAtTheAddress() {
        account(accountantInAlfa, alfa, "rossi-alfa");
        account(accountantInBeta, beta, "rossi-beta");

        assertThat(usersRepository.findAllByManagerEmailIgnoreCase(SHARED_EMAIL.toUpperCase()))
                .extracting(user -> user.getOrganization().getId())
                .containsExactlyInAnyOrder(alfa.getId(), beta.getId());
    }

    private Managers manager(Organization org) {
        return managersRepository.save(Managers.builder().firstName("Anna").lastName("Rossi")
                .email(SHARED_EMAIL).organization(org).build());
    }

    private Users account(Managers manager, Organization org, String username) {
        Roles role = rolesRepository.findByRole("MANAGER")
                .orElseGet(() -> rolesRepository.save(Roles.builder().role("MANAGER").build()));
        return usersRepository.save(Users.builder().username(username + "-" + UUID.randomUUID().toString().substring(0, 6))
                .passwordHash("x").verified(true).manager(manager).role(role).organization(org).build());
    }

    private Contracts expiringContract(Organization org, Managers manager) {
        BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder().name("Area").organization(org).build());
        Counterparty counterparty = counterpartiesRepository.save(Counterparty.builder().name("Cliente di " + org.getName())
                .type(CounterpartyType.CUSTOMER).organization(org).build());
        return contractsRepository.save(Contracts.builder().contractNumber("SE-" + UUID.randomUUID().toString().substring(0, 8))
                .counterparty(counterparty).businessArea(area).manager(manager).organization(org)
                .status(ContractStatus.ACTIVE).startDate(LocalDate.now().minusMonths(6))
                .endDate(LocalDate.now().plusDays(30)).build());
    }
}
