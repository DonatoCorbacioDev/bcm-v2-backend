package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.dto.ContractStatsResponse;
import com.donatodev.bcm_backend.dto.ContractsByAreaDTO;
import com.donatodev.bcm_backend.dto.ContractsTimelineDTO;
import com.donatodev.bcm_backend.dto.TopManagerDTO;
import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.ManagerNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.ContractMapper;
import com.donatodev.bcm_backend.repository.ContractHistoryRepository;
import com.donatodev.bcm_backend.repository.ContractManagerRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Service class managing business logic for contracts. Includes methods to
 * retrieve, search (paged), update, delete and assign managers, with role-based
 * access control (ADMIN vs MANAGER).
 */
@Service
public class ContractService {

    private static final Logger logger = LoggerFactory.getLogger(ContractService.class);

    private static final String MSG_USER_NOT_FOUND = "User not found";
    private static final String MSG_NO_AUTH_USER = "No authenticated user";
    private static final String MSG_CONTRACT_NOT_FOUND_PREFIX = "Contract not found: ";
    private static final String ROLE_ADMIN = "ADMIN";

    private final ContractsRepository contractsRepository;
    private final ContractMapper contractMapper;
    private final UsersRepository usersRepository;
    private final ManagerService managerService;
    private final ContractManagerRepository contractManagerRepository;
    private final ContractHistoryRepository contractHistoryRepository;

    public ContractService(
            ContractsRepository contractsRepository,
            ContractMapper contractMapper,
            UsersRepository usersRepository,
            ManagerService managerService,
            ContractManagerRepository contractManagerRepository,
            ContractHistoryRepository contractHistoryRepository
    ) {
        this.contractsRepository = contractsRepository;
        this.contractMapper = contractMapper;
        this.usersRepository = usersRepository;
        this.managerService = managerService;
        this.contractManagerRepository = contractManagerRepository;
        this.contractHistoryRepository = contractHistoryRepository;
    }

    /**
     * Retrieves all contracts accessible by the authenticated user. Admins: all
     * contracts; Managers: only their contracts.
     */
    public List<ContractDTO> getAllContracts() {
        String username = getAuthenticatedUsername();
        logger.info("Authenticated user: {}", username);

        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(MSG_USER_NOT_FOUND));

