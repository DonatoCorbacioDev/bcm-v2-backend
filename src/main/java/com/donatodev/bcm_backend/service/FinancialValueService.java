package com.donatodev.bcm_backend.service;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.dto.FinancialValueDTO;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.FinancialValueNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.FinancialValueMapper;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Service class for managing business logic related to financial values.
 * <p>
 * Provides methods to retrieve, create, update, and delete financial values
 * with role-based access control.
 */
@Service
public class FinancialValueService {

    private static final String NOT_FOUND_SUFFIX = " not found";
    private static final String FINANCIAL_VALUE_ID_PREFIX = "Financial value ID ";

    private final FinancialValuesRepository financialValuesRepository;
    private final FinancialValueMapper financialValueMapper;
    private final UsersRepository usersRepository;

    public FinancialValueService(
            FinancialValuesRepository financialValuesRepository,
            FinancialValueMapper financialValueMapper,
            UsersRepository usersRepository) {
        this.financialValuesRepository = financialValuesRepository;
        this.financialValueMapper = financialValueMapper;
        this.usersRepository = usersRepository;
    }

    /**
     * Retrieves all financial values accessible to the authenticated user.
     * <p>
     * Admin users retrieve all values, managers only values related to
     * contracts they manage.
     *
     * @return list of {@link FinancialValueDTO}
     * @throws UserNotFoundException if authenticated user is not found
     */
    public List<FinancialValueDTO> getAllValues() {
        String username = getAuthenticatedUsername();
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User" + NOT_FOUND_SUFFIX));

        if ("ADMIN".equals(user.getRole().getRole())) {
            return financialValuesRepository.findAll()
                    .stream()
                    .map(financialValueMapper::toDTO)
                    .toList();
        } else {
            Long managerId = user.getManager().getId();
            return financialValuesRepository.findByContract_Manager_Id(managerId)
                    .stream()
                    .map(financialValueMapper::toDTO)
                    .toList();
        }
    }

    /**
     * Retrieves a financial value by its ID with access control.
     *
     * @param id the financial value ID
     * @return the corresponding {@link FinancialValueDTO}
     * @throws FinancialValueNotFoundException if value not found
     * @throws UserNotFoundException if authenticated user not found
     * @throws SecurityException if access is denied
     */
    public FinancialValueDTO getValueById(Long id) {
        FinancialValues value = financialValuesRepository.findById(id)
                .orElseThrow(() -> new FinancialValueNotFoundException(FINANCIAL_VALUE_ID_PREFIX + id + NOT_FOUND_SUFFIX));

        checkAccessToFinancialValue(value);

        return financialValueMapper.toDTO(value);
    }

    /**
     * Creates a new financial value.
     *
     * @param dto the financial value data transfer object
     * @return the created {@link FinancialValueDTO}
     */
    public FinancialValueDTO createValue(FinancialValueDTO dto) {
        FinancialValues value = financialValueMapper.toEntity(dto);
        value = financialValuesRepository.save(value);
        return financialValueMapper.toDTO(value);
    }

    /**
     * Updates an existing financial value with access control.
     *
     * @param id the ID of the financial value to update
     * @param dto the data transfer object with updated data
     * @return the updated {@link FinancialValueDTO}
     * @throws FinancialValueNotFoundException if value not found
     * @throws UserNotFoundException if authenticated user not found
     * @throws SecurityException if access is denied
     */
    public FinancialValueDTO updateValue(Long id, FinancialValueDTO dto) {
        FinancialValues value = financialValuesRepository.findById(id)
                .orElseThrow(() -> new FinancialValueNotFoundException(FINANCIAL_VALUE_ID_PREFIX + id + NOT_FOUND_SUFFIX));

        checkAccessToFinancialValue(value);

        value.setMonth(dto.month());
        value.setYear(dto.year());
        value.setFinancialAmount(dto.financialAmount());

        // Keep original relations unchanged
        value.setFinancialType(value.getFinancialType());
        value.setBusinessArea(value.getBusinessArea());
        value.setContract(value.getContract());

        value = financialValuesRepository.save(value);
        return financialValueMapper.toDTO(value);
    }

    /**
     * Deletes a financial value by ID with access control.
     *
     * @param id the ID of the financial value to delete
     * @throws FinancialValueNotFoundException if value not found
     * @throws UserNotFoundException if authenticated user not found
     * @throws SecurityException if access is denied
     */
    public void deleteValue(Long id) {
        FinancialValues value = financialValuesRepository.findById(id)
                .orElseThrow(() -> new FinancialValueNotFoundException(FINANCIAL_VALUE_ID_PREFIX + id + NOT_FOUND_SUFFIX));

        checkAccessToFinancialValue(value);

        financialValuesRepository.delete(value);
    }

    /**
     * Retrieves the username of the currently authenticated user.
     *
     * @return the username or {@code null} if not authenticated
     */
    private String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return principal == null ? null : principal.toString();
    }

    /**
     * Checks if the authenticated user has access to the specified financial
     * value.
     * <p>
     * Admins have access to all values. Managers only have access to values
     * related to contracts they manage.
     *
     * @param value the financial value to check
     * @throws UserNotFoundException if authenticated user not found
     * @throws SecurityException if access is denied
     */
    private void checkAccessToFinancialValue(FinancialValues value) {
        String username = getAuthenticatedUsername();
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User" + NOT_FOUND_SUFFIX));

        if ("MANAGER".equals(user.getRole().getRole())) {
            Long managerId = user.getManager().getId();
            Long valueManagerId = value.getContract().getManager().getId();

            if (!managerId.equals(valueManagerId)) {
                throw new SecurityException("Access denied: you are not assigned to this contract");
            }
        }
    }

    /**
     * Retrieves all financial values associated with a specific contract.
     *
     * @param contractId the ID of the contract
     * @return a list of {@link FinancialValueDTO}
     */
    public List<FinancialValueDTO> getValuesByContractId(Long contractId) {
        List<FinancialValues> values = financialValuesRepository.findByContractId(contractId);
        return values.stream()
                .map(financialValueMapper::toDTO)
                .toList();
    }
}
