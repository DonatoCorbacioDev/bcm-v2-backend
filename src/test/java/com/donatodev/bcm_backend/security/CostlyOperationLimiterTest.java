package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;
import com.donatodev.bcm_backend.service.MlProxyService;
import com.donatodev.bcm_backend.support.TestTenant;

import jakarta.persistence.EntityManager;

/**
 * OWASP API4: each user has a small budget per minute for the endpoints that
 * cost the most, so one account (the public demo's included) can't use up
 * the CPU every customer shares. Low limits here; the test profile otherwise
 * keeps them out of the way.
 */
@SpringBootTest(properties = {"rate-limit.llm-per-minute=2", "rate-limit.heavy-per-minute=3"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CostlyOperationLimiterTest {

    @Autowired private MockMvc mvc;
    @Autowired private EntityManager em;
    @Autowired private JwtUtils jwt;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;
    @MockitoBean private MlProxyService ml;

    private String anna;
    private String marco;

    @BeforeEach
    void seed() {
        TenantContext.clear();
        TestTenant.clear();
        Organization org = Organization.builder().name("API4").slug(UUID.randomUUID().toString())
                .subscriptionTier(SubscriptionTier.PRO).build();
        em.persist(org);
        anna = jwt.generateTokenFromUser(manager(org, "anna"));
        marco = jwt.generateTokenFromUser(manager(org, "marco"));
        em.flush();
        when(ml.askAgent(anyString())).thenReturn(ResponseEntity.ok("{\"answer\":\"ok\"}"));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        TestTenant.clear();
    }

    private Users manager(Organization org, String name) {
        Managers profile = Managers.builder().organization(org).firstName(name).lastName("Test")
                .email(name + UUID.randomUUID() + "@example.com").build();
        em.persist(profile);
        Roles role = em.createQuery("select r from Roles r where r.role = 'MANAGER'", Roles.class)
                .getResultList().stream().findFirst().orElseGet(() -> {
                    Roles created = Roles.builder().role("MANAGER").build();
                    em.persist(created);
                    return created;
                });
        Users user = Users.builder().organization(org).manager(profile).role(role).verified(true)
                .username(name + UUID.randomUUID().toString().substring(0, 8)).passwordHash("x").build();
        em.persist(user);
        return user;
    }

    private org.springframework.test.web.servlet.ResultActions ask(String token) throws Exception {
        return mvc.perform(post("/agent/ask").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Quali contratti scadono?\"}"));
    }

    @Test
    @DisplayName("the assistant answers a user twice a minute, then 429 with a message to wait")
    void llmBudgetPerUser() throws Exception {
        ask(anna).andExpect(status().isOk());
        ask(anna).andExpect(status().isOk());
        ask(anna).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(CostlyOperationLimiter.MESSAGE));
    }

    @Test
    @DisplayName("one user's spent budget does not touch a colleague's")
    void budgetsAreSeparatePerUser() throws Exception {
        ask(anna).andExpect(status().isOk());
        ask(anna).andExpect(status().isOk());
        ask(anna).andExpect(status().isTooManyRequests());

        ask(marco).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a contract search page holds at most 1000 rows: a bigger one is refused, not loaded")
    void searchPageIsBounded() throws Exception {
        mvc.perform(get("/contracts/search").param("size", "1000000").header("Authorization", "Bearer " + anna))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/contracts/search").param("size", "1000").header("Authorization", "Bearer " + anna))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("every endpoint that runs the model, embeds, exports, imports or parses a file is budgeted")
    void theCostlyEndpointsCarryTheAnnotation() {
        Map<String, CostlyOperation.Cost> expected = Map.ofEntries(
                Map.entry("MlProxyController.askAgent", CostlyOperation.Cost.LLM),
                Map.entry("MlProxyController.getAgentInsights", CostlyOperation.Cost.LLM),
                Map.entry("ContractDocumentController.analyzeClauseRisk", CostlyOperation.Cost.LLM),
                Map.entry("ContractDocumentController.extractText", CostlyOperation.Cost.HEAVY),
                Map.entry("ContractDocumentController.uploadDocument", CostlyOperation.Cost.HEAVY),
                Map.entry("ContractDocumentController.uploadNewVersion", CostlyOperation.Cost.HEAVY),
                Map.entry("SemanticSearchController.semanticSearch", CostlyOperation.Cost.HEAVY),
                Map.entry("ContractController.exportContractsToExcel", CostlyOperation.Cost.HEAVY),
                Map.entry("ContractController.exportContractsToPDF", CostlyOperation.Cost.HEAVY),
                Map.entry("ContractController.importContractsFromExcel", CostlyOperation.Cost.HEAVY),
                Map.entry("ElectronicInvoiceController.uploadInvoice", CostlyOperation.Cost.HEAVY),
                Map.entry("ElectronicInvoiceController.recomputeMatches", CostlyOperation.Cost.HEAVY));

        Map<RequestMappingInfo, HandlerMethod> handlers = mappings.getHandlerMethods();
        Map<String, CostlyOperation.Cost> annotated = handlers.values().stream()
                .filter(h -> h.hasMethodAnnotation(CostlyOperation.class))
                .collect(Collectors.toMap(
                        h -> h.getBeanType().getSimpleName() + "." + h.getMethod().getName(),
                        h -> h.getMethodAnnotation(CostlyOperation.class).value(),
                        (a, b) -> a));

        Set<String> missing = new TreeSet<>(expected.keySet());
        missing.removeAll(annotated.keySet());
        assertThat(missing).as("costly endpoints without a budget").isEmpty();
        assertThat(annotated).containsAllEntriesOf(expected);
    }
}
