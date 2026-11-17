package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.donatodev.bcm_backend.entity.Organization;

/**
 * OWASP Multi-Tenant Security Cheat Sheet, denied by default like the roles in
 * {@code FunctionLevelAuthorizationTest}: every query on a table that belongs
 * to an organization filters by organization. A repository method that does
 * not, or a service reading such a table by id alone, fails here unless it is
 * listed below with the reason it is safe.
 */
class TenantScopeGuardTest {

    private static final String REPOSITORIES = "com.donatodev.bcm_backend.repository";
    private static final Path MAIN = Path.of("src/main/java/com/donatodev/bcm_backend");

    private static final String VIA_MANAGER =
            "a manager belongs to one organization, and the id comes from the signed-in, scoped user";
    private static final String VIA_CONTRACT =
            "the contract id comes from a contract already found in the organization (ContractAccessGuard)";
    private static final String NIGHTLY_JOB =
            "nightly job over every organization: each contract is then handled within its own";
    private static final String VIA_COUNTERPARTY =
            "the counterparty id comes from a counterparty already found in the organization";
    private static final String USERNAME =
            "usernames are unique across organizations (V51): sign-in and the signed-in user's own lookup";

    /** Repository methods that do not filter by organization, and why that is safe. */
    private static final Map<String, String> UNSCOPED_METHODS = Map.ofEntries(
            Map.entry("AuditLogRepository.deleteByTimestampBefore", "retention job: every organization, by design"),
            Map.entry("AuditLogRepository.findByActionInAndTimestampAfter",
                    "the operator's weekly abuse report: every organization, sent only to the operator"),
            Map.entry("ContractsRepository.findByManagerId", VIA_MANAGER),
            Map.entry("ContractsRepository.findByManagerIdAndStatus", VIA_MANAGER),
            Map.entry("ContractsRepository.findByManagerIdAndContractNumberContainingIgnoreCaseOrManagerIdAndCounterpartyNameContainingIgnoreCase",
                    VIA_MANAGER),
            Map.entry("ContractsRepository.findByManagerIdAndStatusAndContractNumberContainingIgnoreCaseOrManagerIdAndStatusAndCounterpartyNameContainingIgnoreCase",
                    VIA_MANAGER),
            Map.entry("ContractsRepository.countActiveContractsByManager", VIA_MANAGER),
            Map.entry("ContractsRepository.countAllContractsByManager", VIA_MANAGER),
            Map.entry("ContractsRepository.countContractsByAreaAndManager", VIA_MANAGER),
            Map.entry("ContractsRepository.countContractsByMonthAndManager", VIA_MANAGER),
            Map.entry("ContractsRepository.countDraftContractsByManager", VIA_MANAGER),
            Map.entry("ContractsRepository.countExpiredContractsByManager", VIA_MANAGER),
            Map.entry("ContractsRepository.countExpiringContractsByManager", VIA_MANAGER),
            Map.entry("ContractsRepository.findExpiringContractsByManager", VIA_MANAGER),
            Map.entry("ContractsRepository.findTopManagerForManager", VIA_MANAGER),
            Map.entry("ContractsRepository.countActiveContractsByCounterpartyId", VIA_COUNTERPARTY),
            Map.entry("ContractsRepository.sumActiveAnnualValueByCounterpartyId", VIA_COUNTERPARTY),
            Map.entry("FinancialValuesRepository.sumCostByCounterpartyUpToMonth", VIA_COUNTERPARTY),
            Map.entry("FinancialValuesRepository.sumCostByManagerUpToMonth", VIA_MANAGER),
            Map.entry("ContractsRepository.findByStatusAndAutoRenewTrueAndNoticeDaysIsNotNull", NIGHTLY_JOB),
            Map.entry("ContractsRepository.findByStatusAndEndDate", NIGHTLY_JOB),
            Map.entry("ContractsRepository.findByStatusAndEndDateBefore", NIGHTLY_JOB),
            Map.entry("FinancialValuesRepository.findByContractId", VIA_CONTRACT),
            Map.entry("FinancialValuesRepository.findByContract_IdAndFinancialType_Id", VIA_CONTRACT),
            Map.entry("FinancialValuesRepository.findByContract_Manager_Id", VIA_MANAGER),
            Map.entry("ManagersRepository.existsByEmail",
                    "sign-up refuses an address already in use anywhere: it answers only yes or no"),
            Map.entry("RiskFeedbackRepository.findByContractManagerIdOrderByCreatedAtDescIdDesc", VIA_MANAGER),
            Map.entry("SepaPaymentBatchRepository.findByContractIdOrderByCreatedAtDesc", VIA_CONTRACT),
            Map.entry("SepaPaymentBatchRepository.findByIdAndContractId", VIA_CONTRACT),
            Map.entry("SepaPaymentBatchRepository.findByCreatedAtBefore", "retention job: every organization, by design"),
            Map.entry("UsersRepository.findByUsername", USERNAME),
            Map.entry("UsersRepository.findAllByUsername", USERNAME),
            Map.entry("UsersRepository.existsByUsername", USERNAME),
            Map.entry("UsersRepository.findByManagerId",
                    "one account per manager (unique) and a manager in one organization; callers also check it"),
            Map.entry("UsersRepository.existsByManagerId", "one account per manager: answers only yes or no"),
            Map.entry("UsersRepository.findByTotpSecretEncryptedIsNotNull",
                    "startup re-encryption of two-factor secrets (TotpKeyMigration): every organization, no request"),
            Map.entry("UsersRepository.findAllByManagerEmailIgnoreCase",
                    "forgot password: each account at that address gets its own link, sent only to that address"),
            Map.entry("UsersRepository.findByCalendarToken",
                    "the secret feed token is the credential; the feed shows only its owner's organization"));

