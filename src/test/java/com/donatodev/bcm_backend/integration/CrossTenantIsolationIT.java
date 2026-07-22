package com.donatodev.bcm_backend.integration;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;

/**
 * Proves multi-tenant isolation holds at the real database, not just in a
 * mocked repository. The service layer ({@code ContractAccessGuard},
 * exercised with mocks in {@code ContractAccessGuardTest}) trusts that
 * {@code findByIdAndOrganization_Id}/{@code findByOrganization_Id} never
 * return a row from another tenant — this is what actually verifies that
 * trust against a real MySQL query plan and real foreign keys.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Integration Test: cross-tenant isolation against real MySQL")
class CrossTenantIsolationIT extends AbstractMySQLIntegrationTest {

    @Autowired private ContractsRepository contractsRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private BusinessAreasRepository businessAreasRepository;

    private Organization orgA;
    private Organization orgB;
    private BusinessAreas areaA;
    private BusinessAreas areaB;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        orgA = organizationRepository.save(Organization.builder()
                .name("Cross-Tenant IT Org A").slug("cross-tenant-it-org-a").build());
        orgB = organizationRepository.save(Organization.builder()
                .name("Cross-Tenant IT Org B").slug("cross-tenant-it-org-b").build());
        // business_areas.organization_id is NOT NULL (V8) — every area belongs to
        // exactly one tenant, so org A and org B each need their own.
        areaA = businessAreasRepository.save(BusinessAreas.builder()
                .name("Cross-Tenant IT Area").organization(orgA).build());
        areaB = businessAreasRepository.save(BusinessAreas.builder()
                .name("Cross-Tenant IT Area").organization(orgB).build());
    }

    private Contracts contractFor(Organization org, BusinessAreas area, String contractNumber) {
        return contractsRepository.save(Contracts.builder()
                .customerName("Customer of " + org.getName())
                .contractNumber(contractNumber)
                .businessArea(area)
                .startDate(LocalDate.now())
                .status(ContractStatus.ACTIVE)
                .organization(org)
                .build());
    }

    @Test
    @DisplayName("findByIdAndOrganization_Id: a contract in org A is invisible when looked up with org B's id")
    void contractLookupIsScopedToOwningOrganization() {
        Contracts contractInOrgA = contractFor(orgA, areaA, "IT-TEST-LOOKUP-001");

        Optional<Contracts> ownTenantLookup =
                contractsRepository.findByIdAndOrganization_Id(contractInOrgA.getId(), orgA.getId());
        Optional<Contracts> crossTenantLookup =
                contractsRepository.findByIdAndOrganization_Id(contractInOrgA.getId(), orgB.getId());

        assertTrue(ownTenantLookup.isPresent(), "The owning organization must see its own contract");
        assertTrue(crossTenantLookup.isEmpty(), "A contract must not be visible through another organization's id");
    }

    @Test
    @DisplayName("findByOrganization_Id: listing org A's contracts never includes org B's")
    void contractListingDoesNotLeakAcrossOrganizations() {
        Contracts contractInOrgA = contractFor(orgA, areaA, "IT-TEST-LIST-001");
        contractFor(orgB, areaB, "IT-TEST-LIST-002");

        List<Contracts> orgAContracts = contractsRepository.findByOrganization_Id(orgA.getId());

        assertEquals(1, orgAContracts.size());
        assertEquals(contractInOrgA.getId(), orgAContracts.get(0).getId());
    }
}
