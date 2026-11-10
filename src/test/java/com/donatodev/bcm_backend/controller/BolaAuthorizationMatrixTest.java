package com.donatodev.bcm_backend.controller;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.*;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.service.LocalStorageService;
import com.donatodev.bcm_backend.service.MlProxyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** JWT -> filters -> real services -> real repositories; only external IO is replaced. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BolaAuthorizationMatrixTest {
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtUtils jwt;
    @Autowired private tools.jackson.databind.ObjectMapper json;
    @MockitoBean private LocalStorageService storage;
    @MockitoBean private MlProxyService ml;

    private record Route(String method, String path, String payload, boolean managerAllowed) {
        @Override public String toString() { return method + " " + path; }
    }
    private record Data(Organization org, BusinessAreas area, Managers manager, Users user, Counterparty counterparty,
                        FinancialTypes type, Contracts contract, FinancialValues value, ContractHistory history,
                        ContractTemplate template, Budget budget, ContractDocument document,
                        ElectronicInvoice invoice, SepaPaymentBatch batch) {}
    private Data own;
    private Data colleague;
    private Data foreign;
    private Users admin;
    private String adminToken;
    private String managerToken;

    private static final List<String> SNAPSHOT_TABLES = List.of("contracts", "contract_history", "contract_manager",
            "business_areas", "managers", "users", "counterparties", "financial_types", "financial_values",
            "contract_templates", "budgets", "contract_documents", "electronic_invoices", "sepa_payment_batches",
            "contract_workflow_events", "risk_feedback", "notifications");

    @BeforeEach
    void seed() {
        TenantContext.clear();
        com.donatodev.bcm_backend.support.TestTenant.clear();
        Organization org = persist(Organization.builder().name("BOLA A").slug(UUID.randomUUID().toString()).build());
        Organization other = persist(Organization.builder().name("BOLA B").slug(UUID.randomUUID().toString()).build());
        own = data(org);
        colleague = data(org);
        foreign = data(other);
        admin = account(org, null, "ADMIN");
        adminToken = jwt.generateTokenFromUser(admin);
        managerToken = jwt.generateTokenFromUser(own.user());
        flushAndClear();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
        com.donatodev.bcm_backend.support.TestTenant.clear();
    }

    private static List<Route> resourceRoutes() {
        List<Route> routes = new ArrayList<>();
        for (String resource : List.of("business-areas", "financial-types", "counterparties", "managers", "users",
                "budgets", "contract-templates", "financial-values", "contracts")) {
            boolean readByManager = !Set.of("managers", "users").contains(resource);
            routes.add(new Route("GET", "/" + resource + "/{id}", resource, readByManager));
            routes.add(new Route("PUT", "/" + resource + "/{id}", resource, resource.equals("financial-values")));
            routes.add(new Route("DELETE", "/" + resource + "/{id}", resource, false));
        }
        routes.add(new Route("GET", "/contract-history/{id}", "contract-history", true));
        routes.add(new Route("GET", "/counterparties/{id}/invoicing-summary", "counterparties", true));
        routes.add(new Route("DELETE", "/contract-history/{id}", "contract-history", false));
        routes.add(new Route("PATCH", "/users/{id}", "users", false));
        routes.add(new Route("POST", "/users/{id}/force-reset", "users", false));
        routes.add(new Route("PATCH", "/managers/{id}", "managers", false));
        routes.add(new Route("PATCH", "/counterparties/{id}/verified-iban", "counterparties", false));
        routes.add(new Route("POST", "/contract-templates/{id}/instantiate", "instantiate", false));
        return routes;
    }

    private static List<Route> contractRoutes() {
        return List.of(
            new Route("GET", "/contracts/{contract}", "", true),
            new Route("GET", "/financial-values/by-contract/{contract}", "", true),
            new Route("GET", "/contract-history/contract/{contract}", "", true),
            new Route("GET", "/contracts/{contract}/collaborators", "", true),
            new Route("PATCH", "/contracts/{contract}/collaborators", "collaborators", false),
            new Route("PATCH", "/contracts/{contract}/assign-manager", "assign", false),
            new Route("POST", "/contracts/{contract}/generate-financial-values", "", false),
            new Route("GET", "/contracts/{contract}/documents", "", true),
            new Route("UPLOAD", "/contracts/{contract}/documents", "", true),
            new Route("UPLOAD", "/contracts/{contract}/documents/{document}/versions", "", true),
            new Route("GET", "/contracts/{contract}/documents/{document}/versions", "", true),
            new Route("GET", "/contracts/{contract}/documents/{document}/diff/{document}", "", true),
            new Route("GET", "/contracts/{contract}/documents/{document}/download", "", true),
            new Route("GET", "/contracts/{contract}/documents/{document}/terms", "", true),
            new Route("POST", "/contracts/{contract}/documents/{document}/extract", "", true),
            new Route("POST", "/contracts/{contract}/documents/{document}/analyze-clause-risk", "", true),
            new Route("DELETE", "/contracts/{contract}/documents/{document}", "", false),
            new Route("GET", "/contracts/{contract}/invoices", "", true),
            new Route("UPLOAD", "/contracts/{contract}/invoices", "", true),
            new Route("GET", "/contracts/{contract}/invoices/{invoice}", "", true),
            new Route("GET", "/contracts/{contract}/invoices/{invoice}/download", "", true),
            new Route("DELETE", "/contracts/{contract}/invoices/{invoice}", "", false),
            new Route("PATCH", "/contracts/{contract}/invoices/{invoice}/payment-details", "payment", true),
            new Route("POST", "/contracts/{contract}/invoices/{invoice}/match/confirm", "match", false),
            new Route("POST", "/contracts/{contract}/invoices/{invoice}/match/reject", "", false),
            new Route("POST", "/contracts/{contract}/invoices/match/recompute", "", false),
            new Route("GET", "/contracts/{contract}/sepa-payments", "", true),
            new Route("POST", "/contracts/{contract}/sepa-payments", "sepa", true),
            new Route("GET", "/contracts/{contract}/sepa-payments/{batch}/download", "", true),
            new Route("POST", "/contracts/{contract}/workflow/submit", "", true),
            new Route("POST", "/contracts/{contract}/workflow/approve", "", true),
            new Route("POST", "/contracts/{contract}/workflow/reject", "reject", true),
            new Route("GET", "/contracts/{contract}/workflow/events", "", true),
            new Route("POST", "/risk-feedback/contracts/{contract}", "risk", true));
    }

    static Stream<Arguments> foreignCases() {
        return Stream.concat(resourceRoutes().stream(), contractRoutes().stream())
                .flatMap(route -> Stream.of(false, true).map(missing -> Arguments.of(route, missing)));
    }

    @ParameterizedTest(name = "{0}, missing={1}")
    @MethodSource("foreignCases")
    void foreignAndMissingResourcesAreHiddenWithoutSideEffects(Route route, boolean missing) throws Exception {
        Map<String, List<Map<String, Object>>> before = snapshot();
        mvc.perform(request(route, foreign, missing).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        flushAndClear();
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(storage, ml);
        assertThat(TenantContext.get()).isNull();
    }

    static Stream<Route> colleagueCases() {
        return contractRoutes().stream().filter(Route::managerAllowed);
    }

    @ParameterizedTest(name = "colleague: {0}")
    @MethodSource("colleagueCases")
    void plainManagerCannotAccessColleagueContract(Route route) throws Exception {
        Map<String, List<Map<String, Object>>> before = snapshot();
        mvc.perform(request(route, colleague, false).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
        flushAndClear();
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(storage, ml);
    }

    static Stream<Route> childCases() {
        return contractRoutes().stream().filter(r -> r.path().contains("{document}")
                || r.path().contains("{invoice}") || r.path().contains("{batch}"));
    }

    @ParameterizedTest(name = "child outside parent: {0}")
    @MethodSource("childCases")
    void childIdMustBelongToRequestedContract(Route route) throws Exception {
        Map<String, List<Map<String, Object>>> before = snapshot();
        String path = resolve(route.path(), foreign, false).replace("/contracts/" + foreign.contract().getId(),
                "/contracts/" + own.contract().getId());
        mvc.perform(request(route, path).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        flushAndClear();
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(storage, ml);
    }

    static Stream<Route> adminOnlyCases() {
        return Stream.concat(resourceRoutes().stream(), contractRoutes().stream()).filter(r -> !r.managerAllowed());
    }

    @ParameterizedTest(name = "role: {0}")
    @MethodSource("adminOnlyCases")
    void plainManagerCannotUseAdminOperations(Route route) throws Exception {
        Map<String, List<Map<String, Object>>> before = snapshot();
        mvc.perform(request(route, own, false).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
        flushAndClear();
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(storage, ml);
    }

    static Stream<Arguments> ownReadCases() {
        return resourceRoutes().stream().filter(r -> r.method().equals("GET"))
                .flatMap(r -> r.managerAllowed() ? Stream.of(Arguments.of(r, false), Arguments.of(r, true))
                        : Stream.of(Arguments.of(r, false)));
    }

    @ParameterizedTest(name = "own resource {0}, manager={1}")
    @MethodSource("ownReadCases")
    void authorizedResourceRemainsReadable(Route route, boolean manager) throws Exception {
        mvc.perform(request(route, own, false).header("Authorization", "Bearer " + (manager ? managerToken : adminToken)))
                .andExpect(status().isOk());
    }

    static Stream<Arguments> listCases() {
        return Stream.of("/business-areas", "/financial-types", "/counterparties", "/budgets", "/contract-templates",
                        "/financial-values", "/contracts/search", "/contracts/filter?status=ACTIVE", "/contracts/export/excel", "/contracts/export/pdf")
                .flatMap(path -> Stream.of(Arguments.of(path, false), Arguments.of(path, true)));
    }

    @ParameterizedTest(name = "list/export {0}, manager={1}")
    @MethodSource("listCases")
    void listsSearchesAndExportsStayInScope(String path, boolean manager) throws Exception {
        byte[] bytes = mvc.perform(get(path).header("Authorization", "Bearer " + (manager ? managerToken : adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String text;
        if (path.endsWith("excel")) {
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
                StringBuilder contents = new StringBuilder();
                for (var sheet : workbook) for (var row : sheet) for (var cell : row) contents.append(cell).append(' ');
                text = contents.toString();
            }
        } else if (path.endsWith("pdf")) {
            try (var document = org.apache.pdfbox.Loader.loadPDF(bytes)) {
                text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            }
        } else {
            text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        text = text.replaceAll("\\s+", "");
        assertThat(text).doesNotContain(foreign.contract().getContractNumber());
        if (manager && (path.startsWith("/contracts/") || path.equals("/financial-values"))) {
            assertThat(text).doesNotContain(colleague.contract().getContractNumber());
        }
        assertThat(text).contains(own.contract().getContractNumber());
    }

    static Stream<Route> protectedCases() {
        return Stream.concat(resourceRoutes().stream(), contractRoutes().stream());
    }

    @ParameterizedTest(name = "unauthenticated: {0}")
    @MethodSource("protectedCases")
    void noJwtMeansNoResourceAccess(Route route) throws Exception {
        Map<String, List<Map<String, Object>>> before = snapshot();
        mvc.perform(request(route, own, false)).andExpect(status().isUnauthorized());
        flushAndClear();
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(storage, ml);
    }

    static Stream<Arguments> bodyCases() {
        return Stream.of("POST", "PUT").flatMap(method -> Stream.of("contractId", "financialTypeId", "businessAreaId")
                .map(field -> Arguments.of(method, field)));
    }

    @ParameterizedTest(name = "financial value {0}: foreign {1}")
    @MethodSource("bodyCases")
    void relatedIdsInFinancialValueBodyMustStayInTenant(String method, String field) throws Exception {
        Map<String, List<Map<String, Object>>> before = snapshot();
        String body = financialBody(field.equals("contractId") ? foreign : own,
                field.equals("financialTypeId") ? foreign : own, field.equals("businessAreaId") ? foreign : own);
        String path = "/financial-values" + (method.equals("PUT") ? "/" + own.value().getId() : "");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        flushAndClear();
        assertThat(snapshot()).isEqualTo(before);
    }

    @org.junit.jupiter.api.Test
    void managerCannotMoveOwnFinancialValueToColleagueContract() throws Exception {
        Map<String, List<Map<String, Object>>> before = snapshot();
        mvc.perform(put("/financial-values/" + own.value().getId()).header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON).content(financialBody(colleague, own, own)))
                .andExpect(status().isForbidden());
        flushAndClear();
        assertThat(snapshot()).isEqualTo(before);
    }

    private String financialBody(Data contract, Data type, Data area) {
        return "{\"month\":1,\"year\":2026,\"financialAmount\":100,\"contractId\":" + q(contract.contract().getId())
                + ",\"financialTypeId\":" + q(type.type().getId()) + ",\"businessAreaId\":" + q(area.area().getId()) + "}";
    }

    @org.junit.jupiter.api.Test
    void approverMayReadColleagueWorkflowWithinSameTenant() throws Exception {
        Users approver = em.find(Users.class, own.user().getId());
        approver.setCanApproveContracts(true);
        flushAndClear();
        mvc.perform(get("/contracts/" + colleague.contract().getId() + "/workflow/events")
                .header("Authorization", "Bearer " + managerToken)).andExpect(status().isOk());
        mvc.perform(get("/contracts/" + foreign.contract().getId() + "/workflow/events")
                .header("Authorization", "Bearer " + managerToken)).andExpect(status().isNotFound());
    }

    @org.junit.jupiter.api.Test
    void globalRolesDoNotBecomeTenantOwnedResources() throws Exception {
        mvc.perform(get("/roles/" + own.user().getRole().getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @org.junit.jupiter.api.Test
    void calendarUsesItsOwnTokenInsteadOfTenantJwt() throws Exception {
        Users owner = em.find(Users.class, own.user().getId());
        String token = UUID.randomUUID().toString();
        owner.setCalendarToken(token);
        flushAndClear();
        String feed = mvc.perform(get("/calendar/" + token + ".ics")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().replaceAll("\\s+", "");
        assertThat(feed).contains(own.contract().getContractNumber()).doesNotContain(
                colleague.contract().getContractNumber(), foreign.contract().getContractNumber());
        mvc.perform(get("/calendar/invalid-token.ics")).andExpect(status().isNotFound());
    }

    @org.junit.jupiter.api.Test
    void ordinaryJwtCannotReplaceInternalApiKey() throws Exception {
        Map<String, List<Map<String, Object>>> before = snapshot();
        int code = mvc.perform(put("/internal/organizations/" + foreign.org().getId() + "/tier")
                .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tier\":\"PRO\"}")).andReturn().getResponse().getStatus();
        assertThat(code).isIn(403,404);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest(name = "empty history: {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"own", "colleague", "foreign", "unassigned", "noManagerProfile"})
    void historyAuthorizationDoesNotDependOnExistingRows(String scenario) throws Exception {
        Data source = scenario.equals("foreign") ? foreign : scenario.equals("colleague") ? colleague : own;
        Contracts empty = persist(Contracts.builder().organization(source.org()).businessArea(source.area())
                .counterparty(source.counterparty()).manager(scenario.equals("unassigned") ? null : source.manager())
                .contractNumber(UUID.randomUUID().toString()).startDate(LocalDate.of(2026, Month.JANUARY,1)).status(ContractStatus.ACTIVE).build());
        if (scenario.equals("noManagerProfile")) em.find(Users.class, own.user().getId()).setManager(null);
        flushAndClear();
        int expected = scenario.equals("own") ? 200 : scenario.equals("foreign") ? 404 : 403;
        mvc.perform(get("/contract-history/contract/" + empty.getId()).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().is(expected));
        if (scenario.equals("unassigned") || scenario.equals("noManagerProfile")) {
            ContractHistory history = persist(ContractHistory.builder().contract(em.find(Contracts.class, empty.getId()))
                    .modificationDate(LocalDateTime.of(2026, Month.JANUARY,1,0,0)).previousStatus(ContractStatus.DRAFT).newStatus(ContractStatus.ACTIVE).build());
            flushAndClear();
            mvc.perform(get("/contract-history/" + history.getId()).header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden());
        }
    }

    @org.junit.jupiter.api.Test
    void signedJwtWithoutOrganizationCannotAccessTenantResources() throws Exception {
        Users tokenUser = Users.builder().id(admin.getId()).username(admin.getUsername()).role(admin.getRole()).build();
        String token = jwt.generateTokenFromUser(tokenUser);
        mvc.perform(get("/contracts/" + own.contract().getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(storage, ml);
    }

    @org.junit.jupiter.api.Test
    void jwtCannotMarkAnotherUsersNotificationRead() throws Exception {
        Notification notification = persist(Notification.builder().orgId(foreign.org().getId()).user(foreign.user())
                .title("Foreign notification").message("Private message").build());
        flushAndClear();
        Map<String, List<Map<String, Object>>> before = snapshot();
        mvc.perform(patch("/notifications/" + notification.getId() + "/read")
                .header("Authorization", "Bearer " + managerToken)).andExpect(status().isNotFound());
        flushAndClear();
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest(name = "create must not overwrite foreign id: {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"business-areas", "financial-types", "counterparties",
            "managers", "users", "budgets", "contract-templates", "financial-values", "contracts", "contract-history"})
    void creationCannotOverwriteExistingResourceThroughBodyId(String resource) throws Exception {
        UUID victimId = UUID.fromString(resolve("/" + resource + "/{id}", foreign, false).split("/")[2]);
        String table = switch (resource) {
            case "business-areas" -> "business_areas"; case "financial-types" -> "financial_types";
            case "contract-templates" -> "contract_templates"; case "financial-values" -> "financial_values";
            case "contract-history" -> "contract_history"; default -> resource;
        };
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM " + table + " WHERE id = ?", victimId);
        String body = switch (resource) {
            case "business-areas" -> "{\"name\":\"Created area\"}";
            case "financial-types" -> "{\"name\":\"Created type\",\"category\":\"REVENUE\"}";
            case "managers" -> "{\"firstName\":\"Created\",\"lastName\":\"Manager\",\"email\":\"" + UUID.randomUUID() + "@example.com\"}";
            case "users" -> {
                Managers fresh = persist(Managers.builder().organization(own.org()).firstName("New").lastName("Manager")
                        .email(UUID.randomUUID() + "@example.com").build());
                yield "{\"username\":\"created-user\",\"password\":\"ValidPassword123!\",\"managerId\":"
                        + q(fresh.getId()) + ",\"roleId\":" + q(admin.getRole().getId()) + "}";
            }
            case "budgets" -> "{\"businessAreaId\":" + q(own.area().getId()) + ",\"year\":2027,\"category\":\"REVENUE\",\"targetAmount\":100}";
            case "financial-values" -> financialBody(own, own, own).replace("\"month\":1", "\"month\":2");
            case "contract-history" -> "{\"contractId\":" + q(own.contract().getId()) + ",\"modifiedById\":"
                    + q(own.user().getId()) + ",\"modificationDate\":\"2026-01-01T00:00:00\",\"previousStatus\":\"DRAFT\",\"newStatus\":\"ACTIVE\"}";
            default -> payload(resource);
        };
        body = "{\"id\":" + q(victimId) + "," + body.substring(1);
        var response = mvc.perform(post("/" + resource).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
        if (response.getStatus() == 201) {
            assertThat(json.readTree(response.getContentAsString()).get("id").asText()).isNotEqualTo(victimId.toString());
        }
        flushAndClear();
        assertThat(jdbc.queryForMap("SELECT * FROM " + table + " WHERE id = ?", victimId)).isEqualTo(before);
    }

    // ── Same-tenant child/parent mismatch ──────────────────────────────────
    // A foreign child under an own contract is refused by the tenant check
    // alone; only a child of ANOTHER contract of the SAME organization proves
    // that the child is looked up through its parent.

    @ParameterizedTest(name = "colleague's child under own contract: {0}")
    @MethodSource("childCases")
    void childOfAnotherContractInSameTenantIsHidden(Route route) throws Exception {
        Map<String, List<Map<String, Object>>> before = snapshot();
        String path = resolve(route.path(), colleague, false).replaceFirst(
                "/contracts/" + colleague.contract().getId() + "(?=/|$)", "/contracts/" + own.contract().getId());
        mvc.perform(request(route, path).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        flushAndClear();
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(storage, ml);
    }

    // ── Positive cases on contract-scoped reads ────────────────────────────
    // Without them an endpoint answering 404 to everybody would pass every
    // refusal test above.

    static Stream<Arguments> ownContractReadCases() {
        return contractRoutes().stream().filter(r -> r.method().equals("GET") && r.managerAllowed())
                .flatMap(r -> Stream.of(Arguments.of(r, false), Arguments.of(r, true)));
    }

    @ParameterizedTest(name = "own contract {0}, manager={1}")
    @MethodSource("ownContractReadCases")
    void ownContractReadsSucceed(Route route, boolean manager) throws Exception {
        when(storage.readDocument(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(request(route, own, false).header("Authorization", "Bearer " + (manager ? managerToken : adminToken)))
                .andExpect(status().is2xxSuccessful());
    }

    // ── A plain manager and a colleague's own records ──────────────────────

    static Stream<Arguments> colleagueRecordCases() {
        return Stream.of("GET /financial-values", "PUT /financial-values", "GET /contract-history").map(Arguments::of);
    }

    @ParameterizedTest(name = "colleague record: {0}")
    @MethodSource("colleagueRecordCases")
    void plainManagerCannotReadOrChangeColleagueRecords(String operation) throws Exception {
        Map<String, List<Map<String, Object>>> before = snapshot();
        var builder = switch (operation) {
            case "GET /financial-values" -> get("/financial-values/" + colleague.value().getId());
            case "PUT /financial-values" -> put("/financial-values/" + colleague.value().getId())
                    .contentType(MediaType.APPLICATION_JSON).content(financialBody(colleague, colleague, colleague));
            default -> get("/contract-history/" + colleague.history().getId());
        };
        mvc.perform(builder.header("Authorization", "Bearer " + managerToken)).andExpect(status().isForbidden());
        flushAndClear();
        assertThat(snapshot()).isEqualTo(before);
    }

    // ── Aggregates must not move when another organization's data changes ─
    // A total summed across organizations contains no foreign contract number,
    // so "does not contain" checks cannot catch it: compare before and after.

    static Stream<Arguments> aggregateCases() {
        return Stream.of("/contracts/stats", "/contracts/stats/invoicing-summary", "/contracts/stats/by-area",
                        "/contracts/stats/timeline", "/contracts/stats/top-managers", "/contracts/expiring?days=3650",
                        "/home/attention", "/financial-values")
                .flatMap(path -> Stream.of(Arguments.of(path, false), Arguments.of(path, true)));
    }

    @ParameterizedTest(name = "aggregate {0}, manager={1}")
    @MethodSource("aggregateCases")
    void aggregatesIgnoreOtherOrganizations(String path, boolean manager) throws Exception {
        String token = "Bearer " + (manager ? managerToken : adminToken);
        String first = mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        addForeignActivity();
        String second = mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
    }

    /** More contracts, values and invoices in the foreign organization, some expiring soon. */
    private void addForeignActivity() {
        Organization other = em.find(Organization.class, foreign.org().getId());
        Data more = data(other);
        Contracts soon = persist(Contracts.builder().organization(other).manager(more.manager()).businessArea(more.area())
                .counterparty(more.counterparty()).contractNumber(UUID.randomUUID().toString())
                .startDate(LocalDate.now().minusMonths(6)).endDate(LocalDate.now().plusDays(10))
                .status(ContractStatus.ACTIVE).workflowStage(WorkflowStage.DRAFT).build());
        persist(FinancialValues.builder().organization(other).contract(soon).businessArea(more.area())
                .financialType(more.type()).month(LocalDate.now().getMonthValue()).year(LocalDate.now().getYear())
                .financialAmount(999_999).build());
        persist(ElectronicInvoice.builder().contract(soon).orgId(other.getId()).storagePath(UUID.randomUUID() + ".xml")
                .fileName("noise.xml").fileSize(1L).contentType("application/xml")
                .totalAmount(BigDecimal.valueOf(999_999)).build());
        flushAndClear();
    }

    // ── Lists of people and activity hold no foreign rows ──────────────────

    static Stream<Arguments> peopleAndActivityLists() {
        return Stream.of(
                Arguments.of("/contract-history", false), Arguments.of("/managers", false), Arguments.of("/managers", true),
                Arguments.of("/managers/search?q=Test", false), Arguments.of("/users", false),
                Arguments.of("/users/search?q=", false), Arguments.of("/audit-logs", false),
                Arguments.of("/notifications", false), Arguments.of("/notifications", true),
                Arguments.of("/risk-feedback", false), Arguments.of("/risk-feedback", true));
    }

    @ParameterizedTest(name = "list {0}, manager={1}")
    @MethodSource("peopleAndActivityLists")
    void peopleAndActivityListsStayInScope(String path, boolean manager) throws Exception {
        String marker = foreign.contract().getContractNumber();
        Users foreignUser = em.find(Users.class, foreign.user().getId());
        Contracts foreignContract = em.find(Contracts.class, foreign.contract().getId());
        persist(Notification.builder().orgId(foreign.org().getId()).user(foreignUser).title(marker).message(marker).build());
        persist(RiskFeedback.builder().contract(foreignContract).submittedBy(foreignUser).organizationId(foreign.org().getId())
                .riskScore(0.9).riskLevel(marker.substring(0, 8)).agree(true).build());
        persist(AuditLog.builder().action("UPDATE").entityType("Contracts").entityId(foreignContract.getId())
                .username(foreignUser.getUsername()).orgId(foreign.org().getId()).details(marker).build());
        flushAndClear();

        String text = mvc.perform(get(path).header("Authorization", "Bearer " + (manager ? managerToken : adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(text).doesNotContain(marker, marker.substring(0, 8), foreignUser.getUsername());
    }

    // ── References to another organization in write bodies ─────────────────

    static Stream<Arguments> foreignReferenceWrites() {
        return Stream.of("create contract with foreign counterparty", "update contract with foreign counterparty",
                "assign foreign manager", "create user with foreign manager",
                "instantiate template with foreign counterparty").map(Arguments::of);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foreignReferenceWrites")
    void writesCannotReferenceAnotherOrganization(String scenario) throws Exception {
        Map<String, List<Map<String, Object>>> before = snapshot();
        String foreignCounterparty = payload("contracts").replace(
                "\"counterpartyId\":" + q(own.counterparty().getId()), "\"counterpartyId\":" + q(foreign.counterparty().getId()));
        var builder = switch (scenario) {
            case "create contract with foreign counterparty" -> post("/contracts").content(foreignCounterparty);
            case "update contract with foreign counterparty" -> put("/contracts/" + own.contract().getId()).content(foreignCounterparty);
            case "assign foreign manager" -> patch("/contracts/" + own.contract().getId() + "/assign-manager")
                    .content("{\"managerId\":" + q(foreign.manager().getId()) + "}");
            case "create user with foreign manager" -> post("/users").content("{\"username\":\"" + UUID.randomUUID()
                    + "\",\"password\":\"ValidPassword123!\",\"managerId\":" + q(foreign.manager().getId())
                    + ",\"roleId\":" + q(admin.getRole().getId()) + "}");
            default -> post("/contract-templates/" + own.template().getId() + "/instantiate").content(foreignCounterparty);
        };
        int code = mvc.perform(builder.contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getStatus();
        assertThat(code).isIn(400, 404);
        flushAndClear();
        assertThat(snapshot()).isEqualTo(before);
    }

    private AbstractMockHttpServletRequestBuilder<?> request(Route route, Data target, boolean missing) {
        return request(route, resolve(route.path(), target, missing));
    }

    private AbstractMockHttpServletRequestBuilder<?> request(Route route, String path) {
        if (route.method().equals("UPLOAD")) {
            return multipart(path).file(new MockMultipartFile("file", "test.pdf", "application/pdf",
                    "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.valueOf(route.method()), path)
                .contentType(MediaType.APPLICATION_JSON).content(payload(route.payload()));
    }

    private String resolve(String path, Data d, boolean missing) {
        Map<String, UUID> ids = Map.of("contract", d.contract().getId(), "document", d.document().getId(),
                "invoice", d.invoice().getId(), "batch", d.batch().getId());
        for (var entry : ids.entrySet()) path = path.replace("{" + entry.getKey() + "}", "" + (missing ? uid(Long.MAX_VALUE) : entry.getValue()));
        if (path.contains("{id}")) {
            String resource = path.split("/")[1];
            UUID id = switch (resource) {
                case "business-areas" -> d.area().getId(); case "financial-types" -> d.type().getId();
                case "counterparties" -> d.counterparty().getId(); case "managers" -> d.manager().getId();
                case "users" -> d.user().getId(); case "budgets" -> d.budget().getId();
                case "contract-templates" -> d.template().getId(); case "financial-values" -> d.value().getId();
                case "contract-history" -> d.history().getId(); case "contracts" -> d.contract().getId();
                default -> throw new IllegalArgumentException(resource);
            };
            path = path.replace("{id}", "" + (missing ? uid(Long.MAX_VALUE) : id));
        }
        return path;
    }

    private String payload(String kind) {
        return switch (kind) {
            case "contracts", "instantiate" -> "{\"counterpartyId\":" + q(own.counterparty().getId())
                    + ",\"contractNumber\":\"NEW-CONTRACT\",\"status\":\"ACTIVE\",\"startDate\":\"2026-01-01\",\"endDate\":\"2027-01-01\",\"areaId\":" + q(own.area().getId()) + "}";
            case "counterparties" -> "{\"name\":\"Updated\",\"type\":\"CUSTOMER\",\"iban\":\"IT60X0542811101000000123456\"}";
            case "users" -> "{\"username\":\"updated-user\"}";
            case "contract-templates" -> "{\"name\":\"Updated\"}";
            case "assign" -> "{\"managerId\":" + q(own.manager().getId()) + "}";
            case "collaborators" -> "{\"managerIds\":[" + q(own.manager().getId()) + "]}";
            case "sepa" -> "{\"invoiceIds\":[" + q(own.invoice().getId()) + "],\"executionDate\":\"2027-01-01\"}";
            case "reject" -> "{\"comment\":\"Rejected for testing\"}";
            case "risk" -> "{\"riskScore\":0.5,\"level\":\"LOW\",\"agree\":true}";
            case "payment" -> "{\"supplierIban\":null,\"supplierBic\":null,\"paymentDueDate\":null}";
            default -> "{}";
        };
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : SNAPSHOT_TABLES) result.put(table, jdbc.queryForList("SELECT * FROM " + table));
        return result;
    }

    private void flushAndClear() { em.flush(); em.clear(); }
    private <T> T persist(T entity) { em.persist(entity); return entity; }

    private Users account(Organization org, Managers manager, String role) {
        List<Roles> found = em.createQuery("select r from Roles r where r.role = :role", Roles.class).setParameter("role", role).getResultList();
        Roles r = found.isEmpty() ? persist(Roles.builder().role(role).build()) : found.getFirst();
        return persist(Users.builder().organization(org).manager(manager).username(UUID.randomUUID().toString())
                .passwordHash("test-password").verified(true).role(r).build());
    }

    private Data data(Organization org) {
        String name = UUID.randomUUID().toString();
        BusinessAreas area = persist(BusinessAreas.builder().organization(org).name(name).build());
        Managers manager = persist(Managers.builder().organization(org).firstName("Test").lastName("Manager").email(name + "@example.com").build());
        Users user = account(org, manager, "MANAGER");
        Counterparty cp = persist(Counterparty.builder().organization(org).name(name).type(CounterpartyType.CUSTOMER).build());
        FinancialTypes type = persist(FinancialTypes.builder().organization(org).name(name).category(FinancialCategory.REVENUE).build());
        Contracts contract = persist(Contracts.builder().organization(org).manager(manager).businessArea(area).counterparty(cp)
                .contractNumber(name).startDate(LocalDate.of(2026, Month.JANUARY,1)).endDate(LocalDate.of(2027, Month.JANUARY,1))
                .status(ContractStatus.ACTIVE).workflowStage(WorkflowStage.DRAFT).build());
        FinancialValues value = persist(FinancialValues.builder().organization(org).contract(contract).businessArea(area).financialType(type)
                .month(1).year(2026).financialAmount(100).build());
        ContractHistory history = persist(ContractHistory.builder().contract(contract).modifiedBy(user)
                .modificationDate(LocalDateTime.of(2026, Month.JANUARY,1,0,0)).previousStatus(ContractStatus.DRAFT).newStatus(ContractStatus.ACTIVE).build());
        ContractTemplate template = persist(ContractTemplate.builder().orgId(org.getId()).name(name).businessArea(area).defaultManager(manager).build());
        Budget budget = persist(Budget.builder().organization(org).businessArea(area).category(FinancialCategory.REVENUE).year(2026).targetAmount(100).build());
        ContractDocument document = persist(ContractDocument.builder().contract(contract).orgId(org.getId()).storagePath(name + ".pdf")
                .fileName("test.pdf").fileSize(1L).contentType("application/pdf").versionNumber(1).extractedText("Test document").build());
        document.setVersionGroupId(document.getId());
        ElectronicInvoice invoice = persist(ElectronicInvoice.builder().contract(contract).orgId(org.getId()).storagePath(name + ".xml")
                .fileName("test.xml").fileSize(1L).contentType("application/xml").totalAmount(BigDecimal.valueOf(100)).build());
        SepaPaymentBatch batch = persist(SepaPaymentBatch.builder().contract(contract).orgId(org.getId()).messageId(name.substring(0,30))
                .executionDate(LocalDate.of(2027, Month.JANUARY,1)).totalAmount(BigDecimal.valueOf(100)).currency("EUR").numberOfTransactions(1)
                .storagePath(name + "-sepa.xml").fileName("sepa.xml").build());
        return new Data(org,area,manager,user,cp,type,contract,value,history,template,budget,document,invoice,batch);
    }

    /** A value as JSON: ids are UUIDs now, so they go in quotes (ADR-0009). */
    private static String q(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
