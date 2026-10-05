package com.donatodev.bcm_backend.service;

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
import java.time.Year;
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

    @InjectMocks
    private CounterpartyService service;

    @BeforeEach
    void setup() {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken("admin", null, Collections.emptyList()));
        SecurityContextHolder.setContext(ctx);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: CounterpartyService")
    @SuppressWarnings("unused")
    class VerifyCounterpartyService {

        @Test
        @Order(1)
        @DisplayName("Get all counterparties returns list of DTOs")
        void shouldGetAllCounterparties() {
            Counterparty entity1 = Counterparty.builder().id(1L).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();
            Counterparty entity2 = Counterparty.builder().id(2L).name("Beta Srl").type(CounterpartyType.SUPPLIER).build();
            CounterpartyDTO dto1 = new CounterpartyDTO(1L, "Alfa Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);
            CounterpartyDTO dto2 = new CounterpartyDTO(2L, "Beta Srl", CounterpartyType.SUPPLIER, null, null, null, null, null, null, null, null, null);

            when(repository.findAll()).thenReturn(Arrays.asList(entity1, entity2));
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
            Counterparty entity = Counterparty.builder().id(1L).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO dto = new CounterpartyDTO(1L, "Alfa Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);

            when(repository.findById(1L)).thenReturn(Optional.of(entity));
            when(mapper.toDTO(entity)).thenReturn(dto);

            CounterpartyDTO result = service.getCounterpartyById(1L);

            assertEquals("Alfa Srl", result.name());
            assertEquals(CounterpartyType.CUSTOMER, result.type());
        }

        @Test
        @Order(3)
        @DisplayName("Get counterparty by ID throws exception if not found")
        void shouldThrowExceptionWhenCounterpartyNotFound() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            CounterpartyNotFoundException ex =
                assertThrows(CounterpartyNotFoundException.class, () -> service.getCounterpartyById(999L));
            assertEquals("ID controparte 999 non trovata", ex.getMessage());
        }

        @Test
        @Order(4)
        @DisplayName("Create counterparty returns saved DTO")
        void shouldCreateCounterparty() {
            CounterpartyDTO dto = new CounterpartyDTO(null, "New Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);
            Counterparty entity = Counterparty.builder().name("New Srl").type(CounterpartyType.CUSTOMER).build();
            Counterparty savedEntity = Counterparty.builder().id(1L).name("New Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO savedDTO = new CounterpartyDTO(1L, "New Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);

            when(mapper.toEntity(dto)).thenReturn(entity);
            when(repository.save(entity)).thenReturn(savedEntity);
            when(mapper.toDTO(savedEntity)).thenReturn(savedDTO);

            CounterpartyDTO result = service.createCounterparty(dto);

            assertEquals(1L, result.id());
            assertEquals("New Srl", result.name());
        }

        @Test
        @Order(5)
        @DisplayName("Update counterparty returns updated DTO")
        void shouldUpdateCounterparty() {
            Counterparty existing = Counterparty.builder().id(1L).name("Old").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO updatedDTO = new CounterpartyDTO(1L, "Updated", CounterpartyType.SUPPLIER,
                    "IT12345678901", "RSSMRA80A01H501U", "Via Roma 1", "Mario Rossi", "mario@updated.com", "+39123456", "Note", null, null);

            when(repository.findById(1L)).thenReturn(Optional.of(existing));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDTO(any())).thenReturn(updatedDTO);

            CounterpartyDTO result = service.updateCounterparty(1L, updatedDTO);

            assertEquals("Updated", result.name());
            assertEquals(CounterpartyType.SUPPLIER, result.type());
            assertEquals(CounterpartyType.SUPPLIER, existing.getType());
            assertEquals("IT12345678901", existing.getVatNumber());
            assertEquals("mario@updated.com", existing.getContactEmail());
        }

        @Test
        @Order(6)
        @DisplayName("Update counterparty throws exception if not found")
        void shouldThrowExceptionWhenUpdatingMissingCounterparty() {
            CounterpartyDTO updatedDTO = new CounterpartyDTO(1L, "Updated", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);
            when(repository.findById(1L)).thenReturn(Optional.empty());

            CounterpartyNotFoundException ex =
                assertThrows(CounterpartyNotFoundException.class, () -> service.updateCounterparty(1L, updatedDTO));
            assertEquals("ID controparte 1 non trovata", ex.getMessage());
        }

        @Test
        @Order(7)
        @DisplayName("Delete counterparty calls repository")
        void shouldDeleteCounterparty() {
            Long id = 1L;
            Counterparty counterparty = Counterparty.builder().id(id).build();
            when(repository.findById(id)).thenReturn(Optional.of(counterparty));

            service.deleteCounterparty(id);

            verify(repository, times(1)).delete(counterparty);
        }

        @Test
        @Order(8)
        @DisplayName("Delete counterparty throws if it doesn't exist")
        void shouldThrowWhenDeletingNonExistentCounterparty() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            CounterpartyNotFoundException ex = assertThrows(CounterpartyNotFoundException.class,
                    () -> service.deleteCounterparty(999L));
            assertEquals("ID controparte 999 non trovata", ex.getMessage());
            verify(repository, never()).delete(any(Counterparty.class));
        }

        @Test
        @Order(9)
        @DisplayName("getAllCounterparties with TenantContext uses org-filtered repository")
        void shouldGetAllCounterpartiesWithTenantContext() {
            Counterparty counterparty = Counterparty.builder().id(1L).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO dto = new CounterpartyDTO(1L, "Alfa Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);

            TenantContext.set(1L);
            try {
                when(repository.findAllByOrganizationId(1L)).thenReturn(List.of(counterparty));
                when(mapper.toDTO(counterparty)).thenReturn(dto);

                List<CounterpartyDTO> result = service.getAllCounterparties();

                assertEquals(1, result.size());
                verify(repository).findAllByOrganizationId(1L);
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
            Counterparty saved = Counterparty.builder().id(2L).name("Gamma Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO savedDTO = new CounterpartyDTO(2L, "Gamma Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);

            TenantContext.set(5L);
            try {
                when(mapper.toEntity(dto)).thenReturn(entity);
                when(repository.save(any())).thenReturn(saved);
                when(mapper.toDTO(saved)).thenReturn(savedDTO);

                CounterpartyDTO result = service.createCounterparty(dto);

                assertEquals(2L, result.id());
                assertNotNull(entity.getOrganization());
                assertEquals(5L, entity.getOrganization().getId());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(11)
        @DisplayName("getCounterpartyById with TenantContext uses org-scoped repository")
        void shouldGetCounterpartyByIdWithTenantContext() {
            Counterparty entity = Counterparty.builder().id(1L).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();
            CounterpartyDTO dto = new CounterpartyDTO(1L, "Alfa Srl", CounterpartyType.CUSTOMER, null, null, null, null, null, null, null, null, null);

            TenantContext.set(8L);
            try {
                when(repository.findByIdAndOrganizationId(1L, 8L)).thenReturn(Optional.of(entity));
                when(mapper.toDTO(entity)).thenReturn(dto);

                CounterpartyDTO result = service.getCounterpartyById(1L);

                assertEquals("Alfa Srl", result.name());
                verify(repository).findByIdAndOrganizationId(1L, 8L);
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(12)
        @DisplayName("getInvoicingSummary computes variance from contracted value vs. confirmed-invoiced YTD")
        void shouldGetInvoicingSummary() {
            Counterparty entity = Counterparty.builder().id(1L).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();
            int year = Year.now().getValue();

            when(repository.findById(1L)).thenReturn(Optional.of(entity));
            when(contractsRepository.countActiveContractsByCounterpartyId(1L)).thenReturn(3);
            when(contractsRepository.sumActiveAnnualValueByCounterpartyId(1L)).thenReturn(10000.0);
            when(invoiceRepository.sumConfirmedInvoicedAmountByCounterpartyIdAndYear(1L, year))
                    .thenReturn(new BigDecimal("8000.00"));
            when(invoiceRepository.countByContractCounterpartyId(1L)).thenReturn(5L);
            when(invoiceRepository.findLastInvoiceDateByCounterpartyId(1L)).thenReturn(LocalDate.of(2026, Month.MARCH, 1));

            CounterpartyInvoicingSummaryDTO result = service.getInvoicingSummary(1L);

            assertEquals(1L, result.counterpartyId());
            assertEquals("Alfa Srl", result.counterpartyName());
            assertEquals(3, result.activeContracts());
            assertEquals(10000.0, result.contractedValue());
            assertEquals(8000.0, result.invoicedYtd());
            assertEquals(5L, result.invoiceCount());
            assertEquals(LocalDate.of(2026, Month.MARCH, 1), result.lastInvoiceDate());
            assertEquals(-20.0, result.variancePercent(), 0.0001);
        }

        @Test
        @Order(13)
        @DisplayName("getInvoicingSummary reports zero variance when there is no contracted value yet")
        void shouldReportZeroVariancePercentWhenContractedValueIsZero() {
            Counterparty entity = Counterparty.builder().id(2L).name("Beta Srl").type(CounterpartyType.CUSTOMER).build();
            int year = Year.now().getValue();

            when(repository.findById(2L)).thenReturn(Optional.of(entity));
            when(contractsRepository.countActiveContractsByCounterpartyId(2L)).thenReturn(0);
            when(contractsRepository.sumActiveAnnualValueByCounterpartyId(2L)).thenReturn(0.0);
            when(invoiceRepository.sumConfirmedInvoicedAmountByCounterpartyIdAndYear(2L, year))
                    .thenReturn(BigDecimal.ZERO);
            when(invoiceRepository.countByContractCounterpartyId(2L)).thenReturn(0L);
            when(invoiceRepository.findLastInvoiceDateByCounterpartyId(2L)).thenReturn(null);

            CounterpartyInvoicingSummaryDTO result = service.getInvoicingSummary(2L);

            assertEquals(0.0, result.variancePercent());
            assertEquals(null, result.lastInvoiceDate());
        }

        @Test
        @Order(14)
        @DisplayName("getInvoicingSummary throws when the counterparty doesn't exist")
        void shouldThrowWhenGettingInvoicingSummaryForMissingCounterparty() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(CounterpartyNotFoundException.class, () -> service.getInvoicingSummary(999L));
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
            Counterparty existing = Counterparty.builder().id(3L).name("Alfa Srl").type(CounterpartyType.CUSTOMER).build();

            TenantContext.set(1L);
            try {
                when(repository.findByNameIgnoreCaseAndOrganizationId("alfa srl", 1L))
                        .thenReturn(Optional.of(existing));

                Counterparty result = service.resolveOrCreateByName("alfa srl");

                assertEquals(3L, result.getId());
                verify(repository, never()).save(any());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(2)
        @DisplayName("Creates a new CUSTOMER counterparty when no match exists in the org")
        void shouldCreateNewCounterpartyWhenNoMatch() {
            TenantContext.set(1L);
            try {
                when(repository.findByNameIgnoreCaseAndOrganizationId("Nuova Srl", 1L))
                        .thenReturn(Optional.empty());
                when(repository.save(any(Counterparty.class))).thenAnswer(inv -> {
                    Counterparty c = inv.getArgument(0);
                    c.setId(42L);
                    return c;
                });

                Counterparty result = service.resolveOrCreateByName("Nuova Srl");

                assertEquals(42L, result.getId());
                assertEquals("Nuova Srl", result.getName());
                assertEquals(CounterpartyType.CUSTOMER, result.getType());
                assertNotNull(result.getOrganization());
                assertEquals(1L, result.getOrganization().getId());
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @Order(3)
        @DisplayName("Without a tenant context, always creates rather than looking up by name")
        void shouldCreateWithoutTenantContext() {
            when(repository.save(any(Counterparty.class))).thenAnswer(inv -> {
                Counterparty c = inv.getArgument(0);
                c.setId(7L);
                return c;
            });

            Counterparty result = service.resolveOrCreateByName("No Org Srl");

            assertEquals(7L, result.getId());
            assertEquals(CounterpartyType.CUSTOMER, result.getType());
            verify(repository, never()).findByNameIgnoreCaseAndOrganizationId(any(), any());
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
            Counterparty counterparty = Counterparty.builder().id(1L).name("Alfa Srl").type(CounterpartyType.SUPPLIER).build();
            CounterpartyDTO dto = new CounterpartyDTO(1L, "Alfa Srl", CounterpartyType.SUPPLIER,
                    null, null, null, null, null, null, null, VALID_IBAN, VALID_BIC);

            when(repository.findById(1L)).thenReturn(Optional.of(counterparty));
            when(repository.save(any(Counterparty.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDTO(any(Counterparty.class))).thenReturn(dto);

            CounterpartyDTO result = service.confirmVerifiedIban(1L, new VerifyCounterpartyIbanRequest(VALID_IBAN, VALID_BIC));

            assertEquals(VALID_IBAN, result.verifiedIban());
            assertEquals(VALID_IBAN, counterparty.getVerifiedIban());
            assertEquals(VALID_BIC, counterparty.getVerifiedBic());

            ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
            verify(auditLogService).save(org.mockito.ArgumentMatchers.eq("VERIFIED_IBAN_CONFIRMED"),
                    org.mockito.ArgumentMatchers.eq("Counterparty"), org.mockito.ArgumentMatchers.eq(1L),
                    org.mockito.ArgumentMatchers.eq("admin"), any(), details.capture());
            assertTrue(details.getValue().contains(" set "));
            assertTrue(details.getValue().contains("IT..."));
        }

        @Test
        @Order(2)
        @DisplayName("Replacing an already-verified IBAN logs it as 'changed' with both masked values")
        void shouldReplaceVerifiedIbanAndLogChange() {
            Counterparty counterparty = Counterparty.builder().id(2L).name("Beta Srl").type(CounterpartyType.SUPPLIER)
                    .verifiedIban("DE89370400440532013000").build();
            CounterpartyDTO dto = new CounterpartyDTO(2L, "Beta Srl", CounterpartyType.SUPPLIER,
                    null, null, null, null, null, null, null, VALID_IBAN, null);

            when(repository.findById(2L)).thenReturn(Optional.of(counterparty));
            when(repository.save(any(Counterparty.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDTO(any(Counterparty.class))).thenReturn(dto);

            service.confirmVerifiedIban(2L, new VerifyCounterpartyIbanRequest(VALID_IBAN, null));

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
            Counterparty counterparty = Counterparty.builder().id(1L).name("Alfa Srl").type(CounterpartyType.SUPPLIER).build();
            when(repository.findById(1L)).thenReturn(Optional.of(counterparty));
            when(repository.save(any(Counterparty.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDTO(any(Counterparty.class))).thenReturn(
                    new CounterpartyDTO(1L, "Alfa Srl", CounterpartyType.SUPPLIER, null, null, null, null, null, null, null, VALID_IBAN, null));

            service.confirmVerifiedIban(1L, new VerifyCounterpartyIbanRequest(VALID_IBAN, "   "));

            assertNull(counterparty.getVerifiedBic());
        }

        @Test
        @Order(4)
        @DisplayName("Normalizes a lowercase IBAN with spaces before validating and saving")
        void shouldNormalizeIbanBeforeSaving() {
            Counterparty counterparty = Counterparty.builder().id(1L).name("Alfa Srl").type(CounterpartyType.SUPPLIER).build();
            when(repository.findById(1L)).thenReturn(Optional.of(counterparty));
            when(repository.save(any(Counterparty.class))).thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDTO(any(Counterparty.class))).thenReturn(
                    new CounterpartyDTO(1L, "Alfa Srl", CounterpartyType.SUPPLIER, null, null, null, null, null, null, null, VALID_IBAN, null));

            service.confirmVerifiedIban(1L, new VerifyCounterpartyIbanRequest("it60 x054 2811 1010 0000 0123 456", null));

            assertEquals(VALID_IBAN, counterparty.getVerifiedIban());
        }

        @Test
        @Order(5)
        @DisplayName("Rejects a structurally invalid IBAN and doesn't save or log anything")
        void shouldRejectInvalidIban() {
            Counterparty counterparty = Counterparty.builder().id(1L).name("Alfa Srl").type(CounterpartyType.SUPPLIER).build();
            when(repository.findById(1L)).thenReturn(Optional.of(counterparty));
            VerifyCounterpartyIbanRequest request = new VerifyCounterpartyIbanRequest("NOTANIBAN", null);

            assertThrows(IllegalArgumentException.class,
                    () -> service.confirmVerifiedIban(1L, request));

            verify(repository, never()).save(any());
            verify(auditLogService, never()).save(any(), any(), any(), any(), any(), any());
        }

        @Test
        @Order(6)
        @DisplayName("Throws when the counterparty doesn't exist")
        void shouldThrowWhenCounterpartyNotFound() {
            when(repository.findById(999L)).thenReturn(Optional.empty());
            VerifyCounterpartyIbanRequest request = new VerifyCounterpartyIbanRequest(VALID_IBAN, null);

            assertThrows(CounterpartyNotFoundException.class,
                    () -> service.confirmVerifiedIban(999L, request));
            verify(auditLogService, never()).save(any(), any(), any(), any(), any(), any());
        }
    }
}
