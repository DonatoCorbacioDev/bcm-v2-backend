package com.donatodev.bcm_backend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ContractHistoryDTO;
import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.AccessDeniedException;
import com.donatodev.bcm_backend.exception.ContractHistoryNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.ContractHistoryMapper;
import com.donatodev.bcm_backend.repository.ContractHistoryRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Service class for business logic related to contract history.
 * <p>
 * Provides methods to retrieve, create, and delete contract history entries,
 * with access control based on user roles and ownership.
 */
@Service
public class ContractHistoryService {
	
	private static final String USER_NOT_FOUND_MSG = "User not found";
	private static final String ADMIN_ROLE = "ADMIN"; 

	private final ContractHistoryRepository historyRepository;
	private final ContractHistoryMapper historyMapper;
	private final UsersRepository usersRepository;

	public ContractHistoryService(
			ContractHistoryRepository historyRepository,
			ContractHistoryMapper historyMapper,
			UsersRepository usersRepository) {
		this.historyRepository = historyRepository;
		this.historyMapper = historyMapper;
		this.usersRepository = usersRepository;
	}

	/**
	 * Retrieves all contract history entries accessible to the authenticated user.
	 * <p>
	 * Admin users retrieve all entries,
	 * while managers only retrieve entries related to contracts they manage.
	 *
	 * @return list of {@link ContractHistoryDTO}
	 * @throws UserNotFoundException if the authenticated user cannot be found
	 */
	public List<ContractHistoryDTO> getAll() {
		String username = getAuthenticatedUsername();
		Users user = usersRepository.findByUsername(username)
				.orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_MSG));

		if (user.getRole().getRole().equals(ADMIN_ROLE)) {
			Long orgId = TenantContext.get();
			List<ContractHistory> all = (orgId != null)
					? historyRepository.findByContract_Organization_Id(orgId)
					: historyRepository.findAll();
			return all.stream()
					.map(historyMapper::toDTO)
					.toList();
		} else {
			Long managerId = user.getManager().getId();
			return historyRepository.findByContractManagerId(managerId).stream()
					.map(historyMapper::toDTO)
					.toList();
		}
	}

	/**
	 * Retrieves a specific contract history entry by ID,
	 * with access control based on user role and contract ownership.
	 *
	 * @param id the ID of the contract history entry
	 * @return the corresponding {@link ContractHistoryDTO}
	 * @throws ContractHistoryNotFoundException if the entry is not found
	 * @throws UserNotFoundException if the authenticated user cannot be found
	 * @throws RuntimeException if access is denied
	 */
	public ContractHistoryDTO getById(Long id) {
		ContractHistory history = findHistoryInScope(id)
				.orElseThrow(() -> new ContractHistoryNotFoundException("History ID " + id + " not found"));

		String username = getAuthenticatedUsername();
		Users user = usersRepository.findByUsername(username)
				.orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_MSG));

		if (!user.getRole().getRole().equals(ADMIN_ROLE)) { 
			Long managerId = user.getManager().getId();
			if (!history.getContract().getManager().getId().equals(managerId)) {
				throw new AccessDeniedException("Access denied: not authorized to view this history record");
			}
		}

		return historyMapper.toDTO(history);
	}

	/**
	 * Retrieves all contract history entries for a specific contract,
	 * with access control based on user role and contract ownership.
	 *
	 * @param contractId the ID of the contract
	 * @return list of {@link ContractHistoryDTO}
	 * @throws UserNotFoundException if the authenticated user cannot be found
	 * @throws RuntimeException if access is denied
	 */
	public List<ContractHistoryDTO> getByContractId(Long contractId) {
		String username = getAuthenticatedUsername();
		Users user = usersRepository.findByUsername(username)
				.orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_MSG));

		Long orgId = TenantContext.get();
		List<ContractHistory> all = (orgId != null)
				? historyRepository.findByContractIdAndContract_Organization_Id(contractId, orgId)
				: historyRepository.findByContractId(contractId);

		if (user.getRole().getRole().equals(ADMIN_ROLE)) {
			return all.stream()
					.map(historyMapper::toDTO)
					.toList();
		} else {
			Long managerId = user.getManager().getId();
			boolean isOwner = all.stream()
					.allMatch(h -> h.getContract().getManager().getId().equals(managerId));

			if (!isOwner) {
				throw new AccessDeniedException("Access denied: not authorized to view this contract history");
			}

			return all.stream()
					.map(historyMapper::toDTO)
					.toList();
		}
	}

	/**
	 * Creates a new contract history entry.
	 *
	 * @param dto the contract history data transfer object
	 * @return the created {@link ContractHistoryDTO}
	 */
	public ContractHistoryDTO create(ContractHistoryDTO dto) {
		ContractHistory entity = historyMapper.toEntity(dto);
		entity = historyRepository.save(entity);
		return historyMapper.toDTO(entity);
	}

	/**
	 * Deletes a contract history entry by ID.
	 *
	 * @param id the ID of the contract history entry to delete
	 * @throws ContractHistoryNotFoundException if the entry does not exist
	 */
	public void delete(Long id) {
		ContractHistory history = findHistoryInScope(id)
				.orElseThrow(() -> new ContractHistoryNotFoundException("History ID " + id + " not found"));
		historyRepository.delete(history);
	}

	/**
	 * Finds a contract history entry by ID, scoped to the current tenant when
	 * {@link TenantContext} carries an organization ID.
	 */
	private Optional<ContractHistory> findHistoryInScope(Long id) {
		Long orgId = TenantContext.get();
		return (orgId != null)
				? historyRepository.findByIdAndContract_Organization_Id(id, orgId)
				: historyRepository.findById(id);
	}

	/**
	 * Retrieves the username of the currently authenticated user.
	 *
	 * @return the username, or {@code null} if authentication information is not available
	 */
	private String getAuthenticatedUsername() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return null;
		}
		Object principal = authentication.getPrincipal();
		if (principal == null) {
			return null;
		}
		if (principal instanceof UserDetails userDetails) {
			return userDetails.getUsername();
		}
		return principal.toString();
	}
}