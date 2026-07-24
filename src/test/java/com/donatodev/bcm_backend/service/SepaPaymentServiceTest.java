package com.donatodev.bcm_backend.service;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.SepaPaymentBatchDTO;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.SepaPaymentBatch;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.SepaPaymentBatchRepository;

@ExtendWith(MockitoExtension.class)
class SepaPaymentServiceTest {

    private static final Long CONTRACT_ID = 1L;
    private static final Long ORG_ID = 5L;

    @Mock private ContractAccessGuard contractAccessGuard;
    @Mock private ElectronicInvoiceRepository invoiceRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private SepaPaymentBatchRepository batchRepository;
    @Mock private LocalStorageService localStorageService;

    @InjectMocks
    private SepaPaymentService sepaPaymentService;

    @BeforeEach
    void setup() {
        TenantContext.set(ORG_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Contracts fakeContract() {
        Contracts c = new Contracts();
        c.setId(CONTRACT_ID);
        return c;
    }

    private Organization fakeOrganization(String iban, String bic) {
        return Organization.builder().id(ORG_ID).name("Acme S.r.l.").iban(iban).bic(bic).build();
    }

    private ElectronicInvoice fakeInvoice(Long id, String supplierIban, String currency, BigDecimal amount) {
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(id);
        invoice.setSupplierName("Fornitore " + id);
        invoice.setSupplierIban(supplierIban);
        invoice.setSupplierBic("COBADEFFXXX");
        invoice.setCurrency(currency);
        invoice.setTotalAmount(amount);
        invoice.setInvoiceNumber("INV-" + id);
        return invoice;
    }

    @Nested
    @DisplayName("createSepaPayment")
    class CreateSepaPayment {

        @Test
        @DisplayName("happy path: builds a valid pain.001 XML, persists batch, tags invoices")
        void shouldCreateSepaPayment() throws Exception {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice1 = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            ElectronicInvoice invoice2 = fakeInvoice(11L, "FR1420041010050500013M02606", "EUR", new BigDecimal("50.50"));

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, List.of(10L, 11L)))
                    .thenReturn(List.of(invoice1, invoice2));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(localStorageService.storeSepaPayment(eq(ORG_ID), eq(CONTRACT_ID), any()))
                    .thenReturn("sepa/5/1/uuid.xml");
            when(batchRepository.save(any(SepaPaymentBatch.class))).thenAnswer(inv -> {
                SepaPaymentBatch batch = inv.getArgument(0);
                batch.setId(99L);
                return batch;
            });

            LocalDate executionDate = LocalDate.now().plusDays(1);
            FileDownload result = sepaPaymentService.createSepaPayment(CONTRACT_ID, List.of(10L, 11L), executionDate);

            assertEquals("application/xml", result.contentType());

            Document doc = parse(result.bytes());
            assertEquals("2", textOf(doc, "NbOfTxs"));
            assertEquals(2, doc.getElementsByTagName("CdtTrfTxInf").getLength());
            assertEquals(0, new BigDecimal("150.50").compareTo(new BigDecimal(textOf(doc, "CtrlSum"))));
            assertTrue(xmlContains(doc, "IBAN", "DE89370400440532013000"));
            assertTrue(xmlContains(doc, "IBAN", "IT60X0542811101000000123456"));
            assertTrue(xmlContains(doc, "IBAN", "FR1420041010050500013M02606"));

            verify(invoiceRepository).saveAll(List.of(invoice1, invoice2));
            assertEquals(99L, invoice1.getSepaBatch().getId());
            assertEquals(99L, invoice2.getSepaBatch().getId());
        }

        @Test
        @DisplayName("throws when the organization has no IBAN configured")
        void shouldThrowWhenOrgHasNoIban() {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization(null, null);
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));

