/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.UUID;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.util.PageLimits;
import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.dto.ContractStatsResponse;
import com.donatodev.bcm_backend.dto.ContractsByAreaDTO;
import com.donatodev.bcm_backend.dto.ContractsTimelineDTO;
import com.donatodev.bcm_backend.dto.OrganizationInvoicingSummaryDTO;
import com.donatodev.bcm_backend.dto.TopManagerDTO;
import com.donatodev.bcm_backend.dto.FinancialGenerationResultDTO;
import com.donatodev.bcm_backend.entity.BillingFrequency;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.FinancialTypes;
import com.donatodev.bcm_backend.entity.WorkflowStage;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.BusinessAreaNotFoundException;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.CounterpartyNotFoundException;
import com.donatodev.bcm_backend.exception.FinancialTypeNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.ContractMapper;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractHistoryRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Service class managing business logic for contracts. Includes methods to
 * retrieve, search (paged), update, delete and assign managers, with role-based
 * access control (ADMIN vs MANAGER).
 */
@Service
public class ContractService {

    private static final Logger logger = LoggerFactory.getLogger(ContractService.class);

    private static final String CRLF_REGEX = "[\r\n]";
    private static final String MSG_USER_NOT_FOUND = "Utente non trovato";
    private static final String MSG_NO_AUTH_USER = "Nessun utente autenticato";
    private static final String MSG_CONTRACT_NOT_FOUND_PREFIX = "Contratto non trovato: ";
    private static final String ROLE_ADMIN = "ADMIN";

    private final ContractsRepository contractsRepository;
    private final ContractMapper contractMapper;
    private final UsersRepository usersRepository;
    private final ManagerService managerService;
    private final ContractHistoryRepository contractHistoryRepository;
    private final BusinessAreasRepository businessAreasRepository;
    private final CounterpartiesRepository counterpartiesRepository;
    private final FinancialTypesRepository financialTypesRepository;
    private final ContractFinancialGenerationService contractFinancialGenerationService;
    private final ElectronicInvoiceRepository invoiceRepository;
    private final FinancialValuesRepository financialValuesRepository;
    private final TierLimitService tierLimitService;
    private final ContractAccessGuard contractAccessGuard;

    public ContractService(
            ContractsRepository contractsRepository,
            ContractMapper contractMapper,
            UsersRepository usersRepository,
            ManagerService managerService,
            ContractHistoryRepository contractHistoryRepository,
            BusinessAreasRepository businessAreasRepository,
            CounterpartiesRepository counterpartiesRepository,
            FinancialTypesRepository financialTypesRepository,
            ContractFinancialGenerationService contractFinancialGenerationService,
            ElectronicInvoiceRepository invoiceRepository,
            FinancialValuesRepository financialValuesRepository,
            TierLimitService tierLimitService,
            ContractAccessGuard contractAccessGuard
    ) {
        this.contractsRepository = contractsRepository;
        this.contractMapper = contractMapper;
        this.usersRepository = usersRepository;
        this.managerService = managerService;
        this.contractHistoryRepository = contractHistoryRepository;
        this.businessAreasRepository = businessAreasRepository;
        this.counterpartiesRepository = counterpartiesRepository;
        this.financialTypesRepository = financialTypesRepository;
        this.contractFinancialGenerationService = contractFinancialGenerationService;
        this.invoiceRepository = invoiceRepository;
        this.financialValuesRepository = financialValuesRepository;
        this.tierLimitService = tierLimitService;
        this.contractAccessGuard = contractAccessGuard;
    }

    // The update path doesn't go through ContractMapper, so these mirror its
    // tenant-scoped resolvers: another organization's counterparty, type or
    // area would otherwise show up (name, VAT number) on this tenant's contract.
    private Counterparty resolveCounterpartyForUpdate(UUID counterpartyId) {
        UUID orgId = TenantContext.require();
        return counterpartiesRepository.findByIdAndOrganizationId(counterpartyId, orgId)
                .orElseThrow(() -> new CounterpartyNotFoundException("Controparte non trovata: " + counterpartyId));
    }