    /** Services reading an organization's table by id alone (JpaRepository's own methods), and why. */
    private static final String TEST_DATA = "TestDataCleaner wipes the test database between tests";

    private static final Map<String, String> UNSCOPED_CALLS = Map.ofEntries(
            Map.entry("NotificationService: usersRepository.findById",
                    "then refused unless the user is in the notification's organization"),
            Map.entry("UserService: managersRepository.findById",
                    "the manager id comes from an invite token issued inside the organization"),
            Map.entry("UserMapper: managersRepository.findById",
                    "its only caller, UserService.createUser, refuses a manager of another organization"),
            Map.entry("ContractFinancialGenerationService: financialValuesRepository.deleteAll",
                    "deletes rows it just read for a contract already found in the organization"),
            Map.entry("TestDataCleaner: budgetRepository.deleteAll", TEST_DATA),
            Map.entry("TestDataCleaner: contractTemplateRepository.deleteAll", TEST_DATA),
            Map.entry("TestDataCleaner: contractsRepository.deleteAll", TEST_DATA),
            Map.entry("TestDataCleaner: financialValuesRepository.deleteAll", TEST_DATA),
            Map.entry("TestDataCleaner: managersRepository.deleteAll", TEST_DATA),
            Map.entry("TestDataCleaner: notificationRepository.deleteAll", TEST_DATA),
            Map.entry("TestDataCleaner: riskFeedbackRepository.deleteAll", TEST_DATA),
            Map.entry("TestDataCleaner: sepaPaymentBatchRepository.deleteAll", TEST_DATA),
            Map.entry("TestDataCleaner: usersRepository.deleteAll", TEST_DATA));

    /** JpaRepository's own methods: none of them knows about organizations. */
    private static final Pattern INHERITED_CALL = Pattern.compile(
            "\\b(\\w+Repository)\\.(findById|findAllById|findAll|existsById|getReferenceById|count|deleteById|deleteAll)\\(");

    @Test
    @DisplayName("every repository method on an organization's table filters by organization, or is listed with why")
    void everyRepositoryMethodIsScoped() throws ClassNotFoundException {
        Set<String> unscoped = new TreeSet<>();
        for (Class<?> repository : repositories()) {
            if (!isTenantOwned(entityOf(repository))) {
                continue;
            }
            for (Method method : repository.getDeclaredMethods()) {
                if (!method.isSynthetic() && !method.isDefault() && !scoped(method)) {
                    unscoped.add(repository.getSimpleName() + "." + method.getName());
                }
            }
        }
        assertThat(unscoped).as("queries on an organization's table without an organization filter")
                .isEqualTo(new TreeSet<>(UNSCOPED_METHODS.keySet()));
    }

    @Test
    @DisplayName("no service reads an organization's table by id alone, unless it is listed with why")
    void noServiceReadsByIdAlone() throws IOException, ClassNotFoundException {
        Set<String> tenantRepositories = new TreeSet<>();
        for (Class<?> repository : repositories()) {
            if (isTenantOwned(entityOf(repository))) {
                tenantRepositories.add(repository.getSimpleName());
            }
        }
        Set<String> calls = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = INHERITED_CALL.matcher(Files.readString(file));
                while (m.find()) {
                    String repository = Character.toUpperCase(m.group(1).charAt(0)) + m.group(1).substring(1);
                    if (tenantRepositories.contains(repository)) {
                        calls.add(file.getFileName().toString().replace(".java", "") + ": " + m.group(1) + "."
                                + m.group(2));
                    }
                }
            }
        }
        assertThat(calls).as("JpaRepository calls on an organization's table").isEqualTo(new TreeSet<>(UNSCOPED_CALLS.keySet()));
    }

    @Test
    @DisplayName("the guard sees the tables it protects")
    void guardSeesTheTenantTables() throws ClassNotFoundException {
        Map<String, Boolean> owned = new TreeMap<>();
        for (Class<?> repository : repositories()) {
            owned.put(entityOf(repository).getSimpleName(), isTenantOwned(entityOf(repository)));
        }
        assertThat(owned).containsEntry("Contracts", true).containsEntry("Users", true)
                .containsEntry("Managers", true).containsEntry("FinancialValues", true)
                .containsEntry("AuditLog", true).containsEntry("Notification", true)
                .containsEntry("Organization", false);
    }

    /** By its name or its JPQL: a method that names the organization, or filters on it. */
    private static boolean scoped(Method method) {
        if (method.getName().contains("Org")) {
            return true;
        }
        Query query = method.getAnnotation(Query.class);
        return query != null && Pattern.compile("organization|orgId|org_id", Pattern.CASE_INSENSITIVE)
                .matcher(query.value()).find();
    }

    /** A table holding one organization's rows: a link to the organization, or its id. */
    private static boolean isTenantOwned(Class<?> entity) {
        for (Field field : entity.getDeclaredFields()) {
            if (field.getType() == Organization.class
                    || Set.of("orgId", "organizationId").contains(field.getName())) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> entityOf(Class<?> repository) {
        for (Type type : repository.getGenericInterfaces()) {
            if (type instanceof ParameterizedType p && p.getRawType() == JpaRepository.class) {
                return (Class<?>) p.getActualTypeArguments()[0];
            }
        }
        throw new IllegalStateException(repository + " is not a JpaRepository");
    }

    private static Set<Class<?>> repositories() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(JpaRepository.class));
        Set<Class<?>> found = new java.util.HashSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(REPOSITORIES)) {
            found.add(Class.forName(definition.getBeanClassName()));
        }
        return found;
    }
}
