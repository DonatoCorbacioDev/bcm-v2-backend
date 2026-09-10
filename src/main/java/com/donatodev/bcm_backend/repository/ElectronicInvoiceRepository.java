/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.InvoiceMatchStatus;

@Repository
public interface ElectronicInvoiceRepository extends JpaRepository<ElectronicInvoice, Long> {

    List<ElectronicInvoice> findByContractIdOrderByUploadedAtDesc(Long contractId);

    Optional<ElectronicInvoice> findByIdAndContractId(Long id, Long contractId);

    List<ElectronicInvoice> findByContractIdAndIdIn(Long contractId, List<Long> ids);

    List<ElectronicInvoice> findByContractIdAndMatchStatusIn(Long contractId, List<InvoiceMatchStatus> statuses);

    Optional<ElectronicInvoice> findByMatchedFinancialValue_IdAndMatchStatus(Long financialValueId, InvoiceMatchStatus status);

    boolean existsByOrgIdAndSupplierVatNumberAndInvoiceNumberAndDocumentType(
            Long orgId, String supplierVatNumber, String invoiceNumber, String documentType);
}
