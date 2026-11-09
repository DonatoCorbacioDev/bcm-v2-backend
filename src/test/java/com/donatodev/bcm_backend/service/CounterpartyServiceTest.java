package com.donatodev.bcm_backend.service;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import static org.mockito.Mockito.verifyNoInteractions;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.CounterpartyDTO;
import com.donatodev.bcm_backend.dto.CounterpartyInvoicingSummaryDTO;
import com.donatodev.bcm_backend.dto.VerifyCounterpartyIbanRequest;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.exception.CounterpartyNotFoundException;
import com.donatodev.bcm_backend.mapper.CounterpartyMapper;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;

/**
 * Unit tests for {@link CounterpartyService}, mirroring the coverage shape of
 * {@code BusinessAreaServiceTest} (its closest structural sibling), plus
 * dedicated tests for {@link CounterpartyService#resolveOrCreateByName} â€”
 * the resolve-or-create lookup used by contract Excel import.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class CounterpartyServiceTest {

    /** The organization every request carries (its token's); a test that needs another sets its own. */
    private static final UUID TENANT = uid(1);

    @BeforeEach
    void setup() {
        TenantContext.set(TENANT);
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken("admin", null, Collections.emptyList()));
        SecurityContextHolder.setContext(ctx);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Mock
    private CounterpartiesRepository repository;

    @Mock
    private CounterpartyMapper mapper;

    @Mock
    private ContractsRepository contractsRepository;

    @Mock
    private ElectronicInvoiceRepository invoiceRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private com.donatodev.bcm_backend.repository.FinancialValuesRepository financialValuesRepository;

    @InjectMocks
    private CounterpartyService service;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: CounterpartyService")
    @SuppressWarnings("unused")
    class VerifyCounterpartyService {

        @Test
        @Order(1)
        @DisplayName("Get all counterparties returns list of DTOs")
        void shouldGetAllCounterparties() {
            Counterparty entity1 = Counterparty.builder().id(uid(1)).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();
            Counterparty entity2 = Counterparty.builder().id(uid(2)).name("Beta Srl").type(CounterpartyType.SUPPLIER).build();
            CounterpartyDTO dto1 = new CounterpartyDTO(uid(1), "Alfa Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);
            CounterpartyDTO dto2 = new CounterpartyDTO(uid(2), "Beta Srl", CounterpartyType.SUPPLIER, null, null, null, null, null, null, null, null, null);

            when(repository.findAllByOrganizationId(TENANT)).thenReturn(Arrays.asList(entity1, entity2));
            when(mapper.toDTO(entity1)).thenReturn(dto1);
            when(mapper.toDTO(entity2)).thenReturn(dto2);

            List<CounterpartyDTO> result = service.getAllCounterparties();

            assertEquals(2, result.size());
            assertEquals("Alfa Srl", result.get(0).name());
            assertEquals("Beta Srl", result.get(1).name());
        }

        @Test
        @Order(2)
        @DisplayName("Get counterparty by ID returns DTO")
        void shouldGetCounterpartyById() {
            Counterparty entity = Counterparty.builder().id(uid(1)).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO dto = new CounterpartyDTO(uid(1), "Alfa Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);

            when(repository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));
            when(mapper.toDTO(entity)).thenReturn(dto);

            CounterpartyDTO result = service.getCounterpartyById(uid(1));

            assertEquals("Alfa Srl", result.name());
            assertEquals(CounterpartyType.CUSTOMER, result.type());
        }

        @Test
        @Order(3)
        @DisplayName("Get counterparty by ID throws exception if not found")
        void shouldThrowExceptionWhenCounterpartyNotFound() {
            when(repository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());

            CounterpartyNotFoundException ex =
                assertThrows(CounterpartyNotFoundException.class, () -> service.getCounterpartyById(uid(999)));
            assertEquals("ID controparte " + uid(999) + " non trovata", ex.getMessage());
        }

        @Test
        @Order(4)
        @DisplayName("Create counterparty returns saved DTO")
        void shouldCreateCounterparty() {
            CounterpartyDTO dto = new CounterpartyDTO(null, "New Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);
            Counterparty entity = Counterparty.builder().name("New Srl").type(CounterpartyType.CUSTOMER).build();
            Counterparty savedEntity = Counterparty.builder().id(uid(1)).name("New Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO savedDTO = new CounterpartyDTO(uid(1), "New Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);

            when(mapper.toEntity(dto)).thenReturn(entity);
            when(repository.save(entity)).thenReturn(savedEntity);
            when(mapper.toDTO(savedEntity)).thenReturn(savedDTO);

            CounterpartyDTO result = service.createCounterparty(dto);

            assertEquals(uid(1), result.id());
            assertEquals("New Srl", result.name());
        }

        @Test
        @Order(5)
        @DisplayName("Update counterparty returns updated DTO")
        void shouldUpdateCounterparty() {
            Counterparty existing = Counterparty.builder().id(uid(1)).name("Old").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO updatedDTO = new CounterpartyDTO(uid(1), "Updated", CounterpartyType.SUPPLIER,
                    "IT12345678901", "RSSMRA80A01H501U", "Via Roma 1", "Mario Rossi", "mario@updated.com", "+39123456", "Note", null, null);

            when(repository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDTO(any())).thenReturn(updatedDTO);

            CounterpartyDTO result = service.updateCounterparty(uid(1), updatedDTO);

            assertEquals("Updated", result.name());
            assertEquals(CounterpartyType.SUPPLIER, result.type());
            assertEquals(CounterpartyType.SUPPLIER, existing.getType());
            assertEquals("IT12345678901", existing.getVatNumber());
            assertEquals("mario@updated.com", existing.getContactEmail());
        }

        @Test
        @DisplayName("Update counterparty stores the address parts, codes upper case, blanks as not set")
        void shouldUpdateAddressParts() {
            Counterparty existing = Counterparty.builder().id(uid(1)).name("Old").type(CounterpartyType.SUPPLIER).build();
            CounterpartyDTO updatedDTO = new CounterpartyDTO(uid(1), "Rossi Impianti", CounterpartyType.SUPPLIER,
                    null, null, "Via Verdi 3", null, null, null, null, null, null, "20121", "Milano", "mi", " ");

            when(repository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDTO(any())).thenReturn(updatedDTO);

            service.updateCounterparty(uid(1), updatedDTO);

            assertEquals("20121", existing.getPostalCode());
            assertEquals("Milano", existing.getCity());
            assertEquals("MI", existing.getProvince());
            assertNull(existing.getCountry());
        }

        @Test
        @Order(6)
        @DisplayName("Update counterparty throws exception if not found")
        void shouldThrowExceptionWhenUpdatingMissingCounterparty() {
            CounterpartyDTO updatedDTO = new CounterpartyDTO(uid(1), "Updated", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);
            when(repository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.empty());

            CounterpartyNotFoundException ex =
                assertThrows(CounterpartyNotFoundException.class, () -> service.updateCounterparty(uid(1), updatedDTO));
            assertEquals("ID controparte " + uid(1) + " non trovata", ex.getMessage());
        }

        @Test
        @Order(7)
        @DisplayName("Delete counterparty calls repository")
        void shouldDeleteCounterparty() {
            UUID id = uid(1);
            Counterparty counterparty = Counterparty.builder().id(id).build();
            when(repository.findByIdAndOrganizationId(id, TENANT)).thenReturn(Optional.of(counterparty));

            service.deleteCounterparty(id);

            verify(repository, times(1)).delete(counterparty);
        }

        @Test
        @Order(8)
        @DisplayName("Delete counterparty throws if it doesn't exist")
        void shouldThrowWhenDeletingNonExistentCounterparty() {
            when(repository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());

            CounterpartyNotFoundException ex = assertThrows(CounterpartyNotFoundException.class,
                    () -> service.deleteCounterparty(uid(999)));
            assertEquals("ID controparte " + uid(999) + " non trovata", ex.getMessage());
            verify(repository, never()).delete(any(Counterparty.class));
        }

        @Test
        @Order(9)
        @DisplayName("getAllCounterparties with TenantContext uses org-filtered repository")
        void shouldGetAllCounterpartiesWithTenantContext() {
            Counterparty counterparty = Counterparty.builder().id(uid(1)).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO dto = new CounterpartyDTO(uid(1), "Alfa Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);

            TenantContext.set(uid(1));
            try {
                when(repository.findAllByOrganizationId(uid(1))).thenReturn(List.of(counterparty));
                when(mapper.toDTO(counterparty)).thenReturn(dto);

                List<CounterpartyDTO> result = service.getAllCounterparties();

                assertEquals(1, result.size());
                verify(repository).findAllByOrganizationId(uid(1));
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(10)
        @DisplayName("createCounterparty with TenantContext sets organization on entity")
        void shouldCreateCounterpartyWithTenantContext() {
            CounterpartyDTO dto = new CounterpartyDTO(null, "Gamma Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);
            Counterparty entity = Counterparty.builder().name("Gamma Srl").type(CounterpartyType.CUSTOMER).build();
            Counterparty saved = Counterparty.builder().id(uid(2)).name("Gamma Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO savedDTO = new CounterpartyDTO(uid(2), "Gamma Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);

            TenantContext.set(uid(5));
            try {
                when(mapper.toEntity(dto)).thenReturn(entity);
                when(repository.save(any())).thenReturn(saved);
                when(mapper.toDTO(saved)).thenReturn(savedDTO);

                CounterpartyDTO result = service.createCounterparty(dto);

                assertEquals(uid(2), result.id());
                assertNotNull(entity.getOrganization());
                assertEquals(uid(5), entity.getOrganization().getId());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(11)
        @DisplayName("getCounterpartyById with TenantContext uses org-scoped repository")
        void shouldGetCounterpartyByIdWithTenantContext() {
            Counterparty entity = Counterparty.builder().id(uid(1)).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO dto = new CounterpartyDTO(uid(1), "Alfa Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);

            TenantContext.set(uid(8));
            try {
                when(repository.findByIdAndOrganizationId(uid(1), uid(8))).thenReturn(Optional.of(entity));
                when(mapper.toDTO(entity)).thenReturn(dto);

                CounterpartyDTO result = service.getCounterpartyById(uid(1));

                assertEquals("Alfa Srl", result.name());
                verify(repository).findByIdAndOrganizationId(uid(1), uid(8));
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(12)
        @DisplayName("getInvoicingSummary compares a supplier's expected costs and invoices over the same months")
        void shouldGetInvoicingSummary() {
            Counterparty entity = Counterparty.builder().id(uid(1)).name("Alfa Srl").type(CounterpartyType.SUPPLIER).build();
            LocalDate today = LocalDate.now();
            int year = today.getYear();

            when(repository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(entity));
            when(contractsRepository.countActiveContractsByCounterpartyId(uid(1))).thenReturn(3);
            when(contractsRepository.sumActiveAnnualValueByCounterpartyId(uid(1))).thenReturn(12000.0);
            when(financialValuesRepository.sumCostByCounterpartyUpToMonth(uid(1), year, today.getMonthValue())).thenReturn(10000.0);
            when(invoiceRepository.sumConfirmedInvoicedAmountByCounterpartyIdAndYear(uid(1), year))
                    .thenReturn(new BigDecimal("8000.00"));
            when(invoiceRepository.countByContractCounterpartyId(uid(1))).thenReturn(5L);
            when(invoiceRepository.findLastInvoiceDateByCounterpartyId(uid(1))).thenReturn(LocalDate.of(2026, Month.MARCH, 1));

            CounterpartyInvoicingSummaryDTO result = service.getInvoicingSummary(uid(1));

            assertEquals(uid(1), result.counterpartyId());
            assertEquals("Alfa Srl", result.counterpartyName());
            assertEquals(3, result.activeContracts());
            assertEquals(12000.0, result.contractedValue());
            assertEquals(10000.0, result.expectedYtd());
            assertEquals(8000.0, result.invoicedYtd());
            assertEquals(5L, result.invoiceCount());
            assertEquals(LocalDate.of(2026, Month.MARCH, 1), result.lastInvoiceDate());
            // Against the expected so far, not the yearly value (which would say -33%).
            assertEquals(-20.0, result.variancePercent(), 0.0001);
            assertEquals(true, result.comparable());
        }

        @Test
        @Order(12)
        @DisplayName("getInvoicingSummary compares a counterparty that is both customer and supplier")
        void shouldCompareBoth() {
            Counterparty entity = Counterparty.builder().id(uid(3)).name("Gamma Srl").type(CounterpartyType.BOTH).build();
            when(repository.findByIdAndOrganizationId(uid(3), TENANT)).thenReturn(Optional.of(entity));
            // COALESCE(SUM(...), 0) in the query: never null from the database.
            when(invoiceRepository.sumConfirmedInvoicedAmountByCounterpartyIdAndYear(eq(uid(3)), anyInt()))
                    .thenReturn(BigDecimal.ZERO);

            assertEquals(true, service.getInvoicingSummary(uid(3)).comparable());
        }

        @Test
        @Order(13)
        @DisplayName("getInvoicingSummary doesn't compare a customer: its invoices aren't in BCM")
        void shouldNotCompareCustomer() {
            Counterparty entity = Counterparty.builder().id(uid(2)).name("Beta Srl").type(CounterpartyType.CUSTOMER).build();

            when(repository.findByIdAndOrganizationId(uid(2), TENANT)).thenReturn(Optional.of(entity));
            when(contractsRepository.countActiveContractsByCounterpartyId(uid(2))).thenReturn(1);
            when(contractsRepository.sumActiveAnnualValueByCounterpartyId(uid(2))).thenReturn(5000.0);
            when(invoiceRepository.countByContractCounterpartyId(uid(2))).thenReturn(0L);
            when(invoiceRepository.findLastInvoiceDateByCounterpartyId(uid(2))).thenReturn(null);

            CounterpartyInvoicingSummaryDTO result = service.getInvoicingSummary(uid(2));

            assertEquals(false, result.comparable());
            assertEquals(5000.0, result.contractedValue());
            assertEquals(0.0, result.variancePercent());
            assertEquals(0.0, result.invoicedYtd());
            verifyNoInteractions(financialValuesRepository);
        }

        @Test
        @Order(131)
        @DisplayName("getInvoicingSummary reports zero variance when nothing is expected yet")
        void shouldReportZeroVariancePercentWhenNothingExpected() {
            Counterparty entity = Counterparty.builder().id(uid(3)).name("Gamma Srl").type(CounterpartyType.SUPPLIER).build();
            LocalDate today = LocalDate.now();

            when(repository.findByIdAndOrganizationId(uid(3), TENANT)).thenReturn(Optional.of(entity));
            when(financialValuesRepository.sumCostByCounterpartyUpToMonth(uid(3), today.getYear(), today.getMonthValue())).thenReturn(0.0);
            when(invoiceRepository.sumConfirmedInvoicedAmountByCounterpartyIdAndYear(uid(3), today.getYear()))
                    .thenReturn(new BigDecimal("100.00"));

            assertEquals(0.0, service.getInvoicingSummary(uid(3)).variancePercent());
        }

        @Test
        @Order(14)
        @DisplayName("getInvoicingSummary throws when the counterparty doesn't exist")
        void shouldThrowWhenGettingInvoicingSummaryForMissingCounterparty() {
            when(repository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());

            assertThrows(CounterpartyNotFoundException.class, () -> service.getInvoicingSummary(uid(999)));
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: CounterpartyService.resolveOrCreateByName")
    @SuppressWarnings("unused")
    class ResolveOrCreateByName {

        @Test
        @Order(1)
        @DisplayName("Returns the existing counterparty on a case-insensitive name match within the org")
        void shouldReturnExistingCounterpartyOnCaseInsensitiveMatch() {
            Counterparty existing = Counterparty.builder().id(uid(3)).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();

            TenantContext.set(uid(1));
            try {
                when(repository.findByNameIgnoreCaseAndOrganizationId("alfa srl", uid(1)))
                        .thenReturn(Optional.of(existing));

                Counterparty result = service.resolveOrCreateByName("alfa srl");

                assertEquals(uid(3), result.getId());
                verify(repository, never()).save(any());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(2)
        @DisplayName("Creates a new CUSTOMER counterparty when no match exists in the org")
        void shouldCreateNewCounterpartyWhenNoMatch() {
            TenantContext.set(uid(1));
            try {
                when(repository.findByNameIgnoreCaseAndOrganizationId("Nuova Srl", uid(1)))
                        .thenReturn(Optional.empty());
                when(repository.save(any(Counterparty.class))).thenAnswer(inv -> {
                    Counterparty c = inv.getArgument(0);
                    c.setId(uid(42));
                    return c;
                });

                Counterparty result = service.resolveOrCreateByName("Nuova Srl");

                assertEquals(uid(42), result.getId());
                assertEquals("Nuova Srl", result.getName());
                assertEquals(CounterpartyType.CUSTOMER, result.getType());
                assertNotNull(result.getOrganization());
                assertEquals(uid(1), result.getOrganization().getId());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(3)
        @DisplayName("Without an organization it refuses, instead of creating a counterparty that belongs to nobody")
        void shouldCreateWithoutTenantContext() {
            TenantContext.clear();

            assertThrows(com.donatodev.bcm_backend.exception.MissingTenantException.class, () -> service.resolveOrCreateByName("No Org Srl"));
            verify(repository, never()).save(any(Counterparty.class));
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: CounterpartyService.confirmVerifiedIban")
    @SuppressWarnings("unused")
    class ConfirmVerifiedIban {

        private static final String VALID_IBAN = "IT60X0542811101000000123456";
        private static final String VALID_BIC = "COBADEFFXXX";

        @Test
        @Order(1)
        @DisplayName("First confirmation learns the IBAN and logs it as 'set'")
        void shouldSetVerifiedIbanForTheFirstTime() {
            Counterparty counterparty = Counterparty.builder().id(uid(1)).name("Alfa Srl").type(CounterpartyType.SUPPLIER).build();
            CounterpartyDTO dto = new CounterpartyDTO(uid(1), "Alfa Srl", CounterpartyType.SUPPLIER,
                    null, null, null, null, null, null, null, VALID_IBAN, VALID_BIC);

            when(repository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(counterparty));
            when(repository.save(any(Counterparty.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDTO(any(Counterparty.class))).thenReturn(dto);

            CounterpartyDTO result = service.confirmVerifiedIban(uid(1), new VerifyCounterpartyIbanRequest(VALID_IBAN, VALID_BIC));

            assertEquals(VALID_IBAN, result.verifiedIban());
            assertEquals(VALID_IBAN, counterparty.getVerifiedIban());
            assertEquals(VALID_BIC, counterparty.getVerifiedBic());

            ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
            verify(auditLogService).save(org.mockito.ArgumentMatchers.eq("VERIFIED_IBAN_CONFIRMED"),
                    org.mockito.ArgumentMatchers.eq("Counterparty"), org.mockito.ArgumentMatchers.eq(uid(1)),
                    org.mockito.ArgumentMatchers.eq("admin"), any(), details.capture());
            assertTrue(details.getValue().contains(" set "));
            assertTrue(details.getValue().contains("IT..."));
        }

        @Test
        @Order(2)
        @DisplayName("Replacing an already-verified IBAN logs it as 'changed' with both masked values")
        void shouldReplaceVerifiedIbanAndLogChange() {
            Counterparty counterparty = Counterparty.builder().id(uid(2)).name("Beta Srl").type(CounterpartyType.SUPPLIER)
                    .verifiedIban("DE89370400440532013000").build();
            CounterpartyDTO dto = new CounterpartyDTO(uid(2), "Beta Srl", CounterpartyType.SUPPLIER,
                    null, null, null, null, null, null, null, VALID_IBAN, null);

            when(repository.findByIdAndOrganizationId(uid(2), TENANT)).thenReturn(Optional.of(counterparty));
            when(repository.save(any(Counterparty.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDTO(any(Counterparty.class))).thenReturn(dto);

            service.confirmVerifiedIban(uid(2), new VerifyCounterpartyIbanRequest(VALID_IBAN, null));

            assertEquals(VALID_IBAN, counterparty.getVerifiedIban());
            assertNull(counterparty.getVerifiedBic());

            ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
            verify(auditLogService).save(org.mockito.ArgumentMatchers.eq("VERIFIED_IBAN_CONFIRMED"),
                    any(), any(), any(), any(), details.capture());
            assertTrue(details.getValue().contains(" changed "));
            assertTrue(details.getValue().contains("DE..."));
            assertTrue(details.getValue().contains("IT..."));
        }

        @Test
        @Order(3)
        @DisplayName("A blank (non-null) BIC is stored as null, same as no BIC at all")
        void shouldStoreBlankBicAsNull() {
            Counterparty counterparty = Counterparty.builder().id(uid(1)).name("Alfa Srl").type(CounterpartyType.SUPPLIER).build();
            when(repository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(counterparty));
            when(repository.save(any(Counterparty.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDTO(any(Counterparty.class))).thenReturn(
                    new CounterpartyDTO(uid(1), "Alfa Srl", CounterpartyType.SUPPLIER, null, null, null, null, null, null, null, VALID_IBAN, null));

            service.confirmVerifiedIban(uid(1), new VerifyCounterpartyIbanRequest(VALID_IBAN, "   "));

            assertNull(counterparty.getVerifiedBic());
        }

        @Test
        @Order(4)
        @DisplayName("Normalizes a lowercase IBAN with spaces before validating and saving")
        void shouldNormalizeIbanBeforeSaving() {
            Counterparty counterparty = Counterparty.builder().id(uid(1)).name("Alfa Srl").type(CounterpartyType.SUPPLIER).build();
            when(repository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(counterparty));
            when(repository.save(any(Counterparty.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDTO(any(Counterparty.class))).thenReturn(
                    new CounterpartyDTO(uid(1), "Alfa Srl", CounterpartyType.SUPPLIER, null, null, null, null, null, null, null, VALID_IBAN, null));

            service.confirmVerifiedIban(uid(1), new VerifyCounterpartyIbanRequest("it60 x054 2811 1010 0000 0123 456", null));

            assertEquals(VALID_IBAN, counterparty.getVerifiedIban());
        }

        @Test
        @Order(5)
        @DisplayName("Rejects a structurally invalid IBAN and doesn't save or log anything")
        void shouldRejectInvalidIban() {
            Counterparty counterparty = Counterparty.builder().id(uid(1)).name("Alfa Srl").type(CounterpartyType.SUPPLIER).build();
            when(repository.findByIdAndOrganizationId(uid(1), TENANT)).thenReturn(Optional.of(counterparty));
            VerifyCounterpartyIbanRequest request = new VerifyCounterpartyIbanRequest("NOTANIBAN", null);

            assertThrows(IllegalArgumentException.class,
                    () -> service.confirmVerifiedIban(uid(1), request));

            verify(repository, never()).save(any());
            verify(auditLogService, never()).save(any(), any(), any(), any(), any(), any());
        }

        @Test
        @Order(6)
        @DisplayName("Throws when the counterparty doesn't exist")
        void shouldThrowWhenCounterpartyNotFound() {
            when(repository.findByIdAndOrganizationId(uid(999), TENANT)).thenReturn(Optional.empty());
            VerifyCounterpartyIbanRequest request = new VerifyCounterpartyIbanRequest(VALID_IBAN, null);

            assertThrows(CounterpartyNotFoundException.class,
                    () -> service.confirmVerifiedIban(uid(999), request));
            verify(auditLogService, never()).save(any(), any(), any(), any(), any(), any());
        }
    }
}
