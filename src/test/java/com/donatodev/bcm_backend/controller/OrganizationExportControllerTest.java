package com.donatodev.bcm_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.donatodev.bcm_backend.entity.AuditLog;
import com.donatodev.bcm_backend.entity.Budget;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractDocument;
import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.ContractManager;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.ContractTemplate;
import com.donatodev.bcm_backend.entity.ContractWorkflowEvent;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.FinancialCategory;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.entity.InvoiceLineItem;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.MlResultCache;
import com.donatodev.bcm_backend.entity.Notification;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.RiskFeedback;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.SepaPaymentBatch;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.entity.TermsAcceptance;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.entity.WorkflowAction;
import com.donatodev.bcm_backend.entity.WorkflowStage;
import com.donatodev.bcm_backend.repository.AuditLogRepository;
import com.donatodev.bcm_backend.repository.BudgetRepository;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.donatodev.bcm_backend.repository.ContractHistoryRepository;
import com.donatodev.bcm_backend.repository.ContractManagerRepository;
import com.donatodev.bcm_backend.repository.ContractTemplateRepository;
import com.donatodev.bcm_backend.repository.ContractWorkflowEventRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.MlResultCacheRepository;
import com.donatodev.bcm_backend.repository.NotificationRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RiskFeedbackRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.SepaPaymentBatchRepository;
import com.donatodev.bcm_backend.repository.TermsAcceptanceRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.LocalStorageService;
import com.donatodev.bcm_backend.util.TestDataCleaner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationExportControllerTest {

    private static final String EXPORT_URL = "/organizations/me/export";

    /** The data files that hold one element per seeded row, in archive order. */
    private static final List<String> RECORD_FILES = List.of(
            "data/managers.json", "data/users.json", "data/business_areas.json",
            "data/financial_types.json", "data/counterparties.json", "data/contracts.json",
            "data/contract_managers.json", "data/contract_history.json",
            "data/contract_workflow_events.json", "data/financial_values.json", "data/budgets.json",
            "data/contract_documents.json", "data/electronic_invoices.json",
            "data/sepa_payment_batches.json", "data/contract_templates.json", "data/risk_feedback.json",
            "data/ml_analyses.json", "data/notifications.json", "data/audit_logs.json",
            "data/terms_acceptances.json");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private ManagersRepository managersRepository;
    @Autowired private RolesRepository rolesRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private TestDataCleaner testDataCleaner;
    @Autowired private BusinessAreasRepository businessAreasRepository;
    @Autowired private FinancialTypesRepository financialTypesRepository;
    @Autowired private CounterpartiesRepository counterpartiesRepository;
    @Autowired private ContractsRepository contractsRepository;
    @Autowired private ContractManagerRepository contractManagerRepository;
    @Autowired private ContractHistoryRepository contractHistoryRepository;
    @Autowired private ContractWorkflowEventRepository workflowEventRepository;
    @Autowired private FinancialValuesRepository financialValuesRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private ContractDocumentRepository contractDocumentRepository;
    @Autowired private ElectronicInvoiceRepository electronicInvoiceRepository;
    @Autowired private SepaPaymentBatchRepository sepaPaymentBatchRepository;
    @Autowired private ContractTemplateRepository contractTemplateRepository;
    @Autowired private RiskFeedbackRepository riskFeedbackRepository;
    @Autowired private MlResultCacheRepository mlResultCacheRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TermsAcceptanceRepository termsAcceptanceRepository;
    @Autowired private LocalStorageService storageService;

    private Organization org;
    private Roles adminRole;

    @BeforeEach
    void setup() {
        // Tables TestDataCleaner doesn't know, in foreign-key order around it.
        notificationRepository.deleteAll();
        electronicInvoiceRepository.deleteAll();
        contractDocumentRepository.deleteAll();
        testDataCleaner.clean();
        counterpartiesRepository.deleteAll();
        mlResultCacheRepository.deleteAll();
        auditLogRepository.deleteAll();
        termsAcceptanceRepository.deleteAll();
        organizationRepository.deleteAll();

        org = organizationRepository.save(Organization.builder()
                .name("Export Corp").slug("export-corp").subscriptionTier(SubscriptionTier.FREE)
                .iban("IT60X0542811101000000123456").build());
        Managers manager = managersRepository.save(Managers.builder()
                .firstName("Admin").lastName("User").email("admin@export.test").organization(org).build());
        adminRole = rolesRepository.save(Roles.builder().role("ADMIN").build());
        usersRepository.save(Users.builder()
                .username("export-admin").passwordHash("pwd").verified(true)
                .role(adminRole).manager(manager).organization(org).build());
    }

    /** One row in every table the export covers, with {@code marker} in the text fields. */
    private void seedOneOfEverything(Organization o, Users u, Managers m, String marker) {
        BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                .name(marker + " area").organization(o).build());
        FinancialTypes type = financialTypesRepository.save(FinancialTypes.builder()
                .name(marker + " type").category(FinancialCategory.REVENUE).organization(o).build());
        Counterparty counterparty = counterpartiesRepository.save(Counterparty.builder()
                .name(marker + " counterparty").type(CounterpartyType.CUSTOMER).organization(o).build());
        Contracts contract = contractsRepository.save(Contracts.builder()
                .contractNumber(marker + "-CNT").projectName(marker + " project").counterparty(counterparty)
                .businessArea(area).manager(m).financialType(type).startDate(LocalDate.of(2026, 1, 1))
                .status(ContractStatus.ACTIVE).organization(o).build());
        contractManagerRepository.save(ContractManager.builder().contract(contract).manager(m).build());
        contractHistoryRepository.save(ContractHistory.builder().contract(contract).modifiedBy(u)
                .previousStatus(ContractStatus.ACTIVE).newStatus(ContractStatus.EXPIRED).build());
        workflowEventRepository.save(ContractWorkflowEvent.builder().contract(contract).actor(u)
                .toStage(WorkflowStage.IN_REVIEW).action(WorkflowAction.SUBMIT).comment(marker + " comment").build());
        FinancialValues value = financialValuesRepository.save(FinancialValues.builder()
                .month(1).year(2026).financialAmount(100).financialType(type).businessArea(area)
                .contract(contract).organization(o).build());
        budgetRepository.save(Budget.builder().businessArea(area).category(FinancialCategory.REVENUE)
                .year(2026).targetAmount(1000).organization(o).build());
        contractDocumentRepository.save(ContractDocument.builder().contract(contract).orgId(o.getId())
                .storagePath(marker + "/doc.pdf").fileName(marker + "-doc.pdf").fileSize(10L)
                .contentType("application/pdf").versionNumber(1).embedding("[0.1,0.2]").build());
        SepaPaymentBatch batch = sepaPaymentBatchRepository.save(SepaPaymentBatch.builder()
                .contract(contract).orgId(o.getId()).messageId(marker + "-MSG")
                .executionDate(LocalDate.of(2026, 2, 1)).totalAmount(BigDecimal.TEN).currency("EUR")
                .numberOfTransactions(1).storagePath(marker + "/sepa.xml").fileName(marker + "-sepa.xml").build());
        ElectronicInvoice invoice = ElectronicInvoice.builder().contract(contract).orgId(o.getId())
                .storagePath(marker + "/invoice.xml").fileName(marker + "-invoice.xml").fileSize(10L)
                .contentType("application/xml").supplierName(marker + " supplier")
                .sepaBatch(batch).matchedFinancialValue(value).build();
        invoice.getLineItems().add(InvoiceLineItem.builder().invoice(invoice).lineNumber(1)
                .description(marker + " line").build());
        electronicInvoiceRepository.save(invoice);
        contractTemplateRepository.save(ContractTemplate.builder().name(marker + " template")
                .businessArea(area).defaultManager(m).orgId(o.getId()).build());
        riskFeedbackRepository.save(RiskFeedback.builder().contract(contract).submittedBy(u)
                .organizationId(o.getId()).riskScore(0.5).riskLevel("MEDIUM").agree(true).build());
        mlResultCacheRepository.save(MlResultCache.builder().orgId(o.getId()).cacheKey("FORECAST_6")
                .jsonResult("{\"label\":\"" + marker + "\"}").computedAt(LocalDateTime.now()).build());
        notificationRepository.save(Notification.builder().user(u).orgId(o.getId())
                .title(marker + " title").message("message").build());
        auditLogRepository.save(AuditLog.builder().action("TEST").entityType("Contract").orgId(o.getId())
                .username(u.getUsername()).details(marker + " details").build());
        termsAcceptanceRepository.save(TermsAcceptance.builder().organizationId(o.getId())
                .organizationName(o.getName()).userId(u.getId()).username(u.getUsername())
                .signerEmail(m.getEmail()).termsVersion("2026-10-28").onerousClauses("5, 9")
                .professionalUseDeclared(true).signatureMethod(TermsAcceptance.SIGNATURE_EMAIL_OTP)
                .codeSentAt(LocalDateTime.now()).acceptedAt(LocalDateTime.now()).build());
    }

    private Users seedOtherOrganization() {
        Organization other = organizationRepository.save(Organization.builder()
                .name("theirs Corp").slug("theirs-corp").subscriptionTier(SubscriptionTier.FREE).build());
        Managers manager = managersRepository.save(Managers.builder()
                .firstName("theirs").lastName("Admin").email("admin@theirs.test").organization(other).build());
        return usersRepository.save(Users.builder()
                .username("theirs-admin").passwordHash("pwd").verified(true)
                .role(adminRole).manager(manager).organization(other).build());
    }

    /** Runs the request through the async dispatch and returns the archive's entries by path. */
    private Map<String, byte[]> export() throws Exception {
        MvcResult started = mockMvc.perform(get(EXPORT_URL))
                .andExpect(request().asyncStarted())
                .andReturn();
        byte[] zip = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andReturn().getResponse().getContentAsByteArray();
        return unzip(zip);
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                entries.put(e.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "ADMIN")
    @DisplayName("Admin downloads a ZIP with the organization data and a manifest written last")
    void exportsOrganizationAndManifest() throws Exception {
        Map<String, byte[]> entries = export();

        List<String> expected = new ArrayList<>();
        expected.add("data/organization.json");
        expected.addAll(RECORD_FILES);
        expected.add("manifest.json");
        assertThat(entries.keySet()).containsExactlyElementsOf(expected);

        JsonNode organization = objectMapper.readTree(entries.get("data/organization.json"));
        assertThat(organization.get("name").asText()).isEqualTo("Export Corp");
        // The customer's own copy: unmasked, unlike GET /organizations/me.
        assertThat(organization.get("iban").asText()).isEqualTo("IT60X0542811101000000123456");
        assertThat(organization.has("stripeCustomerId")).isFalse();

        JsonNode manifest = objectMapper.readTree(entries.get("manifest.json"));
        assertThat(manifest.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(manifest.get("organizationId").asLong()).isEqualTo(org.getId());
        JsonNode file = manifest.get("files").get(0);
        byte[] content = entries.get("data/organization.json");
        assertThat(file.get("path").asText()).isEqualTo("data/organization.json");
        assertThat(file.get("records").asInt()).isEqualTo(1);
        assertThat(file.get("sizeBytes").asLong()).isEqualTo(content.length);
        assertThat(file.get("sha256").asText())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "ADMIN")
    @DisplayName("Exports managers and users, without credentials")
    void exportsManagersAndUsersWithoutCredentials() throws Exception {
        Users user = usersRepository.findByUsername("export-admin").orElseThrow();
        user.setTotpSecretEncrypted("encrypted-totp-secret");
        user.setCalendarToken("calendar-secret");
        usersRepository.save(user);

        Map<String, byte[]> entries = export();

        JsonNode managers = objectMapper.readTree(entries.get("data/managers.json"));
        assertThat(managers).hasSize(1);
        assertThat(managers.get(0).get("email").asText()).isEqualTo("admin@export.test");

        JsonNode users = objectMapper.readTree(entries.get("data/users.json"));
        assertThat(users).hasSize(1);
        assertThat(users.get(0).get("username").asText()).isEqualTo("export-admin");
        assertThat(users.get(0).get("role").asText()).isEqualTo("ADMIN");
        assertThat(users.get(0).get("managerId").asLong()).isEqualTo(managers.get(0).get("id").asLong());
        String usersJson = new String(entries.get("data/users.json"), StandardCharsets.UTF_8);
        assertThat(usersJson).doesNotContain("passwordHash", "pwd", "totp-secret", "calendar-secret",
                "failedLoginAttempts", "lockedUntil");
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "ADMIN")
    @DisplayName("Every data file holds this organization's rows and none of another's")
    void everyFileIsScopedToTheOrganization() throws Exception {
        Users admin = usersRepository.findByUsername("export-admin").orElseThrow();
        seedOneOfEverything(org, admin, admin.getManager(), "ours");
        Users theirs = seedOtherOrganization();
        seedOneOfEverything(theirs.getOrganization(), theirs, theirs.getManager(), "theirs");

        Map<String, byte[]> entries = export();

        for (String path : RECORD_FILES) {
            // audit_logs also holds the DATA_EXPORTED entry of this very export.
            int expected = path.equals("data/audit_logs.json") ? 2 : 1;
            assertThat(objectMapper.readTree(entries.get(path))).as(path).hasSize(expected);
            assertThat(new String(entries.get(path), StandardCharsets.UTF_8)).as(path)
                    .doesNotContainIgnoringCase("theirs");
        }
        assertThat(new String(entries.get("manifest.json"), StandardCharsets.UTF_8))
                .doesNotContainIgnoringCase("theirs");
        assertThat(entries.keySet()).noneMatch(path -> path.contains("theirs"));
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "ADMIN")
    @DisplayName("Copies the original files under files/, where their metadata points, with a safe name")
    void includesOriginalFiles() throws Exception {
        Users admin = usersRepository.findByUsername("export-admin").orElseThrow();
        seedOneOfEverything(org, admin, admin.getManager(), "ours");
        Contracts contract = contractsRepository.findByOrganization_Id(org.getId()).get(0);
        byte[] pdf = "%PDF-1.7 original bytes".getBytes(StandardCharsets.UTF_8);
        ContractDocument document = contractDocumentRepository.save(ContractDocument.builder()
                .contract(contract).orgId(org.getId())
                .storagePath(storageService.storeDocument(org.getId(), contract.getId(), pdf))
                .fileName("..\\../evil name.pdf").fileSize((long) pdf.length)
                .contentType("application/pdf").versionNumber(2).build());

        Map<String, byte[]> entries = export();

        JsonNode documents = objectMapper.readTree(entries.get("data/contract_documents.json"));
        String archivePath = null;
        for (JsonNode d : documents) {
            if (d.get("id").asLong() == document.getId()) {
                archivePath = d.get("archivePath").asText();
            }
        }
        assertThat(archivePath).isEqualTo("files/contracts/%d/%d-__.._evil name.pdf"
                .formatted(contract.getId(), document.getId()));
        assertThat(entries.get(archivePath)).isEqualTo(pdf);
        for (String path : entries.keySet()) {
            assertThat(path).doesNotContain("\\");
            assertThat(Arrays.asList(path.split("/"))).as(path).doesNotContain("..");
        }

        JsonNode manifest = objectMapper.readTree(entries.get("manifest.json"));
        JsonNode listed = null;
        for (JsonNode f : manifest.get("files")) {
            if (f.get("path").asText().equals(archivePath)) {
                listed = f;
            }
        }
        assertThat(listed).isNotNull();
        assertThat(listed.get("records").isNull()).isTrue();
        assertThat(listed.get("sizeBytes").asLong()).isEqualTo(pdf.length);
        assertThat(listed.get("sha256").asText())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)));
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "ADMIN")
    @DisplayName("A file gone from disk is listed as missing; the rest of the export still arrives")
    void listsMissingFiles() throws Exception {
        Users admin = usersRepository.findByUsername("export-admin").orElseThrow();
        // The seed's storage paths point at nothing on disk.
        seedOneOfEverything(org, admin, admin.getManager(), "ours");

        Map<String, byte[]> entries = export();

        JsonNode manifest = objectMapper.readTree(entries.get("manifest.json"));
        List<String> missing = new ArrayList<>();
        manifest.get("missingFiles").forEach(m -> missing.add(m.asText()));
        assertThat(missing).hasSize(3).allMatch(path -> path.startsWith("files/"));
        assertThat(missing).noneMatch(entries::containsKey);
        assertThat(entries).containsKey("data/contracts.json");
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "ADMIN")
    @DisplayName("Leaves out storage paths and embeddings, links records by id, parses ML results")
    void shapesTheRecords() throws Exception {
        Users admin = usersRepository.findByUsername("export-admin").orElseThrow();
        seedOneOfEverything(org, admin, admin.getManager(), "ours");

        Map<String, byte[]> entries = export();

        for (String path : List.of("data/contract_documents.json", "data/electronic_invoices.json",
                "data/sepa_payment_batches.json")) {
            assertThat(new String(entries.get(path), StandardCharsets.UTF_8)).as(path)
                    .doesNotContain("storagePath", "ours/");
        }
        assertThat(new String(entries.get("data/contract_documents.json"), StandardCharsets.UTF_8))
                .doesNotContain("embedding", "0.1");

        JsonNode invoice = objectMapper.readTree(entries.get("data/electronic_invoices.json")).get(0);
        JsonNode batch = objectMapper.readTree(entries.get("data/sepa_payment_batches.json")).get(0);
        assertThat(invoice.get("lineItems").get(0).get("description").asText()).isEqualTo("ours line");
        assertThat(invoice.get("sepaBatchId").asLong()).isEqualTo(batch.get("id").asLong());

        JsonNode contract = objectMapper.readTree(entries.get("data/contracts.json")).get(0);
        JsonNode manager = objectMapper.readTree(entries.get("data/managers.json")).get(0);
        assertThat(contract.get("managerId").asLong()).isEqualTo(manager.get("id").asLong());

        JsonNode analysis = objectMapper.readTree(entries.get("data/ml_analyses.json")).get(0);
        assertThat(analysis.get("result").get("label").asText()).isEqualTo("ours");
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "ADMIN")
    @DisplayName("Names the file after the organization and forbids caching")
    void setsDownloadHeaders() throws Exception {
        MvcResult started = mockMvc.perform(get(EXPORT_URL)).andReturn();
        mockMvc.perform(asyncDispatch(started))
                .andExpect(header().string("Content-Disposition",
                        matchesPattern(
                                "attachment; filename=\"bcm-export-export-corp-\\d{4}-\\d{2}-\\d{2}\\.zip\"")))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "ADMIN")
    @DisplayName("Lets a cross-origin frontend read the download name")
    void exposesContentDispositionToTheFrontend() throws Exception {
        MvcResult started = mockMvc.perform(get(EXPORT_URL).header("Origin", "http://localhost:3000"))
                .andReturn();
        mockMvc.perform(asyncDispatch(started))
                .andExpect(header().string("Access-Control-Expose-Headers",
                        containsString("Content-Disposition")));
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "ADMIN")
    @DisplayName("Every export is recorded in the audit log")
    void recordsTheExport() throws Exception {
        export();

        assertThat(auditLogRepository.findAll())
                .anySatisfy(log -> {
                    assertThat(log.getAction()).isEqualTo("DATA_EXPORTED");
                    assertThat(log.getUsername()).isEqualTo("export-admin");
                    assertThat(log.getOrgId()).isEqualTo(org.getId());
                });
    }

    @Test
    @WithMockUser(username = "export-admin", roles = "MANAGER")
    @DisplayName("A manager cannot export the organization")
    void managerIsForbidden() throws Exception {
        mockMvc.perform(get(EXPORT_URL)).andExpect(status().isForbidden());
        assertThat(auditLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Unauthenticated request returns 401")
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get(EXPORT_URL)).andExpect(status().isUnauthorized());
    }
}
