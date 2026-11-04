package com.donatodev.bcm_backend.mapper;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;

/**
 * Tacit renewal terms: the last day to give notice is derived from the end
 * date and the notice period, only when the contract states both, and the
 * terms are never kept for a contract that doesn't renew tacitly.
 */
@DisplayName("Contract tacit renewal terms")
class ContractRenewalTermsTest {

    private final ContractMapper mapper = new ContractMapper(
            Mockito.mock(BusinessAreasRepository.class),
            Mockito.mock(ManagersRepository.class),
            Mockito.mock(CounterpartiesRepository.class),
            Mockito.mock(FinancialTypesRepository.class),
            Mockito.mock(CounterpartyMapper.class));

    private static Contracts contract(Boolean autoRenew, Integer noticeDays, Integer renewalMonths, LocalDate endDate) {
        return Contracts.builder()
                .id(1L).contractNumber("MAN-1").status(ContractStatus.ACTIVE)
                .startDate(endDate.minusYears(1)).endDate(endDate)
                .autoRenew(autoRenew).noticeDays(noticeDays).renewalMonths(renewalMonths)
                .build();
    }

    @Test
    @DisplayName("31 December with 90 days of notice: the last day to give notice is 2 October")
    void deadlineIsEndDateMinusNotice() {
        Contracts c = contract(true, 90, 12, LocalDate.of(2026, Month.DECEMBER, 31));

        assertEquals(LocalDate.of(2026, Month.OCTOBER, 2), c.noticeDeadline());
        assertTrue(c.renewsTacitly());
    }

    @ParameterizedTest(name = "autoRenew={0}, noticeDays={1}: no deadline")
    @CsvSource({ ",", "false, 90", "true," })
    @DisplayName("Has no deadline unless it renews tacitly with a stated notice period")
    void noDeadlineWithoutBothTerms(Boolean autoRenew, Integer noticeDays) {
        assertNull(contract(autoRenew, noticeDays, 12, LocalDate.of(2026, Month.DECEMBER, 31)).noticeDeadline());
    }

    @Test
    @DisplayName("Can't be renewed automatically when the renewal length isn't stated")
    void doesNotRenewWithoutLength() {
        assertFalse(contract(true, 90, null, LocalDate.of(2026, Month.DECEMBER, 31)).renewsTacitly());
    }

    @Test
    @DisplayName("Exposes the deadline and the days left to it in the DTO")
    void dtoCarriesTheDeadline() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        ContractDTO dto = mapper.toDTO(contract(true, 90, 12, today.plusDays(120)));

        assertEquals(today.plusDays(30), dto.noticeDeadline());
        assertEquals(30, dto.daysUntilNoticeDeadline());
        assertEquals(true, dto.autoRenew());
        assertEquals(90, dto.noticeDays());
        assertEquals(12, dto.renewalMonths());
    }

    @Test
    @DisplayName("Counts no days to the deadline for a contract that isn't active")
    void noCountdownWhenNotActive() {
        Contracts c = contract(true, 90, 12, LocalDate.now(ZoneId.systemDefault()).plusDays(120));
        c.setStatus(ContractStatus.CANCELLED);

        ContractDTO dto = mapper.toDTO(c);

        assertEquals(c.noticeDeadline(), dto.noticeDeadline());
        assertNull(dto.daysUntilNoticeDeadline());
    }

    @Test
    @DisplayName("Keeps existing contracts as 'not stated': all terms null, no deadline")
    void existingContractsStayNotStated() {
        ContractDTO dto = mapper.toDTO(contract(null, null, null, LocalDate.of(2026, Month.DECEMBER, 31)));

        assertNull(dto.autoRenew());
        assertNull(dto.noticeDeadline());
        assertNull(dto.daysUntilNoticeDeadline());
    }

    @Test
    @DisplayName("Clears notice and renewal length when the contract doesn't renew tacitly")
    void clearsTermsThatDontApply() {
        Contracts c = contract(true, 90, 12, LocalDate.of(2026, Month.DECEMBER, 31));
        ContractDTO update = new ContractDTO(1L, 1L, null, "MAN-1", null, null, ContractStatus.ACTIVE,
                c.getStartDate(), c.getEndDate(), 1L, null, null, null, null, null, null, null, null, null,
                false, 90, 12, null, null);

        mapper.applyRenewalTerms(c, update);

        assertEquals(false, c.getAutoRenew());
        assertNull(c.getNoticeDays());
        assertNull(c.getRenewalMonths());
    }

    @Test
    @DisplayName("Ignores the read-only deadline sent by a client: it is always recomputed")
    void deadlineFromClientIsIgnored() {
        Contracts c = contract(null, null, null, LocalDate.of(2026, Month.DECEMBER, 31));
        ContractDTO update = new ContractDTO(1L, 1L, null, "MAN-1", null, null, ContractStatus.ACTIVE,
                c.getStartDate(), c.getEndDate(), 1L, null, null, null, null, null, null, null, null, null,
                true, 30, 12, LocalDate.of(2020, Month.JANUARY, 1), 999);

        mapper.applyRenewalTerms(c, update);

        assertEquals(LocalDate.of(2026, Month.DECEMBER, 1), c.noticeDeadline());
    }
}
