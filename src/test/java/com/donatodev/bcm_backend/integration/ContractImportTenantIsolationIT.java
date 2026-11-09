package com.donatodev.bcm_backend.integration;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;
import com.donatodev.bcm_backend.support.TestTenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real uploaded workbooks and committed writes against the Flyway-managed MySQL schema. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Test: contract import duplicate checks stay within the tenant")
class ContractImportTenantIsolationIT extends AbstractMySQLIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrganizationRepository organizations;
    @Autowired private BusinessAreasRepository areas;
    @Autowired private CounterpartiesRepository counterparties;
    @Autowired private ContractsRepository contracts;

    private final List<Long> createdOrganizations = new ArrayList<>();
    private Organization org;
    private BusinessAreas area;
    private Contracts foreignContract;

    @BeforeEach
    void seedOtherTenant() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        org = organization();
        Organization otherOrg = organization();
        area = areas.save(BusinessAreas.builder().organization(org).name("Import Area").build());
        BusinessAreas foreignArea = areas.save(BusinessAreas.builder().organization(otherOrg).name("Import Area").build());
        foreignContract = saveContract(otherOrg, foreignArea, "SHARED-NUMBER");
        TestTenant.use(org.getId());
    }

    @AfterEach
    void cleanOnlyCreatedOrganizations() {
        TestTenant.clear();
        TenantContext.clear();
        for (Long orgId : createdOrganizations) {
            jdbc.update("DELETE FROM audit_logs WHERE org_id = ?", orgId);
            jdbc.update("DELETE FROM contracts WHERE organization_id = ?", orgId);
            jdbc.update("DELETE FROM counterparties WHERE organization_id = ?", orgId);
            jdbc.update("DELETE FROM business_areas WHERE organization_id = ?", orgId);
            jdbc.update("DELETE FROM organizations WHERE id = ?", orgId);
        }
        createdOrganizations.clear();
    }

    @ParameterizedTest(name = "{0}: imported={1}, errors={2}")
    @CsvSource({"otherTenant,1,0", "sameTenant,1,1", "sameFile,1,1"})
    void shouldCheckDuplicatesOnlyWithinCurrentTenant(String scenario, int imported, int errors) throws Exception {
        boolean alreadyLocal = scenario.equals("sameTenant");
        if (alreadyLocal) {
            saveContract(org, area, "SHARED-NUMBER");
        }
        String[] numbers = switch (scenario) {
            case "sameTenant" -> new String[] {"SHARED-NUMBER", "NEW-NUMBER"};
            case "sameFile" -> new String[] {"SHARED-NUMBER", "SHARED-NUMBER"};
            default -> new String[] {"SHARED-NUMBER"};
        };
        Map<String, Object> foreignBefore = foreignSnapshot();

        var response = mockMvc.perform(multipart("/contracts/import/excel").file(workbook(numbers))
                .with(user("import-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(numbers.length))
                .andExpect(jsonPath("$.importedCount").value(imported))
                .andExpect(jsonPath("$.errorCount").value(errors));
        if (errors > 0) {
            response.andExpect(jsonPath("$.errors[0].message").value("Numero contratto gia' esistente: SHARED-NUMBER"));
        }

        assertThat(contracts.countAllContractsByOrg(org.getId())).isEqualTo(alreadyLocal ? 2 : 1);
        assertThat(contracts.existsByContractNumberAndOrganizationId("SHARED-NUMBER", org.getId())).isTrue();
        assertThat(foreignSnapshot()).isEqualTo(foreignBefore);
        assertThat(contracts.countAllContractsByOrg(foreignContract.getOrganization().getId())).isEqualTo(1);
    }

    @Test
    void shouldRefuseMissingTenantWithoutImportingRows() throws Exception {
        TestTenant.clear();
        TenantContext.clear();
        Map<String, Object> foreignBefore = foreignSnapshot();

        mockMvc.perform(multipart("/contracts/import/excel").file(workbook("SHARED-NUMBER"))
                .with(user("import-admin").roles("ADMIN"))).andExpect(status().isForbidden());

        assertThat(contracts.countAllContractsByOrg(org.getId())).isZero();
        assertThat(foreignSnapshot()).isEqualTo(foreignBefore);
    }

    @Test
    void shouldKeepDatabaseUniquenessWithinTenant() {
        saveContract(org, area, "SHARED-NUMBER");

        assertThatThrownBy(() -> saveContract(org, area, "SHARED-NUMBER"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(contracts.countAllContractsByOrg(org.getId())).isEqualTo(1);
    }

    private Map<String, Object> foreignSnapshot() {
        return jdbc.queryForMap("SELECT * FROM contracts WHERE id = ?", foreignContract.getId());
    }

    private MockMultipartFile workbook(String... numbers) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Contratti");
            String[] header = {"Contract Number", "Customer", "Project", "Status",
                    "Start Date", "End Date", "Manager", "Business Area"};
            Row title = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) {
                title.createCell(i).setCellValue(header[i]);
            }
            for (int i = 0; i < numbers.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(numbers[i]);
                row.createCell(1).setCellValue("Import Customer");
                row.createCell(3).setCellValue("ACTIVE");
                row.createCell(4).setCellValue("01/01/2026");
                row.createCell(5).setCellValue("31/12/2026");
                row.createCell(7).setCellValue(area.getName());
            }
            workbook.write(output);
            return new MockMultipartFile("file", "contracts.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private Organization organization() {
        Organization result = organizations.save(Organization.builder().name("Import tenant IT")
                .slug(UUID.randomUUID().toString()).build());
        createdOrganizations.add(result.getId());
        return result;
    }

    private Contracts saveContract(Organization owner, BusinessAreas businessArea, String number) {
        Counterparty counterparty = counterparties.save(Counterparty.builder().organization(owner)
                .name(UUID.randomUUID().toString()).type(CounterpartyType.CUSTOMER).build());
        return contracts.saveAndFlush(Contracts.builder().organization(owner).businessArea(businessArea)
                .counterparty(counterparty).contractNumber(number).startDate(LocalDate.of(2026, 1, 1))
                .status(ContractStatus.ACTIVE).build());
    }
}
