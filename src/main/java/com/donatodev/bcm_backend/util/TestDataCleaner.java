package com.donatodev.bcm_backend.util;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractHistoryRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.PasswordResetTokenRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.repository.VerificationTokenRepository;

/**
 * Utility component for cleaning all test data from the database.
 * <p>
 * This class deletes all entries from repositories to reset the database state,
 * useful for integration testing or resetting the development environment.
 */
@Component
public class TestDataCleaner {

	private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ContractHistoryRepository contractHistoryRepository;
    private final FinancialValuesRepository financialValuesRepository;
    private final ContractsRepository contractsRepository;
    private final UsersRepository usersRepository;
    private final ManagersRepository managersRepository;
    private final RolesRepository rolesRepository;
    private final BusinessAreasRepository businessAreasRepository;
    private final FinancialTypesRepository financialTypesRepository;

    public TestDataCleaner(
        VerificationTokenRepository verificationTokenRepository,
        PasswordResetTokenRepository passwordResetTokenRepository,
        ContractHistoryRepository contractHistoryRepository,
        FinancialValuesRepository financialValuesRepository,
        ContractsRepository contractsRepository,
        UsersRepository usersRepository,
        ManagersRepository managersRepository,
        RolesRepository rolesRepository,
        BusinessAreasRepository businessAreasRepository,
        FinancialTypesRepository financialTypesRepository
    ) {
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.contractHistoryRepository = contractHistoryRepository;
        this.financialValuesRepository = financialValuesRepository;
        this.contractsRepository = contractsRepository;
        this.usersRepository = usersRepository;
        this.managersRepository = managersRepository;
        this.rolesRepository = rolesRepository;
        this.businessAreasRepository = businessAreasRepository;
        this.financialTypesRepository = financialTypesRepository;
    }

    /**
     * Deletes all data from the repositories in the proper order.
     * This effectively cleans the entire database of test data.
     */
    public void clean() {
        verificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        contractHistoryRepository.deleteAll();
        financialValuesRepository.deleteAll();
        contractsRepository.deleteAll();
        usersRepository.deleteAll();
        managersRepository.deleteAll();
        rolesRepository.deleteAll();
        businessAreasRepository.deleteAll();
        financialTypesRepository.deleteAll();
    }
}
