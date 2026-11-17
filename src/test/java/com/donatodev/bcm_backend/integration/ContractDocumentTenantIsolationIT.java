package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;
import com.donatodev.bcm_backend.support.TestTenant;
import com.donatodev.bcm_backend.support.TestUploads;

import jakarta.persistence.EntityManager;

/**
 * OWASP File Upload Cheat Sheet, authorization: uploading, downloading and
 * deleting a contract's documents through real JWTs, on the Flyway schema.
 * Another organization's contract answers 404 (it does not exist for the
 * caller), a colleague's contract 403 for a manager; nothing is stored or
 * deleted in either case.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Test: contract documents stay within the tenant and the assigned manager")
class ContractDocumentTenantIsolationIT extends AbstractMySQLIntegrationTest {

    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void storeUploadsInATemporaryDirectory(DynamicPropertyRegistry registry) {
        registry.add("storage.upload-dir", () -> uploads.toString());
    }

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtils jwt;
    @Autowired private PlatformTransactionManager transactions;

    private final List<UUID> orgIds = new ArrayList<>();
    private UUID ownContract;
    private UUID colleagueContract;
    private UUID foreignContract;
    private String managerToken;
    private String adminToken;
    private String foreignAdminToken;

    @BeforeEach
    void seedCommittedData() {
        TenantContext.clear();
        TestTenant.clear();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Organization org = organization();
            Organization other = organization();
            Users manager = account(org, "MANAGER");
            Users colleague = account(org, "MANAGER");
            Users outsider = account(other, "ADMIN");
            ownContract = contract(org, manager);
            colleagueContract = contract(org, colleague);
            foreignContract = contract(other, outsider);
            managerToken = jwt.generateTokenFromUser(manager);
            adminToken = jwt.generateTokenFromUser(account(org, "ADMIN"));
            foreignAdminToken = jwt.generateTokenFromUser(outsider);
        });
    }

    @AfterEach
    void cleanOwnFixtures() {
        TenantContext.clear();
        for (UUID id : orgIds) {
            jdbc.update("DELETE FROM contract_documents WHERE org_id = ?", String.valueOf(id));
        }
        for (String table : List.of("audit_logs", "contracts", "users", "managers", "counterparties",
                "business_areas", "organizations")) {
            String column = table.equals("organizations") ? "id" : table.equals("audit_logs") ? "org_id" : "organization_id";
            for (UUID id : orgIds) {
                jdbc.update("DELETE FROM " + table + " WHERE " + column + " = ?", String.valueOf(id));
            }
        }
        orgIds.clear();
    }

    @ParameterizedTest(name = "upload as {0} to the {1} contract: {2}")
    @CsvSource({"manager,own,201", "manager,colleague,403", "manager,foreign,404",
            "admin,own,201", "admin,colleague,201", "admin,foreign,404"})
    void uploadIsAllowedOnlyWithinTheTenantAndTheAssignment(String caller, String target, int expected) throws Exception {
        UUID contractId = switch (target) {
            case "colleague" -> colleagueContract;
            case "foreign" -> foreignContract;
            default -> ownContract;
        };
        long filesBefore = storedFiles();

        mvc.perform(multipart("/contracts/" + contractId + "/documents").file(pdf("contratto.pdf"))
                        .header("Authorization", "Bearer " + (caller.equals("admin") ? adminToken : managerToken)))
                .andExpect(status().is(expected));

        assertThat(documentCount(contractId)).isEqualTo(expected == 201 ? 1 : 0);
        assertThat(storedFiles()).isEqualTo(filesBefore + (expected == 201 ? 1 : 0));
    }

    @Test
    void anotherOrganizationCanNeitherDownloadNorDeleteADocument() throws Exception {
        UUID documentId = upload(ownContract, adminToken);
        String path = "/contracts/" + ownContract + "/documents/" + documentId;

        mvc.perform(get(path + "/download").header("Authorization", "Bearer " + foreignAdminToken))
                .andExpect(status().isNotFound());
        mvc.perform(delete(path).header("Authorization", "Bearer " + foreignAdminToken))
                .andExpect(status().isNotFound());
        // Its own contract's id does not reach a document of another one either.
        mvc.perform(get("/contracts/" + foreignContract + "/documents/" + documentId + "/download")
                        .header("Authorization", "Bearer " + foreignAdminToken))
                .andExpect(status().isNotFound());

        assertThat(documentCount(ownContract)).isEqualTo(1);
        mvc.perform(get(path + "/download").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void aManagerCannotDownloadAColleaguesDocument() throws Exception {
        UUID documentId = upload(colleagueContract, adminToken);

        mvc.perform(get("/contracts/" + colleagueContract + "/documents/" + documentId + "/download")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void theDocumentIsServedAsAnAttachedPdfUnderItsCleanedName() throws Exception {
        MockMultipartFile declaredHtml = new MockMultipartFile("file", "C:\\fakepath\\Contratto \"2026\".pdf",
                "text/html", TestUploads.pdf());
        String body = mvc.perform(multipart("/contracts/" + ownContract + "/documents").file(declaredHtml)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("Contratto 2026.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(body).get("id").asText());

        mvc.perform(get("/contracts/" + ownContract + "/documents/" + documentId + "/download")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        // Stored under a random name inside the organization's directory, never under the one sent.
        try (Stream<Path> files = Files.walk(uploads)) {
            assertThat(files.filter(Files::isRegularFile).map(p -> p.getFileName().toString()))
                    .noneMatch(name -> name.contains("Contratto"));
        }
    }

    @Test
    void aFileThatIsNotAPdfIsRefusedAndNothingIsStored() throws Exception {
        long filesBefore = storedFiles();
        MockMultipartFile html = new MockMultipartFile("file", "contratto.pdf", "application/pdf",
                "<html><script>alert(1)</script></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(multipart("/contracts/" + ownContract + "/documents").file(html)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sono supportati solo file PDF"));

        assertThat(documentCount(ownContract)).isZero();
        assertThat(storedFiles()).isEqualTo(filesBefore);
    }

    @Test
    void aPdfWithJavaScriptIsRefusedRecordedAndNotStored() throws Exception {
        long filesBefore = storedFiles();
        byte[] withScript = TestUploads.pdf(document -> document.getDocumentCatalog()
                .setOpenAction(new org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript("app.alert(1)")));

        mvc.perform(multipart("/contracts/" + ownContract + "/documents")
                        .file(new MockMultipartFile("file", "contratto.pdf", "application/pdf", withScript))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnprocessableContent());

        assertThat(documentCount(ownContract)).isZero();
        assertThat(storedFiles()).isEqualTo(filesBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE org_id = ? AND action = ?",
                Integer.class, String.valueOf(orgIds.getFirst()), "SECURITY_UNSAFE_UPLOAD")).isEqualTo(1);
    }

    private UUID upload(UUID contractId, String token) throws Exception {
        String body = mvc.perform(multipart("/contracts/" + contractId + "/documents").file(pdf("contratto.pdf"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(tools.jackson.databind.json.JsonMapper.builder().build().readTree(body).get("id").asText());
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", TestUploads.pdf());
    }

    private int documentCount(UUID contractId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM contract_documents WHERE contract_id = ?",
                Integer.class, String.valueOf(contractId));
    }

    private static long storedFiles() throws IOException {
        try (Stream<Path> files = Files.walk(uploads)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private Organization organization() {
        Organization org = save(Organization.builder().name("Upload IT").slug(UUID.randomUUID().toString()).build());
        orgIds.add(org.getId());
        return org;
    }

    private Users account(Organization org, String role) {
        List<Roles> found = em.createQuery("select r from Roles r where r.role = :role", Roles.class)
                .setParameter("role", role).getResultList();
        Roles r = found.isEmpty() ? save(Roles.builder().role(role).build()) : found.getFirst();
        Managers manager = save(Managers.builder().organization(org).firstName("Upload").lastName("Manager")
                .email(UUID.randomUUID() + "@example.com").build());
        return save(Users.builder().organization(org).manager(manager).role(r).username(UUID.randomUUID().toString())
                .passwordHash("test-password").verified(true).build());
    }

    private UUID contract(Organization org, Users owner) {
        String name = UUID.randomUUID().toString();
        BusinessAreas area = save(BusinessAreas.builder().organization(org).name(name).build());
        Counterparty cp = save(Counterparty.builder().organization(org).name(name).type(CounterpartyType.CUSTOMER).build());
        Contracts contract = save(Contracts.builder().organization(org).businessArea(area).counterparty(cp)
                .manager(owner.getManager()).contractNumber(name).startDate(LocalDate.of(2026, Month.JANUARY, 1))
                .status(ContractStatus.ACTIVE).build());
        return contract.getId();
    }

    private <T> T save(T value) {
        em.persist(value);
        return value;
    }
}