            List<Long> invoiceIds = List.of(10L);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null));
            verify(batchRepository, never()).save(any());
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("invalidIbanScenarios")
        @DisplayName("throws when a required IBAN is missing or blank")
        void shouldThrowForInvalidIban(String description, String orgIban, String invoiceIban) {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization(orgIban, "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, invoiceIban, "EUR", new BigDecimal("100.00"));
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null));
        }

        static Stream<Arguments> invalidIbanScenarios() {
            return Stream.of(
                    Arguments.of("invoice supplier IBAN is null", "DE89370400440532013000", null),
                    Arguments.of("invoice supplier IBAN is blank", "DE89370400440532013000", "   "),
                    Arguments.of("organization IBAN is blank", "   ", "IT60X0542811101000000123456")
            );
        }

        @Test
        @DisplayName("throws when a selected invoice is already part of a SEPA batch")
        void shouldThrowWhenInvoiceAlreadyBatched() {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            invoice.setSepaBatch(SepaPaymentBatch.builder().id(1L).build());

            List<Long> invoiceIds = List.of(10L);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null));
        }

        @Test
        @DisplayName("throws when some invoice IDs don't belong to the contract")
        void shouldThrowWhenInvoiceIdsNotFound() {
            Contracts contract = fakeContract();
            List<Long> invoiceIds = List.of(10L, 11L);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds))
                    .thenReturn(List.of(fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", BigDecimal.TEN)));

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null));
        }

        @Test
        @DisplayName("throws when selected invoices mix currencies")
        void shouldThrowWhenCurrenciesMixed() {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice1 = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", BigDecimal.TEN);
            ElectronicInvoice invoice2 = fakeInvoice(11L, "FR1420041010050500013M02606", "USD", BigDecimal.TEN);

            List<Long> invoiceIds = List.of(10L, 11L);
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds))
                    .thenReturn(List.of(invoice1, invoice2));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null));
        }

        @Test
        @DisplayName("throws when the requested execution date is in the past")
        void shouldThrowWhenExecutionDateInPast() {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", BigDecimal.TEN);
            List<Long> invoiceIds = List.of(10L);
            LocalDate pastDate = LocalDate.now().minusDays(1);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, pastDate));
        }

        @Test
        @DisplayName("defaults the execution date to today when none is requested")
        void shouldDefaultExecutionDateToToday() throws Exception {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(localStorageService.storeSepaPayment(eq(ORG_ID), eq(CONTRACT_ID), any())).thenReturn("sepa/5/1/uuid.xml");
            when(batchRepository.save(any(SepaPaymentBatch.class))).thenAnswer(inv -> inv.getArgument(0));

            FileDownload result = sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null);

            Document doc = parse(result.bytes());
            assertEquals(LocalDate.now().toString(), textOf(doc, "ReqdExctnDt"));
        }

        @Test
        @DisplayName("resolves the debtor organization from the contract when there is no tenant context")
        void shouldResolveOrganizationFromContractWhenNoTenantContext() throws Exception {
            TenantContext.clear();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            Contracts contract = fakeContract();
            contract.setOrganization(org);
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(localStorageService.storeSepaPayment(eq(null), eq(CONTRACT_ID), any())).thenReturn("sepa/0/1/uuid.xml");
            when(batchRepository.save(any(SepaPaymentBatch.class))).thenAnswer(inv -> inv.getArgument(0));

            FileDownload result = sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null);

            assertTrue(xmlContains(parse(result.bytes()), "IBAN", "DE89370400440532013000"));
            verify(organizationRepository, never()).findById(any());
        }

        @Test
        @DisplayName("throws when there is no tenant context and the contract has no organization")
        void shouldThrowWhenNoTenantContextAndContractHasNoOrganization() {
            TenantContext.clear();
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null));
        }

        @Test
        @DisplayName("throws when the tenant's organization id doesn't resolve to an organization")
        void shouldThrowWhenTenantOrganizationNotFound() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null));
        }

        @Test
        @DisplayName("defaults an invoice's currency to EUR when it isn't set")
        void shouldDefaultInvoiceCurrencyToEurWhenMissing() {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", null, new BigDecimal("100.00"));
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(localStorageService.storeSepaPayment(eq(ORG_ID), eq(CONTRACT_ID), any())).thenReturn("sepa/5/1/uuid.xml");
            ArgumentCaptor<SepaPaymentBatch> captor = ArgumentCaptor.forClass(SepaPaymentBatch.class);
            when(batchRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null);

            assertEquals("EUR", captor.getValue().getCurrency());
        }

        @Test
        @DisplayName("creates a zero-transaction batch when no invoice ids are given")
        void shouldCreateZeroInvoiceBatchWhenInvoiceIdsEmpty() throws Exception {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            List<Long> invoiceIds = List.of();

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of());
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(localStorageService.storeSepaPayment(eq(ORG_ID), eq(CONTRACT_ID), any())).thenReturn("sepa/5/1/uuid.xml");
            ArgumentCaptor<SepaPaymentBatch> captor = ArgumentCaptor.forClass(SepaPaymentBatch.class);
            when(batchRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            FileDownload result = sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null);

            assertEquals("EUR", captor.getValue().getCurrency());
            assertEquals("0", textOf(parse(result.bytes()), "NbOfTxs"));
        }

        @Test
        @DisplayName("falls back to NOTPROVIDED and a generic remittance when the invoice has no number")
        void shouldFallBackWhenInvoiceNumberMissing() throws Exception {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            invoice.setInvoiceNumber(null);
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(localStorageService.storeSepaPayment(eq(ORG_ID), eq(CONTRACT_ID), any())).thenReturn("sepa/5/1/uuid.xml");
            when(batchRepository.save(any(SepaPaymentBatch.class))).thenAnswer(inv -> inv.getArgument(0));

            FileDownload result = sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null);

            Document doc = parse(result.bytes());
            assertEquals("NOTPROVIDED", textOf(doc, "EndToEndId"));
            assertEquals("Pagamento fattura 10", textOf(doc, "Ustrd"));
        }

        @Test
        @DisplayName("uses NOTPROVIDED when the invoice number sanitizes to an empty string")
        void shouldUseNotProvidedWhenInvoiceNumberSanitizesToEmpty() throws Exception {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            invoice.setInvoiceNumber("###");
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(localStorageService.storeSepaPayment(eq(ORG_ID), eq(CONTRACT_ID), any())).thenReturn("sepa/5/1/uuid.xml");
            when(batchRepository.save(any(SepaPaymentBatch.class))).thenAnswer(inv -> inv.getArgument(0));

            FileDownload result = sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null);

            assertEquals("NOTPROVIDED", textOf(parse(result.bytes()), "EndToEndId"));
        }

        @Test
        @DisplayName("defaults a missing invoice amount to zero in the XML")
        void shouldDefaultInvoiceAmountToZeroWhenMissing() throws Exception {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", null);
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(localStorageService.storeSepaPayment(eq(ORG_ID), eq(CONTRACT_ID), any())).thenReturn("sepa/5/1/uuid.xml");
            when(batchRepository.save(any(SepaPaymentBatch.class))).thenAnswer(inv -> inv.getArgument(0));

            FileDownload result = sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null);

            assertEquals("0.00", textOf(parse(result.bytes()), "InstdAmt"));
        }

        @Test
        @DisplayName("uses NOTPROVIDED under DbtrAgt when the organization has no BIC")
        void shouldOmitOrganizationBicWhenMissing() throws Exception {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", null);
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(localStorageService.storeSepaPayment(eq(ORG_ID), eq(CONTRACT_ID), any())).thenReturn("sepa/5/1/uuid.xml");
            when(batchRepository.save(any(SepaPaymentBatch.class))).thenAnswer(inv -> inv.getArgument(0));

            FileDownload result = sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null);

            Document doc = parse(result.bytes());
            Element dbtrAgt = (Element) doc.getElementsByTagName("DbtrAgt").item(0);
            assertEquals(0, dbtrAgt.getElementsByTagName("BIC").getLength());
            assertEquals(1, dbtrAgt.getElementsByTagName("Othr").getLength());
        }

        @Test
        @DisplayName("uses NOTPROVIDED under CdtrAgt when the supplier has no BIC")
        void shouldOmitSupplierBicWhenMissing() throws Exception {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            invoice.setSupplierBic(null);
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(localStorageService.storeSepaPayment(eq(ORG_ID), eq(CONTRACT_ID), any())).thenReturn("sepa/5/1/uuid.xml");
            when(batchRepository.save(any(SepaPaymentBatch.class))).thenAnswer(inv -> inv.getArgument(0));

            FileDownload result = sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null);

            Document doc = parse(result.bytes());
            Element cdtrAgt = (Element) doc.getElementsByTagName("CdtrAgt").item(0);
            assertEquals(0, cdtrAgt.getElementsByTagName("BIC").getLength());
            assertEquals(1, cdtrAgt.getElementsByTagName("Othr").getLength());
        }

        @Test
        @DisplayName("uses NOTPROVIDED under DbtrAgt when the organization BIC is blank")
        void shouldOmitOrganizationBicWhenBlank() throws Exception {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "   ");
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(localStorageService.storeSepaPayment(eq(ORG_ID), eq(CONTRACT_ID), any())).thenReturn("sepa/5/1/uuid.xml");
            when(batchRepository.save(any(SepaPaymentBatch.class))).thenAnswer(inv -> inv.getArgument(0));

            FileDownload result = sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null);

            Document doc = parse(result.bytes());
            Element dbtrAgt = (Element) doc.getElementsByTagName("DbtrAgt").item(0);
            assertEquals(0, dbtrAgt.getElementsByTagName("BIC").getLength());
            assertEquals(1, dbtrAgt.getElementsByTagName("Othr").getLength());
        }

        @Test
        @DisplayName("strips diacritics and truncates a debtor name longer than 70 characters")
        void shouldSanitizeAndTruncateLongOrganizationName() throws Exception {
            Contracts contract = fakeContract();
            String longName = "Società Élaboratà ".repeat(5); // > 70 chars, has diacritics
            Organization org = Organization.builder().id(ORG_ID).name(longName)
                    .iban("DE89370400440532013000").bic("COBADEFFXXX").build();
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(localStorageService.storeSepaPayment(eq(ORG_ID), eq(CONTRACT_ID), any())).thenReturn("sepa/5/1/uuid.xml");
            when(batchRepository.save(any(SepaPaymentBatch.class))).thenAnswer(inv -> inv.getArgument(0));

            FileDownload result = sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null);

            String nm = textOf(parse(result.bytes()), "Nm");
            assertTrue(nm.length() <= 70);
            assertTrue(nm.startsWith("Societa Elaborata"));
        }

        @Test
        @DisplayName("treats a missing organization name as an empty debtor name")
        void shouldUseEmptyNameWhenOrganizationNameIsNull() throws Exception {
            Contracts contract = fakeContract();
            Organization org = Organization.builder().id(ORG_ID).name(null)
                    .iban("DE89370400440532013000").bic("COBADEFFXXX").build();
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(localStorageService.storeSepaPayment(eq(ORG_ID), eq(CONTRACT_ID), any())).thenReturn("sepa/5/1/uuid.xml");
            when(batchRepository.save(any(SepaPaymentBatch.class))).thenAnswer(inv -> inv.getArgument(0));

            FileDownload result = sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null);

            assertEquals("", textOf(parse(result.bytes()), "Nm"));
        }

        @Test
        @DisplayName("wraps a broken XML parser as an IllegalStateException")
        void shouldWrapParserConfigurationFailure() throws Exception {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            DocumentBuilderFactory brokenFactory = spy(DocumentBuilderFactory.newInstance());
            doThrow(new ParserConfigurationException("boom"))
                    .when(brokenFactory).newDocumentBuilder();

            try (MockedStatic<DocumentBuilderFactory> mocked = mockStatic(DocumentBuilderFactory.class)) {
                mocked.when(DocumentBuilderFactory::newInstance).thenReturn(brokenFactory);

                assertThrows(IllegalStateException.class,
                        () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null));
            }
        }

        @Test
        @DisplayName("wraps a broken XML transformer as an UncheckedIOException")
        void shouldWrapTransformerFailure() throws Exception {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            List<Long> invoiceIds = List.of(10L);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, invoiceIds)).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            TransformerFactory brokenFactory = spy(TransformerFactory.newInstance());
            doThrow(new TransformerConfigurationException("boom"))
                    .when(brokenFactory).newTransformer();

            try (MockedStatic<TransformerFactory> mocked = mockStatic(TransformerFactory.class)) {
                mocked.when(TransformerFactory::newInstance).thenReturn(brokenFactory);

                assertThrows(java.io.UncheckedIOException.class,
                        () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, invoiceIds, null));
            }
        }
    }

    @Nested
    @DisplayName("getPayments / downloadPayment")
    class GetAndDownload {

        @Test
        @DisplayName("getPayments: maps batches to DTOs")
        void shouldListPayments() {
            Contracts contract = fakeContract();
            SepaPaymentBatch batch = SepaPaymentBatch.builder()
                    .id(1L).contract(contract).executionDate(LocalDate.now())
                    .totalAmount(new BigDecimal("10.00")).currency("EUR")
                    .numberOfTransactions(1).fileName("sepa-1.xml").build();

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(batchRepository.findByContractIdOrderByCreatedAtDesc(CONTRACT_ID)).thenReturn(List.of(batch));

            List<SepaPaymentBatchDTO> result = sepaPaymentService.getPayments(CONTRACT_ID);

            assertEquals(1, result.size());
            assertEquals("sepa-1.xml", result.get(0).fileName());
        }

        @Test
        @DisplayName("downloadPayment: returns stored bytes")
        void shouldDownloadPayment() {
            Contracts contract = fakeContract();
            SepaPaymentBatch batch = SepaPaymentBatch.builder()
                    .id(1L).contract(contract).storagePath("sepa/5/1/uuid.xml").fileName("sepa-1.xml").build();

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(batchRepository.findByIdAndContractId(1L, CONTRACT_ID)).thenReturn(Optional.of(batch));
            when(localStorageService.readDocument("sepa/5/1/uuid.xml")).thenReturn("<xml/>".getBytes());

            FileDownload result = sepaPaymentService.downloadPayment(CONTRACT_ID, 1L);

            assertEquals("sepa-1.xml", result.fileName());
        }

        @Test
        @DisplayName("downloadPayment: throws when batch not found for contract")
        void shouldThrowWhenBatchNotFound() {
            Contracts contract = fakeContract();
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(batchRepository.findByIdAndContractId(1L, CONTRACT_ID)).thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> sepaPaymentService.downloadPayment(CONTRACT_ID, 1L));
        }
    }

    private Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private String textOf(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        return nodes.item(0).getTextContent();
    }

    private boolean xmlContains(Document doc, String tagName, String value) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            if (value.equals(nodes.item(i).getTextContent())) {
                return true;
            }
        }
        return false;
    }

}
