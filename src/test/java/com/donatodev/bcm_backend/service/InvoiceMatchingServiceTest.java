package com.donatodev.bcm_backend.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.entity.InvoiceMatchStatus;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class InvoiceMatchingServiceTest {

    @Mock private ElectronicInvoiceRepository invoiceRepository;
    @Mock private FinancialValuesRepository financialValuesRepository;
    @Mock private UsersRepository usersRepository;

    private InvoiceMatchingService invoiceMatchingService;

    private static final long CONTRACT_ID = 1L;

    @BeforeEach
    void setup() {
        invoiceMatchingService = new InvoiceMatchingService(invoiceRepository, financialValuesRepository, usersRepository);
        // Unstubbed by default in most tests: no invoice is already CONFIRMED against any candidate.
        lenientNoOtherConfirmedMatch();
    }

    private void lenientNoOtherConfirmedMatch() {
        lenient()
                .when(invoiceRepository.findByMatchedFinancialValue_IdAndMatchStatus(anyLong(), any()))
                .thenReturn(Optional.empty());
    }

    private Counterparty counterparty() {
        Counterparty c = new Counterparty();
        c.setId(50L);
        c.setName("Vertice Legal S.r.l.");
        c.setType(CounterpartyType.CUSTOMER);
        c.setVatNumber("IT12345678901");
        return c;
    }

    private Contracts contract(Counterparty counterparty) {
        Contracts c = new Contracts();
        c.setId(CONTRACT_ID);
        c.setCounterparty(counterparty);
        return c;
    }

    private ElectronicInvoice invoice(Contracts contract, String supplierName, String supplierVat,
                                       BigDecimal totalAmount, LocalDate invoiceDate) {
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(10L);
        invoice.setContract(contract);
        invoice.setSupplierName(supplierName);
        invoice.setSupplierVatNumber(supplierVat);
        invoice.setTotalAmount(totalAmount);
        invoice.setInvoiceDate(invoiceDate);
        invoice.setMatchStatus(InvoiceMatchStatus.UNMATCHED);
        return invoice;
    }

    private FinancialValues financialValue(long id, Contracts contract, int month, int year, double amount) {
        FinancialValues fv = new FinancialValues();
        fv.setId(id);
        fv.setContract(contract);
        fv.setMonth(month);
        fv.setYear(year);
        fv.setFinancialAmount(amount);
        return fv;
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("computeSuggestion")
    @SuppressWarnings("unused")
    class ComputeSuggestion {

        @Test
        @Order(1)
        @DisplayName("exact amount, same month → SUGGESTED with confidence 1.0")
        void suggestsExactMatch() {
            Counterparty cp = counterparty();
            Contracts contract = contract(cp);
            ElectronicInvoice invoice = invoice(contract, "Vertice Legal S.r.l.", "IT12345678901",
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.JANUARY, 10));
            FinancialValues candidate = financialValue(100L, contract, 1, 2026, 15000.0);

            when(financialValuesRepository.findByContractId(CONTRACT_ID)).thenReturn(List.of(candidate));

            invoiceMatchingService.computeSuggestion(invoice);

            assertEquals(InvoiceMatchStatus.SUGGESTED, invoice.getMatchStatus());
            assertEquals(candidate, invoice.getMatchedFinancialValue());
            assertEquals(1.0, invoice.getMatchConfidence(), 0.0001);
        }

        @Test
        @Order(2)
        @DisplayName("exact amount, one month apart → SUGGESTED with confidence 0.84")
        void suggestsWithOneMonthOffset() {
            Counterparty cp = counterparty();
            Contracts contract = contract(cp);
            ElectronicInvoice invoice = invoice(contract, "Vertice Legal S.r.l.", "IT12345678901",
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.FEBRUARY, 5));
            FinancialValues candidate = financialValue(100L, contract, 1, 2026, 15000.0);

            when(financialValuesRepository.findByContractId(CONTRACT_ID)).thenReturn(List.of(candidate));

            invoiceMatchingService.computeSuggestion(invoice);

            assertEquals(InvoiceMatchStatus.SUGGESTED, invoice.getMatchStatus());
            assertEquals(0.84, invoice.getMatchConfidence(), 0.0001);
        }

        @Test
        @Order(3)
        @DisplayName("amount far off and date far apart → below threshold → UNMATCHED")
        void unmatchedWhenScoreTooLow() {
            Counterparty cp = counterparty();
            Contracts contract = contract(cp);
            ElectronicInvoice invoice = invoice(contract, "Vertice Legal S.r.l.", "IT12345678901",
                    new BigDecimal("500.00"), LocalDate.of(2026, Month.JULY, 5));
            FinancialValues candidate = financialValue(100L, contract, 1, 2026, 15000.0);

            when(financialValuesRepository.findByContractId(CONTRACT_ID)).thenReturn(List.of(candidate));

            invoiceMatchingService.computeSuggestion(invoice);

            assertEquals(InvoiceMatchStatus.UNMATCHED, invoice.getMatchStatus());
            assertNull(invoice.getMatchedFinancialValue());
            assertNull(invoice.getMatchConfidence());
        }

        @Test
        @Order(4)
        @DisplayName("supplier VAT and name both mismatch the counterparty → COUNTERPARTY_MISMATCH, no scoring attempted")
        void flagsCounterpartyMismatch() {
            Counterparty cp = counterparty();
            Contracts contract = contract(cp);
            ElectronicInvoice invoice = invoice(contract, "Totalmente Diversa S.p.A.", "IT99999999999",
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.JANUARY, 10));

            invoiceMatchingService.computeSuggestion(invoice);

            assertEquals(InvoiceMatchStatus.COUNTERPARTY_MISMATCH, invoice.getMatchStatus());
            assertNull(invoice.getMatchedFinancialValue());
            verifyNoInteractions(financialValuesRepository);
        }

        @Test
        @Order(5)
        @DisplayName("supplier name matches loosely (S.r.l. vs Srl) even when VAT is missing on one side")
        void toleratesNameFormattingDifference() {
            Counterparty cp = counterparty();
            cp.setVatNumber(null);
            Contracts contract = contract(cp);
            ElectronicInvoice invoice = invoice(contract, "VERTICE LEGAL SRL", null,
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.JANUARY, 10));
            FinancialValues candidate = financialValue(100L, contract, 1, 2026, 15000.0);

            when(financialValuesRepository.findByContractId(CONTRACT_ID)).thenReturn(List.of(candidate));

            invoiceMatchingService.computeSuggestion(invoice);

            assertEquals(InvoiceMatchStatus.SUGGESTED, invoice.getMatchStatus());
        }

        @Test
        @Order(6)
        @DisplayName("skips a candidate already CONFIRMED to a different invoice")
        void skipsRowsConfirmedElsewhere() {
            Counterparty cp = counterparty();
            Contracts contract = contract(cp);
            ElectronicInvoice invoice = invoice(contract, "Vertice Legal S.r.l.", "IT12345678901",
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.JANUARY, 10));
            FinancialValues takenCandidate = financialValue(100L, contract, 1, 2026, 15000.0);
            FinancialValues freeCandidate = financialValue(101L, contract, 2, 2026, 15000.0);

            when(financialValuesRepository.findByContractId(CONTRACT_ID))
                    .thenReturn(List.of(takenCandidate, freeCandidate));

            ElectronicInvoice otherInvoice = new ElectronicInvoice();
            otherInvoice.setId(999L);
            when(invoiceRepository.findByMatchedFinancialValue_IdAndMatchStatus(100L, InvoiceMatchStatus.CONFIRMED))
                    .thenReturn(Optional.of(otherInvoice));
            when(invoiceRepository.findByMatchedFinancialValue_IdAndMatchStatus(101L, InvoiceMatchStatus.CONFIRMED))
                    .thenReturn(Optional.empty());

            invoiceMatchingService.computeSuggestion(invoice);

            assertEquals(freeCandidate, invoice.getMatchedFinancialValue());
        }

        @Test
        @Order(7)
        @DisplayName("no-op when the invoice is already CONFIRMED")
        void noOpWhenAlreadyConfirmed() {
            Counterparty cp = counterparty();
            Contracts contract = contract(cp);
            ElectronicInvoice invoice = invoice(contract, "Vertice Legal S.r.l.", "IT12345678901",
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.JANUARY, 10));
            invoice.setMatchStatus(InvoiceMatchStatus.CONFIRMED);

            invoiceMatchingService.computeSuggestion(invoice);

            assertEquals(InvoiceMatchStatus.CONFIRMED, invoice.getMatchStatus());
            verifyNoInteractions(financialValuesRepository);
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("confirmMatch")
    @SuppressWarnings("unused")
    class ConfirmMatch {

        @Test
        @Order(1)
        @DisplayName("confirms the already-suggested candidate when no override is given")
        void confirmsSuggested() {
            Contracts contract = contract(counterparty());
            FinancialValues candidate = financialValue(100L, contract, 1, 2026, 15000.0);
            ElectronicInvoice invoice = invoice(contract, "Vertice Legal S.r.l.", "IT12345678901",
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.JANUARY, 10));
            invoice.setMatchStatus(InvoiceMatchStatus.SUGGESTED);
            invoice.setMatchedFinancialValue(candidate);
            invoice.setMatchConfidence(1.0);

            Users user = new Users();
            user.setUsername("admin");
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(user));
            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            ElectronicInvoice result = invoiceMatchingService.confirmMatch(invoice, null, "admin");

            assertEquals(InvoiceMatchStatus.CONFIRMED, result.getMatchStatus());
            assertEquals(candidate, result.getMatchedFinancialValue());
            assertEquals(user, result.getMatchedByUser());
        }

        @Test
        @Order(2)
        @DisplayName("manual override picks a different financial value than the one suggested")
        void confirmsManualOverride() {
            Contracts contract = contract(counterparty());
            FinancialValues suggested = financialValue(100L, contract, 1, 2026, 15000.0);
            FinancialValues override = financialValue(101L, contract, 2, 2026, 15000.0);
            ElectronicInvoice invoice = invoice(contract, "Vertice Legal S.r.l.", "IT12345678901",
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.JANUARY, 10));
            invoice.setMatchStatus(InvoiceMatchStatus.SUGGESTED);
            invoice.setMatchedFinancialValue(suggested);

            when(financialValuesRepository.findById(101L)).thenReturn(Optional.of(override));
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(new Users()));
            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            ElectronicInvoice result = invoiceMatchingService.confirmMatch(invoice, 101L, "admin");

            assertEquals(override, result.getMatchedFinancialValue());
        }

        @Test
        @Order(3)
        @DisplayName("throws when there is nothing to confirm and no override is given")
        void throwsWhenNoCandidate() {
            Contracts contract = contract(counterparty());
            ElectronicInvoice invoice = invoice(contract, "Vertice Legal S.r.l.", "IT12345678901",
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.JANUARY, 10));

            assertThrows(IllegalArgumentException.class,
                    () -> invoiceMatchingService.confirmMatch(invoice, null, "admin"));
        }

        @Test
        @Order(4)
        @DisplayName("throws when the override belongs to a different contract")
        void throwsWhenOverrideBelongsToDifferentContract() {
            Contracts contract = contract(counterparty());
            Contracts otherContract = new Contracts();
            otherContract.setId(999L);
            FinancialValues foreign = financialValue(200L, otherContract, 1, 2026, 15000.0);
            ElectronicInvoice invoice = invoice(contract, "Vertice Legal S.r.l.", "IT12345678901",
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.JANUARY, 10));

            when(financialValuesRepository.findById(200L)).thenReturn(Optional.of(foreign));

            assertThrows(IllegalArgumentException.class,
                    () -> invoiceMatchingService.confirmMatch(invoice, 200L, "admin"));
        }

        @Test
        @Order(5)
        @DisplayName("throws when another invoice is already CONFIRMED against the same row")
        void throwsOnDoubleConfirmation() {
            Contracts contract = contract(counterparty());
            FinancialValues candidate = financialValue(100L, contract, 1, 2026, 15000.0);
            ElectronicInvoice invoice = invoice(contract, "Vertice Legal S.r.l.", "IT12345678901",
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.JANUARY, 10));
            invoice.setId(10L);
            invoice.setMatchedFinancialValue(candidate);

            ElectronicInvoice otherInvoice = new ElectronicInvoice();
            otherInvoice.setId(20L);
            when(invoiceRepository.findByMatchedFinancialValue_IdAndMatchStatus(100L, InvoiceMatchStatus.CONFIRMED))
                    .thenReturn(Optional.of(otherInvoice));

            assertThrows(IllegalArgumentException.class,
                    () -> invoiceMatchingService.confirmMatch(invoice, null, "admin"));
        }

        @Test
        @Order(6)
        @DisplayName("allows re-confirming the same invoice against the row it already owns")
        void allowsReconfirmingOwnRow() {
            Contracts contract = contract(counterparty());
            FinancialValues candidate = financialValue(100L, contract, 1, 2026, 15000.0);
            ElectronicInvoice invoice = invoice(contract, "Vertice Legal S.r.l.", "IT12345678901",
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.JANUARY, 10));
            invoice.setId(10L);
            invoice.setMatchedFinancialValue(candidate);

            when(invoiceRepository.findByMatchedFinancialValue_IdAndMatchStatus(100L, InvoiceMatchStatus.CONFIRMED))
                    .thenReturn(Optional.of(invoice));
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(new Users()));
            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            ElectronicInvoice result = invoiceMatchingService.confirmMatch(invoice, null, "admin");

            assertEquals(InvoiceMatchStatus.CONFIRMED, result.getMatchStatus());
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("rejectMatch")
    @SuppressWarnings("unused")
    class RejectMatch {

        @Test
        @Order(1)
        @DisplayName("clears the candidate and marks the invoice REJECTED")
        void rejectsAndClears() {
            Contracts contract = contract(counterparty());
            FinancialValues candidate = financialValue(100L, contract, 1, 2026, 15000.0);
            ElectronicInvoice invoice = invoice(contract, "Vertice Legal S.r.l.", "IT12345678901",
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.JANUARY, 10));
            invoice.setMatchStatus(InvoiceMatchStatus.SUGGESTED);
            invoice.setMatchedFinancialValue(candidate);
            invoice.setMatchConfidence(0.9);

            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            ElectronicInvoice result = invoiceMatchingService.rejectMatch(invoice);

            assertEquals(InvoiceMatchStatus.REJECTED, result.getMatchStatus());
            assertNull(result.getMatchedFinancialValue());
            assertNull(result.getMatchConfidence());
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("recomputeForContract")
    @SuppressWarnings("unused")
    class RecomputeForContract {

        @Test
        @Order(1)
        @DisplayName("only queries invoices in UNMATCHED/SUGGESTED/COUNTERPARTY_MISMATCH — never CONFIRMED/REJECTED")
        void queriesOnlyReconsiderableStatuses() {
            when(invoiceRepository.findByContractIdAndMatchStatusIn(anyLong(), any())).thenReturn(List.of());
            when(invoiceRepository.saveAll(any())).thenReturn(List.of());

            invoiceMatchingService.recomputeForContract(CONTRACT_ID);

            verify(invoiceRepository).findByContractIdAndMatchStatusIn(
                    CONTRACT_ID,
                    List.of(InvoiceMatchStatus.UNMATCHED, InvoiceMatchStatus.SUGGESTED, InvoiceMatchStatus.COUNTERPARTY_MISMATCH));
        }

        @Test
        @Order(2)
        @DisplayName("recomputes each returned invoice and saves the batch")
        void recomputesAndSaves() {
            Contracts contract = contract(counterparty());
            FinancialValues candidate = financialValue(100L, contract, 1, 2026, 15000.0);
            ElectronicInvoice invoice = invoice(contract, "Vertice Legal S.r.l.", "IT12345678901",
                    new BigDecimal("15000.00"), LocalDate.of(2026, Month.JANUARY, 10));

            when(invoiceRepository.findByContractIdAndMatchStatusIn(anyLong(), any())).thenReturn(List.of(invoice));
            when(financialValuesRepository.findByContractId(CONTRACT_ID)).thenReturn(List.of(candidate));
            when(invoiceRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            List<ElectronicInvoice> result = invoiceMatchingService.recomputeForContract(CONTRACT_ID);

            assertEquals(1, result.size());
            assertEquals(InvoiceMatchStatus.SUGGESTED, result.get(0).getMatchStatus());
        }
    }
}
