package com.donatodev.bcm_backend.service;

import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Shared tenant-scoping and manager-access guard for services that operate on
 * a {@link Contracts} by ID, such as document and invoice attachments.
 * Extracted from {@code ContractDocumentService} and
 * {@code ElectronicInvoiceService}, which were byte-for-byte identical here.
 */
@Component
public class ContractAccessGuard {

    private final ContractsRepository contractsRepository;
    private final UsersRepository usersRepository;

    public ContractAccessGuard(ContractsRepository contractsRepository, UsersRepository usersRepository) {
        this.contractsRepository = contractsRepository;
        this.usersRepository = usersRepository;
    }

    /**
     * Finds the contract by ID, scoped to the current tenant when
     * {@link TenantContext} carries an organization ID. Used to prevent
     * cross-tenant access to a contract's documents/invoices by ID.
     */
    public Contracts getContractInScope(Long contractId) {
        Long orgId = TenantContext.get();
        Optional<Contracts> contract = (orgId != null)
                ? contractsRepository.findByIdAndOrganization_Id(contractId, orgId)
                : contractsRepository.findById(contractId);
        return contract.orElseThrow(() -> new ContractNotFoundException("Contract ID " + contractId + " not found"));
    }

    /**
     * Throws {@link AccessDeniedException} when the authenticated user is a MANAGER
     * not assigned to the given contract. Skips the check when no auth context is present.
     */
    public void checkManagerCanAccess(Contracts contract) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return;
        }
        Long orgId = TenantContext.get();
        if (orgId == null) {
            return;
        }
        Users user = usersRepository.findByUsernameAndOrganizationId(auth.getName(), orgId).orElse(null);
        if (user == null || !"MANAGER".equals(user.getRole().getRole())) {
            return;
        }
        Long managerId = user.getManager() != null ? user.getManager().getId() : null;
        Long contractManagerId = contract.getManager() != null ? contract.getManager().getId() : null;
        if (managerId == null || !managerId.equals(contractManagerId)) {
            throw new AccessDeniedException("Not authorized to access contract: " + contract.getId());
        }
    }
}
