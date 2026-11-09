package com.donatodev.bcm_backend.integration;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractManagerRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.service.ContractService;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;
import com.donatodev.bcm_backend.support.TestTenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** No test transaction: HTTP writes and service rollback must hold on their own. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Test: collaborator authorization and atomic replacement against real MySQL")
class ContractCollaboratorsAuthorizationIT extends AbstractMySQLIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ContractService contractService;
    @Autowired private OrganizationRepository organizations;
    @Autowired private ManagersRepository managers;
    @Autowired private ContractsRepository contracts;
    @Autowired private CounterpartiesRepository counterparties;
    @Autowired private BusinessAreasRepository areas;
    @MockitoSpyBean private ContractManagerRepository collaborators;

    private final List<Long> createdOrganizations = new ArrayList<>();
    private Organization org;
    private Managers original;
    private Managers replacement;
    private Managers secondReplacement;
    private Managers foreignManager;
    private Contracts contract;
    private Contracts foreignContract;

    @BeforeEach
    void seedCommittedAssignments() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        org = organization();
        Organization otherOrg = organization();
        original = manager(org);
        replacement = manager(org);
        secondReplacement = manager(org);
        foreignManager = manager(otherOrg);
        contract = contract(org, original);
        foreignContract = contract(otherOrg, foreignManager);
        collaborators.insertIgnore(contract.getId(), original.getId());
        collaborators.insertIgnore(foreignContract.getId(), foreignManager.getId());
        TestTenant.use(org.getId());
    }

    @AfterEach
    void cleanOnlyCreatedOrganizations() {
        TestTenant.clear();
        TenantContext.clear();
        for (Long orgId : createdOrganizations) {
            // Contract deletion cascades to contract_manager in the real schema.
            jdbc.update("DELETE FROM contracts WHERE organization_id = ?", orgId);
            jdbc.update("DELETE FROM counterparties WHERE organization_id = ?", orgId);
            jdbc.update("DELETE FROM business_areas WHERE organization_id = ?", orgId);
            jdbc.update("DELETE FROM managers WHERE organization_id = ?", orgId);
            jdbc.update("DELETE FROM organizations WHERE id = ?", orgId);
        }
        createdOrganizations.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign", "missing", "mixedForeign", "mixedMissing"})
    void shouldRejectInaccessibleManagersWithoutChangingEitherTenant(String scenario) throws Exception {
        long rejectedId = scenario.endsWith("Missing") || scenario.equals("missing")
                ? Long.MAX_VALUE : foreignManager.getId();
        String ids = scenario.startsWith("mixed") ? replacement.getId() + "," + rejectedId : "" + rejectedId;

        patchAsAdmin(contract.getId(), "[" + ids + "]", 404);

        assertOriginalAssignments();
    }

    @Test
    void shouldReplaceWithManagersFromSameTenant() throws Exception {
        patchAsAdmin(contract.getId(), "[" + replacement.getId() + "," + secondReplacement.getId() + "]", 204);

        assertThat(managerIds(contract)).containsExactlyInAnyOrder(replacement.getId(), secondReplacement.getId());
        assertForeignAssignments();
    }

    @Test
    void shouldTolerateDuplicateManagerIds() throws Exception {
        patchAsAdmin(contract.getId(), "[" + replacement.getId() + "," + replacement.getId() + "]", 204);

        assertThat(managerIds(contract)).containsExactly(replacement.getId());
        assertForeignAssignments();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null"})
    void shouldClearCollaborators(String ids) throws Exception {
        patchAsAdmin(contract.getId(), ids, 204);

        assertThat(managerIds(contract)).isEmpty();
        assertForeignAssignments();
    }

    @Test
    void shouldRejectNullElementWithoutDeletingExistingAssignments() throws Exception {
        patchAsAdmin(contract.getId(), "[" + replacement.getId() + ",null]", 400);

        assertOriginalAssignments();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldHideForeignAndMissingContracts(boolean foreign) throws Exception {
        patchAsAdmin(foreign ? foreignContract.getId() : Long.MAX_VALUE,
                "[" + replacement.getId() + "]", 404);

        assertOriginalAssignments();
    }

    @Test
    void shouldRefuseManagerRole() throws Exception {
        mockMvc.perform(patch("/contracts/{id}/collaborators", contract.getId())
                .with(user("manager").roles("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"managerIds\":[" + replacement.getId() + "]}"))
                .andExpect(status().isForbidden());

        assertOriginalAssignments();
    }

    @Test
    void shouldRefuseMissingTenant() throws Exception {
        TestTenant.clear();
        TenantContext.clear();

        patchAsAdmin(contract.getId(), "[" + replacement.getId() + "]", 403);

        assertOriginalAssignments();
    }

    @Test
    void shouldRollBackDeletionAndEarlierInsertWhenLaterInsertFails() {
        TenantContext.set(org.getId());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(managerIds(contract)).containsExactly(replacement.getId());
            throw new DataIntegrityViolationException("Simulated failure on second collaborator insert");
        }).when(collaborators).insertIgnore(contract.getId(), secondReplacement.getId());

        Long contractId = contract.getId();
        List<Long> replacementIds = List.of(replacement.getId(), secondReplacement.getId());
        assertThatThrownBy(() -> contractService.setCollaborators(contractId, replacementIds))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertOriginalAssignments();
    }

    private void patchAsAdmin(Long contractId, String ids, int expectedStatus) throws Exception {
        mockMvc.perform(patch("/contracts/{id}/collaborators", contractId)
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"managerIds\":" + ids + "}"))
                .andExpect(status().is(expectedStatus));
    }

    private List<Long> managerIds(Contracts target) {
        return jdbc.queryForList("SELECT manager_id FROM contract_manager WHERE contract_id = ? ORDER BY manager_id",
                Long.class, target.getId());
    }

    private void assertOriginalAssignments() {
        assertThat(managerIds(contract)).containsExactly(original.getId());
        assertForeignAssignments();
    }

    private void assertForeignAssignments() {
        assertThat(managerIds(foreignContract)).containsExactly(foreignManager.getId());
    }

    private Organization organization() {
        Organization result = organizations.save(Organization.builder().name("Collaborator authorization IT")
                .slug(UUID.randomUUID().toString()).build());
        createdOrganizations.add(result.getId());
        return result;
    }

    private Managers manager(Organization organization) {
        return managers.save(Managers.builder().organization(organization).firstName("Test").lastName("Manager")
                .email(UUID.randomUUID() + "@example.com").build());
    }

    private Contracts contract(Organization organization, Managers manager) {
        BusinessAreas area = areas.save(BusinessAreas.builder().organization(organization).name("IT Area").build());
        Counterparty counterparty = counterparties.save(Counterparty.builder().organization(organization)
                .name("IT Customer").type(CounterpartyType.CUSTOMER).build());
        return contracts.save(Contracts.builder().organization(organization).manager(manager).businessArea(area)
                .counterparty(counterparty).contractNumber(UUID.randomUUID().toString())
                .startDate(LocalDate.of(2026, Month.JANUARY, 1)).status(ContractStatus.ACTIVE).build());
    }
}
