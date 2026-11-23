/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.exception.TierLimitExceededException;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;

/**
 * Disk space per organization (OWASP ASVS 2.4.1, quotas): every version of
 * every document and every invoice file counted. Without it one account,
 * uploading as fast as its per-minute budget allows, fills the VM's disk in
 * hours, and MySQL, on the same disk, stops with it. Documented in
 * docs/BUSINESS_RULES.md.
 */
@Service
public class StorageQuotaService {

    static final long MB = 1024L * 1024;

    /** Per tier; a tier not listed (ENTERPRISE) has no quota. */
    static final Map<SubscriptionTier, Long> MAX_BYTES = Map.of(
            SubscriptionTier.FREE, 250 * MB,
            SubscriptionTier.PRO, 5 * 1024 * MB);

    private final OrganizationRepository organizationRepository;
    private final ContractDocumentRepository documentRepository;
    private final ElectronicInvoiceRepository invoiceRepository;

    public StorageQuotaService(OrganizationRepository organizationRepository,
            ContractDocumentRepository documentRepository, ElectronicInvoiceRepository invoiceRepository) {
        this.organizationRepository = organizationRepository;
        this.documentRepository = documentRepository;
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Refuses a file of {@code incomingBytes} that would take the organization
     * past its quota (409, as the contract limit). Call it before the file is
     * written, inside the upload's transaction: the organization's row stays
     * locked until it ends, so simultaneous uploads are counted one after the
     * other, never against the same free space.
     */
    public void requireRoom(UUID orgId, long incomingBytes) {
        if (orgId == null) {
            return;
        }
        // The sums must see what the others committed meanwhile: see QuotaTransactions.
        QuotaTransactions.requireReadCommitted();
        Organization organization = organizationRepository.findByIdForUpdate(orgId).orElse(null);
        if (organization == null) {
            return;
        }
        Long limit = MAX_BYTES.get(organization.getSubscriptionTier());
        if (limit == null) {
            return;
        }
        long used = usedBytes(orgId);
        if (used + incomingBytes > limit) {
            throw new TierLimitExceededException("Spazio del piano " + organization.getSubscriptionTier()
                    + " esaurito: usati " + megabytes(used) + " MB su " + megabytes(limit)
                    + " MB. Elimina documenti o fatture che non servono più, o passa a un piano superiore.");
        }
    }

    /** Bytes the organization's stored files take now. */
    public long usedBytes(UUID orgId) {
        return documentRepository.sumFileSizeByOrganization(orgId) + invoiceRepository.sumFileSizeByOrganization(orgId);
    }

    private static long megabytes(long bytes) {
        return (bytes + MB - 1) / MB;
    }
}
