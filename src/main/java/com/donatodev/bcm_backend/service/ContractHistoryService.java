/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ContractHistoryDTO;
import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractHistoryNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.ContractHistoryMapper;
import com.donatodev.bcm_backend.repository.ContractHistoryRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.util.AuthenticatedUserUtils;

/**
 * Service class for business logic related to contract history.
 * <p>
 * Provides methods to retrieve, create, and delete contract history entries,
 * with access control based on user roles and ownership.
 */
@Service
public class ContractHistoryService {
	
	private static final String USER_NOT_FOUND_MSG = "Utente non trovato";
	private static final String ADMIN_ROLE = "ADMIN"; 

	private final ContractHistoryRepository historyRepository;
	private final ContractHistoryMapper historyMapper;
	private final UsersRepository usersRepository;
	private final ContractAccessGuard contractAccessGuard;

	public ContractHistoryService(
			ContractHistoryRepository historyRepository,
			ContractHistoryMapper historyMapper,
			UsersRepository usersRepository, ContractAccessGuard contractAccessGuard) {
		this.historyRepository = historyRepository;
		this.historyMapper = historyMapper;
		this.usersRepository = usersRepository;
		this.contractAccessGuard = contractAccessGuard;
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
		String username = AuthenticatedUserUtils.getUsernameOrNull();
		Users user = usersRepository.findByUsername(username)
				.orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_MSG));

		if (user.getRole().getRole().equals(ADMIN_ROLE)) {
			Long orgId = TenantContext.require();
			List<ContractHistory> all = historyRepository.findByContract_Organization_Id(orgId);
			return all.stream()
					.map(historyMapper::toDTO)
					.toList();
		} else {
			if (user.getManager() == null) {
				return List.of();
			}
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
				.orElseThrow(() -> new ContractHistoryNotFoundException("Voce storico ID " + id + " non trovata"));

		String username = AuthenticatedUserUtils.getUsernameOrNull();
		Users user = usersRepository.findByUsername(username)
				.orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_MSG));

		checkManagerAccess(history.getContract(), user);

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
		String username = AuthenticatedUserUtils.getUsernameOrNull();
		Users user = usersRepository.findByUsername(username)
				.orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_MSG));

		Contracts contract = contractAccessGuard.getContractInScope(contractId);
		checkManagerAccess(contract, user);
		Long orgId = TenantContext.require();
		return historyRepository.findByContractIdAndContract_Organization_Id(contractId, orgId)
				.stream().map(historyMapper::toDTO).toList();
	}

	/** A plain manager reads only the history of contracts assigned to them; an admin reads all. */
	private void checkManagerAccess(Contracts contract, Users user) {
		if (!ADMIN_ROLE.equals(user.getRole().getRole()) && (user.getManager() == null
				|| contract.getManager() == null
				|| !user.getManager().getId().equals(contract.getManager().getId()))) {
			throw new AccessDeniedException("non autorizzato a visualizzare lo storico di questo contratto");
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
		// Creation must never merge an existing row selected by a client-supplied ID.
		entity.setId(null);
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
				.orElseThrow(() -> new ContractHistoryNotFoundException("Voce storico ID " + id + " non trovata"));
		historyRepository.delete(history);
	}

	/**
	 * Finds a contract history entry by ID, scoped to the current tenant when
	 * {@link TenantContext} carries an organization ID.
	 */
	private Optional<ContractHistory> findHistoryInScope(Long id) {
		Long orgId = TenantContext.require();
		return historyRepository.findByIdAndContract_Organization_Id(id, orgId);
	}
}