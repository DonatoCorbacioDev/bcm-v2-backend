package com.donatodev.bcm_backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.HomeAttentionDTO;
import com.donatodev.bcm_backend.dto.HomeAttentionDTO.Type;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.InvoiceMatchStatus;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.SepaPaymentBatch;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Home: what needs attention")
class HomeAttentionServiceTest {

    private static final Long ORG = 3L;
    private static final String VERIFIED_IBAN = "IT17M0503411701000062194812";

    @Mock private ContractsRepository contractsRepository;
    @Mock private ElectronicInvoiceRepository invoiceRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @InjectMocks private HomeAttentionService service;

    private final LocalDate today = LocalDate.now(ZoneId.systemDefault());
    private final Organization org = Organization.builder().id(ORG).name("Nortiva").build();
    private final Managers marco = manager(4L, "Marco", "Bellini");
    private final Managers anna = manager(5L, "Anna", "Verdi");

    @BeforeEach
    void setTenant() {
        TenantContext.set(ORG);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static Managers manager(Long id, String first, String last) {
        Managers m = new Managers();
        m.setId(id);
        m.setFirstName(first);
        m.setLastName(last);
        return m;
    }

    private void loggedInAs(String role, Managers manager) {
        when(currentUserResolver.resolve()).thenReturn(Users.builder().id(1L).username("u")
                .role(Roles.builder().role(role).build()).manager(manager).organization(org).build());
    }

    private Contracts contract(String number, LocalDate endDate, Managers manager) {
        return Contracts.builder().id((long) number.hashCode()).contractNumber(number)
                .counterparty(Counterparty.builder().name("Fornitore " + number).type(CounterpartyType.SUPPLIER)
                        .verifiedIban(VERIFIED_IBAN).build())
                .status(ContractStatus.ACTIVE).startDate(endDate.minusYears(1)).endDate(endDate)
                .manager(manager).organization(org).build();
    }

    private ElectronicInvoice invoice(Contracts contract, InvoiceMatchStatus status, String iban) {
        ElectronicInvoice i = new ElectronicInvoice();
        i.setId(77L);
        i.setContract(contract);
        i.setInvoiceNumber("2026/118");
        i.setInvoiceDate(today.minusDays(3));
        i.setTotalAmount(new BigDecimal("4800.00"));
        i.setSupplierIban(iban);
        i.setMatchStatus(status);
        i.setOrgId(ORG);
        return i;
    }

    @Test
    @DisplayName("Lists the notice deadline of a tacit renewal, not its far end date")
    void noticeDeadline() {
        loggedInAs("ADMIN", null);
        Contracts maintenance = contract("MAN-1", today.plusDays(110), marco);
        maintenance.setAutoRenew(true);
        maintenance.setNoticeDays(90);
        maintenance.setRenewalMonths(12);
        when(contractsRepository.findByStatusAndOrganization_Id(ContractStatus.ACTIVE, ORG)).thenReturn(List.of(maintenance));

        HomeAttentionDTO home = service.getAttention();

        HomeAttentionDTO.Item item = home.items().get(0);
        assertEquals(Type.NOTICE_DEADLINE, item.type());
        assertEquals(today.plusDays(20), item.dueDate());
        assertEquals(20, item.daysLeft());
        assertEquals(12, item.renewalMonths());
        assertEquals("Marco Bellini", item.managerName());
        assertEquals(1, home.dueSoonCount());
    }

    @Test
    @DisplayName("Drops a tacit renewal whose deadline has passed: it renews, there is nothing left to do")
    void passedDeadline() {
        loggedInAs("ADMIN", null);
        Contracts c = contract("MAN-2", today.plusDays(10), marco);
        c.setAutoRenew(true);
        c.setNoticeDays(90);
        c.setRenewalMonths(12);
        when(contractsRepository.findByStatusAndOrganization_Id(ContractStatus.ACTIVE, ORG)).thenReturn(List.of(c));

        assertEquals(List.of(), service.getAttention().items());
    }

    @Test
    @DisplayName("Asks to check a tacit renewal whose terms aren't complete when its end date is near")
    void renewalToCheck() {
        loggedInAs("ADMIN", null);
        Contracts c = contract("MAN-3", today.plusDays(5), marco);
        c.setAutoRenew(true);
        when(contractsRepository.findByStatusAndOrganization_Id(ContractStatus.ACTIVE, ORG)).thenReturn(List.of(c));

        assertEquals(Type.RENEWAL_TO_CHECK, service.getAttention().items().get(0).type());
    }

    @Test
    @DisplayName("Lists ordinary contracts ending within 30 days, and only those")
    void expiries() {
        loggedInAs("ADMIN", null);
        when(contractsRepository.findByStatusAndOrganization_Id(ContractStatus.ACTIVE, ORG)).thenReturn(List.of(
                contract("FAR", today.plusDays(31), marco),
                contract("SOON", today.plusDays(25), marco),
                contract("SOONER", today.plusDays(12), marco),
                contract("PAST", today.minusDays(1), marco)));

        HomeAttentionDTO home = service.getAttention();

        assertEquals(List.of("SOONER", "SOON"), home.items().stream().map(HomeAttentionDTO.Item::contractNumber).toList());
        assertTrue(home.items().stream().allMatch(i -> i.type() == Type.EXPIRY));
        assertEquals(2, home.dueSoonCount());
    }

    @Test
    @DisplayName("Puts a changed IBAN first, contract dates next, invoices to match last; counters match the list")
    void orderAndCounters() {
        loggedInAs("ADMIN", null);
        Contracts advisory = contract("FER-1", today.plusDays(400), marco);
        Contracts cloud = contract("NUV-1", today.plusDays(12), marco);
        when(contractsRepository.findByStatusAndOrganization_Id(ContractStatus.ACTIVE, ORG)).thenReturn(List.of(cloud, advisory));
        when(invoiceRepository.findByOrgIdAndSepaBatchIsNull(ORG)).thenReturn(List.of(
                invoice(cloud, InvoiceMatchStatus.SUGGESTED, VERIFIED_IBAN),
                invoice(advisory, InvoiceMatchStatus.CONFIRMED, "IT60X0542811101000000123456")));

        HomeAttentionDTO home = service.getAttention();

        assertEquals(List.of(Type.IBAN_CHANGED, Type.EXPIRY, Type.INVOICE_TO_MATCH),
                home.items().stream().map(HomeAttentionDTO.Item::type).toList());
        assertEquals(1, home.dueSoonCount());
        assertEquals(1, home.invoicesToMatchCount());
        assertEquals(1, home.ibanAlertsCount());
        assertEquals(30, home.windowDays());
    }

    @Test
    @DisplayName("Compares IBANs like the SEPA check: spaces and case don't count as a change")
    void ibanComparison() {
        Counterparty supplier = Counterparty.builder().verifiedIban(VERIFIED_IBAN).build();
        ElectronicInvoice same = invoice(null, InvoiceMatchStatus.CONFIRMED, "it17 m050 3411 7010 0006 2194 812");
        ElectronicInvoice none = invoice(null, InvoiceMatchStatus.CONFIRMED, null);

        assertEquals(false, HomeAttentionService.ibanChanged(same, supplier));
        assertEquals(false, HomeAttentionService.ibanChanged(none, supplier));
        assertEquals(false, HomeAttentionService.ibanChanged(same, Counterparty.builder().build()));
        // Normalized on both sides, so a verified IBAN stored with spaces or lowercase still matches.
        Counterparty storedLoosely = Counterparty.builder().verifiedIban("it17 m050 3411 7010 0006 2194 812").build();
        assertEquals(false, HomeAttentionService.ibanChanged(
                invoice(null, InvoiceMatchStatus.CONFIRMED, VERIFIED_IBAN), storedLoosely));
        assertEquals(true, HomeAttentionService.ibanChanged(
                invoice(null, InvoiceMatchStatus.CONFIRMED, "IT60X0542811101000000123456"), supplier));
        // RiBa: the supplier collects, no money goes to that IBAN, so no alert.
        ElectronicInvoice riba = invoice(null, InvoiceMatchStatus.CONFIRMED, "IT60X0542811101000000123456");
        riba.setPaymentMethod("MP12");
        assertEquals(false, HomeAttentionService.ibanChanged(riba, supplier));
    }

    @Test
    @DisplayName("Shows a MANAGER only their own contracts and invoices")
    void managerScope() {
        loggedInAs("MANAGER", marco);
        Contracts mine = contract("MINE", today.plusDays(10), marco);
        Contracts colleague = contract("THEIRS", today.plusDays(10), anna);
        when(contractsRepository.findByManagerIdAndStatus(4L, ContractStatus.ACTIVE)).thenReturn(List.of(mine));
        when(invoiceRepository.findByOrgIdAndSepaBatchIsNull(ORG)).thenReturn(List.of(
                invoice(colleague, InvoiceMatchStatus.UNMATCHED, "IT60X0542811101000000123456")));

        HomeAttentionDTO home = service.getAttention();

        assertEquals(List.of("MINE"), home.items().stream().map(HomeAttentionDTO.Item::contractNumber).toList());
        assertEquals(0, home.invoicesToMatchCount());
        assertEquals(0, home.ibanAlertsCount());
        verify(contractsRepository, never()).findByStatusAndOrganization_Id(ContractStatus.ACTIVE, ORG);
    }

    @Test
    @DisplayName("Skips invoices already paid: they are not returned by the query")
    void paidInvoicesAreExcludedByTheQuery() {
        loggedInAs("ADMIN", null);
        ElectronicInvoice paid = invoice(contract("NUV-2", today.plusDays(300), marco), InvoiceMatchStatus.CONFIRMED,
                "IT60X0542811101000000123456");
        paid.setSepaBatch(new SepaPaymentBatch());
        when(invoiceRepository.findByOrgIdAndSepaBatchIsNull(ORG)).thenReturn(List.of());

        assertEquals(0, service.getAttention().ibanAlertsCount());
    }

    @Test
    @DisplayName("Returns nothing, without querying, when the request carries no organization")
    void failsClosedWithoutTenant() {
        TenantContext.clear();

        HomeAttentionDTO home = service.getAttention();

        assertEquals(List.of(), home.items());
        verifyNoInteractions(contractsRepository, invoiceRepository, currentUserResolver);
    }

    @Test
    @DisplayName("Returns nothing for a MANAGER user not linked to a manager")
    void managerWithoutManagerRecord() {
        loggedInAs("MANAGER", null);

        assertEquals(List.of(), service.getAttention().items());
        verifyNoInteractions(contractsRepository, invoiceRepository);
    }
}
