/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    long countByContractCounterpartyId(Long counterpartyId);

    @Query("SELECT MAX(ei.invoiceDate) FROM ElectronicInvoice ei WHERE ei.contract.counterparty.id = :counterpartyId")
    LocalDate findLastInvoiceDateByCounterpartyId(@Param("counterpartyId") Long counterpartyId);

    @Query("""
            SELECT COALESCE(SUM(ei.totalAmount), 0)
            FROM ElectronicInvoice ei
            WHERE ei.contract.counterparty.id = :counterpartyId
              AND ei.matchStatus = com.donatodev.bcm_backend.entity.InvoiceMatchStatus.CONFIRMED
              AND YEAR(ei.invoiceDate) = :year
            """)
    BigDecimal sumConfirmedInvoicedAmountByCounterpartyIdAndYear(
            @Param("counterpartyId") Long counterpartyId, @Param("year") int year);

    @Query("""
            SELECT COALESCE(SUM(ei.totalAmount), 0)
            FROM ElectronicInvoice ei
            WHERE ei.orgId = :orgId
              AND ei.matchStatus = com.donatodev.bcm_backend.entity.InvoiceMatchStatus.CONFIRMED
              AND YEAR(ei.invoiceDate) = :year
            """)
    BigDecimal sumConfirmedInvoicedAmountByOrgIdAndYear(@Param("orgId") Long orgId, @Param("year") int year);

    @Query("""
            SELECT COALESCE(SUM(ei.totalAmount), 0)
            FROM ElectronicInvoice ei
            WHERE ei.matchStatus = com.donatodev.bcm_backend.entity.InvoiceMatchStatus.CONFIRMED
              AND YEAR(ei.invoiceDate) = :year
            """)
    BigDecimal sumConfirmedInvoicedAmountByYear(@Param("year") int year);

    @Query("""
            SELECT COALESCE(SUM(ei.totalAmount), 0)
            FROM ElectronicInvoice ei
            WHERE ei.contract.manager.id = :managerId
              AND ei.matchStatus = com.donatodev.bcm_backend.entity.InvoiceMatchStatus.CONFIRMED
              AND YEAR(ei.invoiceDate) = :year
            """)
    BigDecimal sumConfirmedInvoicedAmountByManagerIdAndYear(@Param("managerId") Long managerId, @Param("year") int year);
}