        boolean isAdmin = ROLE_ADMIN.equals(user.getRole().getRole());
        if (isAdmin) {
            return contractsRepository.findAll()
                    .stream()
                    .map(contractMapper::toDTO)
                    .toList();
        } else {
            Long managerId = user.getManager().getId();
            return contractsRepository.findByManagerId(managerId)
                    .stream()
                    .map(contractMapper::toDTO)
                    .toList();
        }
    }

    /**
     * Retrieves a contract by its ID.
     */
    public ContractDTO getContractById(Long id) {
        return contractsRepository.findById(id)
                .map(contractMapper::toDTO)
                .orElseThrow(() -> new ContractNotFoundException("Contract ID " + id + " not found"));
    }

    /**
     * Retrieves contracts filtered by status accessible by the authenticated
     * user.
     */
    public List<ContractDTO> getContractsByStatus(ContractStatus status) {
        String username = getAuthenticatedUsername();
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(MSG_USER_NOT_FOUND));

        boolean isAdmin = ROLE_ADMIN.equals(user.getRole().getRole());
        if (isAdmin) {
            return contractsRepository.findByStatus(status)
                    .stream()
                    .map(contractMapper::toDTO)
                    .toList();
        } else {
            Long managerId = user.getManager().getId();
            return contractsRepository.findByManagerIdAndStatus(managerId, status)
                    .stream()
                    .map(contractMapper::toDTO)
                    .toList();
        }
    }

    /**
     * Creates a new contract.
     */
    public ContractDTO createContract(ContractDTO contractDTO) {
        Contracts contract = contractMapper.toEntity(contractDTO);
        contract = contractsRepository.save(contract);
        return contractMapper.toDTO(contract);
    }

    /**
     * Updates an existing contract. If the status changes, a history record is
     * automatically created.
     */
    public ContractDTO updateContract(Long id, ContractDTO contractDTO) {
        Contracts contract = contractsRepository.findById(id)
                .orElseThrow(() -> new ContractNotFoundException("Contract not found"));

        // Save previous status for history tracking
        ContractStatus previousStatus = contract.getStatus();

        contract.setCustomerName(contractDTO.customerName());
        contract.setContractNumber(contractDTO.contractNumber());
        contract.setWbsCode(contractDTO.wbsCode());
        contract.setProjectName(contractDTO.projectName());
        contract.setStatus(contractDTO.status());
        contract.setStartDate(contractDTO.startDate());
        contract.setEndDate(contractDTO.endDate());

        contract = contractsRepository.save(contract);

        // Create history record if status changed
        if (previousStatus != contractDTO.status()) {
            String username = getAuthenticatedUsername();
            Users user = usersRepository.findByUsername(username)
                    .orElseThrow(() -> new UserNotFoundException(MSG_USER_NOT_FOUND));

            ContractHistory history = new ContractHistory();
            history.setContract(contract);
            history.setModifiedBy(user);
            history.setModificationDate(LocalDateTime.now());
            history.setPreviousStatus(previousStatus);
            history.setNewStatus(contract.getStatus());

            contractHistoryRepository.save(history);
        }

        return contractMapper.toDTO(contract);
    }

    /**
     * Deletes a contract by its ID.
     */
    public void deleteContract(Long id) {
        contractsRepository.deleteById(id);
    }

    /**
     * Dashboard KPIs.
     */
    public ContractStatsResponse getContractStats() {
        int total = contractsRepository.countAllContracts();
        int active = contractsRepository.countActiveContracts();
        LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
        int expiring = contractsRepository.countExpiringContracts(thirtyDaysFromNow);
        int expired = contractsRepository.countExpiredContracts();
        return new ContractStatsResponse(total, active, expiring, expired);
    }

    /**
     * Retrieves all ACTIVE contracts that will expire within the specified
     * number of days.
     *
     * @param days the number of days in the future to check for expiring
     * contracts
     * @return a list of {@link ContractDTO} representing expiring contracts,
     * ordered by end date
     */
    public List<ContractDTO> getExpiringContracts(int days) {
        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(days);

        List<Contracts> expiring = contractsRepository.findExpiringContracts(today, futureDate);

        return expiring.stream()
                .map(contractMapper::toDTO)
                .toList();
    }

    /**
     * Paged search with optional term (q) and status filter.
     */
    public Page<ContractDTO> searchPaged(String q, ContractStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("contractNumber").ascending());

        String term = (q == null) ? null : q.trim();
        boolean hasTerm = term != null && !term.isBlank();

        AuthCtx auth = getAuthCtx();

        Page<Contracts> pageResult = ROLE_ADMIN.equalsIgnoreCase(auth.role())
                ? searchPagedAdmin(status, hasTerm, term, pageable)
                : searchPagedManager(auth.managerId(), status, hasTerm, term, pageable);

        return pageResult.map(contractMapper::toDTO);
    }

    private Page<Contracts> searchPagedAdmin(ContractStatus status, boolean hasTerm, String term, Pageable pageable) {
        if (status != null && hasTerm) {
            // contractNumber OR customerName con stesso status
            return contractsRepository
                    .findByStatusAndContractNumberContainingIgnoreCaseOrStatusAndCustomerNameContainingIgnoreCase(
                            status, term, status, term, pageable);
        } else if (status != null) {
            return contractsRepository.findByStatus(status, pageable);
        } else if (hasTerm) {
            return contractsRepository
                    .findByContractNumberContainingIgnoreCaseOrCustomerNameContainingIgnoreCase(
                            term, term, pageable);
        } else {
            return contractsRepository.findAllBy(pageable);
        }
    }

    private Page<Contracts> searchPagedManager(Long managerId, ContractStatus status, boolean hasTerm, String term, Pageable pageable) {
        if (managerId == null) {
            return Page.empty(pageable);
        } else if (status != null && hasTerm) {
            return contractsRepository
                    .findByManagerIdAndStatusAndContractNumberContainingIgnoreCaseOrManagerIdAndStatusAndCustomerNameContainingIgnoreCase(
                            managerId, status, term, managerId, status, term, pageable);
        } else if (status != null) {
            return contractsRepository.findByManagerIdAndStatus(managerId, status, pageable);
        } else if (hasTerm) {
            return contractsRepository
                    .findByManagerIdAndContractNumberContainingIgnoreCaseOrManagerIdAndCustomerNameContainingIgnoreCase(
                            managerId, term, managerId, term, pageable);
        } else {
            return contractsRepository.findByManagerId(managerId, pageable);
        }
    }

    /**
     * Assigns a manager to a contract.
     */
    public void assignManager(Long contractId, Long managerId) {
        Contracts c = contractsRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException(MSG_CONTRACT_NOT_FOUND_PREFIX + contractId));

        Managers m = managerService.getManagerEntity(managerId);
        if (m == null) {
            throw new ManagerNotFoundException("Manager not found: " + managerId);
        }

        c.setManager(m);
        contractsRepository.save(c);
    }

    /**
     * Gets collaborator manager IDs for a contract.
     */
    public List<Long> getCollaboratorIds(Long contractId) {
        contractsRepository.findById(contractId)
                .orElseThrow(() -> new ContractNotFoundException(MSG_CONTRACT_NOT_FOUND_PREFIX + contractId));
        return contractManagerRepository.findManagerIdsByContractId(contractId);
    }

    /**
     * Sets collaborators for a contract.
     */
    public void setCollaborators(Long contractId, List<Long> managerIds) {
        contractsRepository.findById(contractId)
                .orElseThrow(() -> new ContractNotFoundException(MSG_CONTRACT_NOT_FOUND_PREFIX + contractId));

        contractManagerRepository.deleteAllByContractId(contractId);

        if (managerIds != null) {
            for (Long mid : managerIds) {
                contractManagerRepository.insertIgnore(contractId, mid);
            }
        }
    }

    /**
     * Get contract distribution by business area.
     *
     * @return list of business areas with contract counts
     */
    public List<ContractsByAreaDTO> getContractsByArea() {
        return contractsRepository.countContractsByArea();
    }

    /**
     * Get contracts timeline (created per month for last 6 months). Converts
     * native query results (Object[]) to DTOs.
     *
     * @return list of months with contract counts
     */
    public List<ContractsTimelineDTO> getContractsTimeline() {
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        List<Object[]> results = contractsRepository.countContractsByMonth(sixMonthsAgo);

        return results.stream()
                .map(row -> {
                    int year = ((Number) row[0]).intValue();
                    int month = ((Number) row[1]).intValue();
                    long count = ((Number) row[2]).longValue();

                    // Format as YYYY-MM
                    String monthStr = String.format("%04d-%02d", year, month);
                    return new ContractsTimelineDTO(monthStr, count);
                })
                .toList();
    }

    /**
     * Get top 5 managers by number of assigned contracts.
     *
     * @return list of top managers with contract counts
     */
    public List<TopManagerDTO> getTopManagers() {
        Pageable topFive = PageRequest.of(0, 5);
        return contractsRepository.findTopManagers(topFive);
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

        Long managerId = null;
        if (!ROLE_ADMIN.equals(role) && user.getManager() != null) {
            managerId = user.getManager().getId();
        }

        return new AuthCtx(role, managerId);
    }

    private record AuthCtx(String role, Long managerId) {

    }
}