    private FinancialTypes resolveFinancialTypeForUpdate(UUID financialTypeId) {
        UUID orgId = TenantContext.require();
        return financialTypesRepository.findByIdAndOrganizationId(financialTypeId, orgId)
                .orElseThrow(() -> new FinancialTypeNotFoundException("Tipo finanziario non trovato: " + financialTypeId));
    }

    /**
     * Runs financial-terms generation for a contract that has all three terms
     * set, swallowing any failure so it never blocks the contract save itself
     * (same "degrade without breaking" style used elsewhere in this codebase
     * for ML/agent-insight side effects that aren't the primary operation).
     */
    private void generateFinancialValuesIfApplicable(Contracts contract) {
        if (!contractFinancialGenerationService.hasFinancialTerms(contract)) {
            return;
        }
        try {
            contractFinancialGenerationService.generate(contract);
        } catch (RuntimeException e) {
            String safeMessage = e.getMessage() == null ? null : e.getMessage().replaceAll(CRLF_REGEX, "_");
            logger.warn("Financial value generation failed for contract {}: {}", contract.getId(), safeMessage);
        }
    }

    /**
     * Retrieves all contracts accessible by the authenticated user. Admins: all
     * contracts; Managers: only their contracts.
     */
    public List<ContractDTO> getAllContracts() {
        AuthCtx auth = getAuthCtx();
        String safeRole = auth.role().replaceAll(CRLF_REGEX, "_");
        logger.info("Authenticated user role: {}", safeRole);

        if (ROLE_ADMIN.equals(Normalizer.normalize(auth.role(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT))) {
            UUID orgId = TenantContext.require();
            List<Contracts> contracts = contractsRepository.findByOrganization_Id(orgId);
            return contracts.stream()
                    .map(contractMapper::toDTO)
                    .toList();
        }
        if (auth.managerId() == null) return List.of();
        return contractsRepository.findByManagerId(auth.managerId())
                .stream()
                .map(contractMapper::toDTO)
                .toList();
    }

    /**
     * Retrieves a contract by its ID. MANAGERs can only access contracts assigned to them.
     */
    public ContractDTO getContractById(UUID id) {
        Contracts contract = findContractInScope(id)
                .orElseThrow(() -> new ContractNotFoundException("Contratto ID " + id + " non trovato"));

        AuthCtx auth = getAuthCtx();
        if (!ROLE_ADMIN.equals(Normalizer.normalize(auth.role(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT))) {
            UUID contractManagerId = contract.getManager() != null ? contract.getManager().getId() : null;
            if (auth.managerId() == null || !auth.managerId().equals(contractManagerId)) {
                throw new AccessDeniedException("non autorizzato ad accedere al contratto: " + id);
            }
        }

        return contractMapper.toDTO(contract);
    }

    /**
     * Finds a contract by ID, scoped to the current tenant when
     * {@link TenantContext} carries an organization ID.
     */
    private Optional<Contracts> findContractInScope(UUID id) {
        UUID orgId = TenantContext.require();
        return contractsRepository.findByIdAndOrganization_Id(id, orgId);
    }

    /**
     * Creates a new contract.
     */
    /**
     * A contract cannot end before it starts (OWASP Input Validation Cheat
     * Sheet, business rules): checked here so the form, a template and the
     * CSV import, which all come through this service, are refused alike.
     */
    private static void requireEndNotBeforeStart(ContractDTO contractDTO) {
        if (contractDTO != null && contractDTO.startDate() != null && contractDTO.endDate() != null
                && contractDTO.endDate().isBefore(contractDTO.startDate())) {
            throw new IllegalArgumentException("La data di fine non può precedere la data di inizio");
        }
    }

    public ContractDTO createContract(ContractDTO contractDTO) {
        requireEndNotBeforeStart(contractDTO);
        Contracts contract = contractMapper.toEntity(contractDTO);
        if (contract == null) {
            throw new IllegalArgumentException("Dati contratto obbligatori");
        }
        UUID orgId = TenantContext.require();
        tierLimitService.enforceContractCreationLimit(orgId);
        // Creation must never merge an existing row selected by a client-supplied ID.
        contract.setId(null);
        Organization org = new Organization();
        org.setId(orgId);
        contract.setOrganization(org);
        contract = contractsRepository.save(contract);
        generateFinancialValuesIfApplicable(contract);
        return contractMapper.toDTO(contract);
    }

    /**
     * Updates an existing contract. If the status changes, a history record is
     * automatically created.
     */
    public ContractDTO updateContract(UUID id, ContractDTO contractDTO) {
        requireEndNotBeforeStart(contractDTO);
        Contracts contract = findContractInScope(id)
                .orElseThrow(() -> new ContractNotFoundException("Contratto non trovato"));

        // Save previous status for history tracking
        ContractStatus previousStatus = contract.getStatus();

        if (contract.getWorkflowStage() == WorkflowStage.IN_REVIEW && contractDTO.status() != previousStatus) {
            throw new IllegalArgumentException(
                    "Impossibile cambiare stato mentre il contratto è in revisione; approvarlo o respingerlo");
        }

        // Snapshot everything that affects financial-value generation, before
        // any of it is overwritten below, so we can tell afterwards whether
        // regeneration is actually warranted (see generateFinancialValuesIfApplicable
        // call below) instead of re-running it on every unrelated edit.
        UUID previousFinancialTypeId = contract.getFinancialType() != null ? contract.getFinancialType().getId() : null;
        Double previousAnnualValue = contract.getAnnualValue();
        BillingFrequency previousBillingFrequency = contract.getBillingFrequency();
        UUID previousAreaId = contract.getBusinessArea() != null ? contract.getBusinessArea().getId() : null;
        LocalDate previousStartDate = contract.getStartDate();
        LocalDate previousEndDate = contract.getEndDate();

        contract.setCounterparty(resolveCounterpartyForUpdate(contractDTO.counterpartyId()));
        contract.setContractNumber(contractDTO.contractNumber());
        contract.setWbsCode(contractDTO.wbsCode());
        contract.setProjectName(contractDTO.projectName());
        contract.setStatus(contractDTO.status());
        contract.setStartDate(contractDTO.startDate());
        contract.setEndDate(contractDTO.endDate());
        contractMapper.applyRenewalTerms(contract, contractDTO);

        if (contractDTO.areaId() != null) {
            UUID orgId = TenantContext.require();
            BusinessAreas area = businessAreasRepository.findByIdAndOrganizationId(contractDTO.areaId(), orgId)
                    .orElseThrow(() -> new BusinessAreaNotFoundException("Area aziendale non trovata: " + contractDTO.areaId()));
            contract.setBusinessArea(area);
        }
        contract.setManager(contractDTO.managerId() != null
                ? managerService.getManagerEntity(contractDTO.managerId())
                : null);
        contract.setFinancialType(contractDTO.financialTypeId() != null
                ? resolveFinancialTypeForUpdate(contractDTO.financialTypeId())
                : null);
        contract.setAnnualValue(contractDTO.annualValue());
        contract.setBillingFrequency(contractDTO.billingFrequency());

        // Derive workflow stage the same way contract creation does: entering
        // DRAFT for the first time starts the workflow; leaving DRAFT for any
        // other status means the approval workflow no longer applies.
        updateWorkflowStage(contract, previousStatus, contractDTO.status());

        contract = contractsRepository.save(contract);

        // Only regenerate when something that actually affects generation
        // changed — otherwise every unrelated edit (e.g. projectName) would
        // needlessly re-run it and exercise the manual-wins skip logic for
        // nothing. Same "diff before side-effecting" idiom as the status-change
        // check below.
        boolean financialTermsRelevantFieldsChanged =
                !Objects.equals(previousFinancialTypeId, contractDTO.financialTypeId())
                || !Objects.equals(previousAnnualValue, contractDTO.annualValue())
                || previousBillingFrequency != contractDTO.billingFrequency()
                || !Objects.equals(previousAreaId, contractDTO.areaId())
                || !Objects.equals(previousStartDate, contractDTO.startDate())
                || !Objects.equals(previousEndDate, contractDTO.endDate());
        if (financialTermsRelevantFieldsChanged) {
            generateFinancialValuesIfApplicable(contract);
        }

        // Create history record if status changed
        if (previousStatus != contractDTO.status()) {
            String username = getAuthenticatedUsername();
            Users user = usersRepository.findByUsername(username)
                    .orElseThrow(() -> new UserNotFoundException(MSG_USER_NOT_FOUND));

            ContractHistory history = new ContractHistory();
            history.setContract(contract);
            history.setModifiedBy(user);
            history.setModificationDate(LocalDateTime.now(ZoneId.systemDefault()));
            history.setPreviousStatus(previousStatus);
            history.setNewStatus(contract.getStatus());

            contractHistoryRepository.save(history);
        }

        return contractMapper.toDTO(contract);
    }

    /**
     * Explicit "regenerate" trigger for the manual admin-triggered endpoint —
     * recomputes the contract's financial values from its current terms right
     * now, regardless of whether anything changed since the last run. Useful
     * after editing something that affects generation (e.g. business area)
     * without touching the terms themselves. Unlike the automatic post-save
     * hook, failures here are NOT swallowed — this is a user-initiated action
     * and its result (including a thrown exception) should be visible to the caller.
     *
     * @throws IllegalArgumentException if the contract has no financial terms set
     */
    public FinancialGenerationResultDTO regenerateFinancialValues(UUID contractId) {
        Contracts contract = findContractInScope(contractId)
                .orElseThrow(() -> new ContractNotFoundException(MSG_CONTRACT_NOT_FOUND_PREFIX + contractId));
        if (!contractFinancialGenerationService.hasFinancialTerms(contract)) {
            throw new IllegalArgumentException("Il contratto non ha termini finanziari impostati");
        }
        return contractFinancialGenerationService.generate(contract);
    }

    /**
     * Deletes a contract by its ID.
     */
    public void deleteContract(UUID id) {
        Contracts contract = findContractInScope(id)
                .orElseThrow(() -> new ContractNotFoundException(MSG_CONTRACT_NOT_FOUND_PREFIX + id));
        contractsRepository.delete(contract);
    }

    /**
     * Dashboard KPIs. Admins: organization-wide; Managers: only their
     * assigned contracts.
     */
    public ContractStatsResponse getContractStats() {
        AuthCtx auth = getAuthCtx();
        LocalDate thirtyDaysFromNow = LocalDate.now(ZoneId.systemDefault()).plusDays(30);
        int total;
        int active;
        int expiring;
        int expired;
        int draft;
        if (!ROLE_ADMIN.equals(Normalizer.normalize(auth.role(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT))) {
            if (auth.managerId() == null) {
                return new ContractStatsResponse(0, 0, 0, 0, 0);
            }
            total    = contractsRepository.countAllContractsByManager(auth.managerId());
            active   = contractsRepository.countActiveContractsByManager(auth.managerId());
            expiring = contractsRepository.countExpiringContractsByManager(thirtyDaysFromNow, auth.managerId());
            expired  = contractsRepository.countExpiredContractsByManager(auth.managerId());
            draft    = contractsRepository.countDraftContractsByManager(auth.managerId());
            return new ContractStatsResponse(total, active, expiring, expired, draft);
        }
        UUID orgId = TenantContext.require();
        total    = contractsRepository.countAllContractsByOrg(orgId);
        active   = contractsRepository.countActiveContractsByOrg(orgId);
        expiring = contractsRepository.countExpiringContractsByOrg(thirtyDaysFromNow, orgId);
        expired  = contractsRepository.countExpiredContractsByOrg(orgId);
        draft    = contractsRepository.countDraftContractsByOrg(orgId);
        return new ContractStatsResponse(total, active, expiring, expired, draft);
    }

    /**
     * Expected value (this year's {@code FinancialValues}) vs. what has
     * actually been confirmed-invoiced. Admins: organization-wide; Managers:
     * only their assigned contracts. Only {@code CONFIRMED} invoice matches
     * count as invoiced.
     */
    public OrganizationInvoicingSummaryDTO getInvoicingSummary() {
        AuthCtx auth = getAuthCtx();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        int year = today.getYear();
        int month = today.getMonthValue();
        double expectedYtd;
        double invoicedYtd;

        if (!ROLE_ADMIN.equals(Normalizer.normalize(auth.role(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT))) {
            if (auth.managerId() == null) {
                return new OrganizationInvoicingSummaryDTO(year, 0, 0, 0, 0);
            }
            expectedYtd = financialValuesRepository.sumCostByManagerUpToMonth(auth.managerId(), year, month);
            invoicedYtd = invoiceRepository.sumConfirmedInvoicedAmountByManagerIdAndYear(auth.managerId(), year).doubleValue();
        } else {
            UUID orgId = TenantContext.require();
            expectedYtd = financialValuesRepository.sumCostByOrgUpToMonth(orgId, year, month);
            invoicedYtd = invoiceRepository.sumConfirmedInvoicedAmountByOrgIdAndYear(orgId, year).doubleValue();
        }

        double variance = invoicedYtd - expectedYtd;
        double variancePercent = expectedYtd == 0 ? 0.0 : (variance / expectedYtd) * 100.0;
        return new OrganizationInvoicingSummaryDTO(year, expectedYtd, invoicedYtd, variance, variancePercent);
    }

    /**
     * Retrieves all ACTIVE contracts that will expire within the specified
     * number of days. Admins: organization-wide; Managers: only their
     * assigned contracts.
     *
     * @param days the number of days in the future to check for expiring
     * contracts
     * @return a list of {@link ContractDTO} representing expiring contracts,
     * ordered by end date
     */
    public List<ContractDTO> getExpiringContracts(int days) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate futureDate = today.plusDays(days);
        AuthCtx auth = getAuthCtx();

        List<Contracts> expiring;
        if (!ROLE_ADMIN.equals(Normalizer.normalize(auth.role(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT))) {
            expiring = auth.managerId() == null
                    ? List.of()
                    : contractsRepository.findExpiringContractsByManager(today, futureDate, auth.managerId());
        } else {
            UUID orgId = TenantContext.require();
            expiring = contractsRepository.findExpiringContractsByOrg(today, futureDate, orgId);
        }

        return expiring.stream().map(contractMapper::toDTO).toList();
    }

    /**
     * Paged search with optional term (q) and status filter.
     */
    public Page<ContractDTO> searchPaged(String q, ContractStatus status, int page, int size) {
        Pageable pageable = PageLimits.of(page, size, Sort.by("contractNumber").ascending());

        String term = (q == null) ? null : q.trim();
        boolean hasTerm = term != null && !term.isBlank();

        AuthCtx auth = getAuthCtx();

        Page<Contracts> pageResult = ROLE_ADMIN.equals(Normalizer.normalize(auth.role(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT))
                ? searchPagedAdminForOrg(TenantContext.require(), status, hasTerm, term, pageable)
                : searchPagedManager(auth.managerId(), status, hasTerm, term, pageable);

        return pageResult.map(contractMapper::toDTO);
    }

    private Page<Contracts> searchPagedAdminForOrg(UUID orgId, ContractStatus status, boolean hasTerm, String term, Pageable pageable) {
        if (status != null && hasTerm) {
            return contractsRepository.findByOrgAndStatusAndTerm(orgId, status, term, pageable);
        } else if (status != null) {
            return contractsRepository.findByStatusAndOrganization_Id(status, orgId, pageable);
        } else if (hasTerm) {
            return contractsRepository.findByOrgAndTerm(orgId, term, pageable);
        } else {
            return contractsRepository.findByOrganization_Id(orgId, pageable);
        }
    }

    private Page<Contracts> searchPagedManager(UUID managerId, ContractStatus status, boolean hasTerm, String term, Pageable pageable) {
        if (managerId == null) {
            return Page.empty(pageable);
        } else if (status != null && hasTerm) {
            return contractsRepository
                    .findByManagerIdAndStatusAndContractNumberContainingIgnoreCaseOrManagerIdAndStatusAndCounterpartyNameContainingIgnoreCase(
                            managerId, status, term, managerId, status, term, pageable);
        } else if (status != null) {
            return contractsRepository.findByManagerIdAndStatus(managerId, status, pageable);
        } else if (hasTerm) {
            return contractsRepository
                    .findByManagerIdAndContractNumberContainingIgnoreCaseOrManagerIdAndCounterpartyNameContainingIgnoreCase(
                            managerId, term, managerId, term, pageable);
        } else {
            return contractsRepository.findByManagerId(managerId, pageable);
        }
    }

    /**
     * Get contract distribution by business area. Admins: organization-wide;
     * Managers: only their assigned contracts.
     *
     * @return list of business areas with contract counts
     */
    public List<ContractsByAreaDTO> getContractsByArea() {
        AuthCtx auth = getAuthCtx();
        if (!ROLE_ADMIN.equals(Normalizer.normalize(auth.role(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT))) {
            return auth.managerId() == null
                    ? List.of()
                    : contractsRepository.countContractsByAreaAndManager(auth.managerId());
        }
        UUID orgId = TenantContext.require();
        return contractsRepository.countContractsByAreaAndOrg(orgId);
    }

    /**
     * Get contracts timeline (contracts started, by start_date, per month for
     * the last 12 calendar months). Always returns exactly 12 chronologically
     * ordered entries, zero-filled for months with no contracts started, so
     * that bulk imports (many contracts sharing one created_at but spread
     * across start_date) don't collapse into a single data point. Admins:
     * organization-wide; Managers: only their assigned contracts.
     *
     * @return chronologically ordered list of 12 months with contract counts
     */
    public List<ContractsTimelineDTO> getContractsTimeline() {
        YearMonth currentMonth = YearMonth.now(ZoneId.systemDefault());
        YearMonth startMonth = currentMonth.minusMonths(11);
        LocalDate windowStart = startMonth.atDay(1);

        AuthCtx auth = getAuthCtx();
        List<Object[]> results;
        if (!ROLE_ADMIN.equals(Normalizer.normalize(auth.role(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT))) {
            results = auth.managerId() == null
                    ? List.of()
                    : contractsRepository.countContractsByMonthAndManager(windowStart, auth.managerId());
        } else {
            UUID orgId = TenantContext.require();
            results = contractsRepository.countContractsByMonthAndOrg(windowStart, orgId);
        }

        Map<YearMonth, Long> countsByMonth = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            countsByMonth.put(startMonth.plusMonths(i), 0L);
        }
        for (Object[] row : results) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            long count = ((Number) row[2]).longValue();
            countsByMonth.computeIfPresent(YearMonth.of(year, month), (ym, existing) -> count);
        }

        return countsByMonth.entrySet().stream()
                .map(entry -> new ContractsTimelineDTO(entry.getKey().toString(), entry.getValue()))
                .toList();
    }

    /**
     * Get top 5 managers by number of assigned contracts. Admins: ranking
     * across the organization; Managers: a single-entry list with just their
     * own count (there is no "colleagues ranking" to show them).
     *
     * @return list of top managers with contract counts
     */
    public List<TopManagerDTO> getTopManagers() {
        AuthCtx auth = getAuthCtx();
        if (!ROLE_ADMIN.equals(Normalizer.normalize(auth.role(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT))) {
            return auth.managerId() == null
                    ? List.of()
                    : contractsRepository.findTopManagerForManager(auth.managerId());
        }
        Pageable topFive = PageRequest.of(0, 5);
        UUID orgId = TenantContext.require();
        return contractsRepository.findTopManagersByOrg(topFive, orgId);
    }

    // -----------------------
    // Helpers Auth
    // -----------------------
    /**
     * Current username from SecurityContext.
     */
    private String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null) {
            throw new UserNotFoundException(MSG_NO_AUTH_USER);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return String.valueOf(principal);
    }

    /**
     * Returns role ("ADMIN"/"MANAGER") and (if MANAGER) the managerId of the
     * current user.
     */
    private AuthCtx getAuthCtx() {
        String username = getAuthenticatedUsername();
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(MSG_USER_NOT_FOUND));
        String role = user.getRole().getRole();

        UUID managerId = null;
        if (!ROLE_ADMIN.equals(role) && user.getManager() != null) {
            managerId = user.getManager().getId();
        }

        return new AuthCtx(role, managerId);
    }

    private static void updateWorkflowStage(Contracts contract, ContractStatus previousStatus, ContractStatus newStatus) {
        if (newStatus != ContractStatus.DRAFT) {
            contract.setWorkflowStage(null);
        } else if (previousStatus != ContractStatus.DRAFT) {
            contract.setWorkflowStage(WorkflowStage.DRAFT);
        }
    }

    private record AuthCtx(String role, UUID managerId) {

    }
}
