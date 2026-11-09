/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// Every SUM below is of the taxable amount (contract amounts are IVA
// esclusa), falling back to the total for an invoice parsed before it was
// stored, and a credit note (TD04) counts negative.
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.InvoiceMatchStatus;

@Repository
public interface ElectronicInvoiceRepository extends JpaRepository<ElectronicInvoice, UUID> {

    List<ElectronicInvoice> findByContractIdOrderByUploadedAtDesc(UUID contractId);

    /** Invoices parsed before the taxable amount was stored (see InvoiceTaxableAmountBackfill). */
    List<ElectronicInvoice> findByTaxableAmountIsNull();

    /** Invoices uploaded before the payment method was read (V53), for the startup backfill. */
    List<ElectronicInvoice> findByPaymentMethodIsNull();

    /** The organization's invoices not paid by a SEPA batch yet: candidates for the home page's to-do list. */
    List<ElectronicInvoice> findByOrgIdAndSepaBatchIsNull(UUID orgId);

    Optional<ElectronicInvoice> findByIdAndContractId(UUID id, UUID contractId);

    List<ElectronicInvoice> findByContractIdAndIdIn(UUID contractId, List<UUID> ids);

    List<ElectronicInvoice> findByContractIdAndMatchStatusIn(UUID contractId, List<InvoiceMatchStatus> statuses);

    Optional<ElectronicInvoice> findByMatchedFinancialValue_IdAndMatchStatus(UUID financialValueId, InvoiceMatchStatus status);

    boolean existsByOrgIdAndSupplierVatNumberAndInvoiceNumberAndDocumentType(
            UUID orgId, String supplierVatNumber, String invoiceNumber, String documentType);

    long countByContractCounterpartyId(UUID counterpartyId);

    @Query("SELECT MAX(ei.invoiceDate) FROM ElectronicInvoice ei WHERE ei.contract.counterparty.id = :counterpartyId")
    LocalDate findLastInvoiceDateByCounterpartyId(@Param("counterpartyId") UUID counterpartyId);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN ei.documentType = 'TD04'
                    THEN -COALESCE(ei.taxableAmount, ei.totalAmount)
                    ELSE COALESCE(ei.taxableAmount, ei.totalAmount) END), 0)
            FROM ElectronicInvoice ei
            WHERE ei.contract.counterparty.id = :counterpartyId
              AND ei.matchStatus = com.donatodev.bcm_backend.entity.InvoiceMatchStatus.CONFIRMED
              AND YEAR(ei.invoiceDate) = :year
            """)
    BigDecimal sumConfirmedInvoicedAmountByCounterpartyIdAndYear(
            @Param("counterpartyId") UUID counterpartyId, @Param("year") int year);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN ei.documentType = 'TD04'
                    THEN -COALESCE(ei.taxableAmount, ei.totalAmount)
                    ELSE COALESCE(ei.taxableAmount, ei.totalAmount) END), 0)
            FROM ElectronicInvoice ei
            WHERE ei.orgId = :orgId
              AND ei.matchStatus = com.donatodev.bcm_backend.entity.InvoiceMatchStatus.CONFIRMED
              AND YEAR(ei.invoiceDate) = :year
            """)
    BigDecimal sumConfirmedInvoicedAmountByOrgIdAndYear(@Param("orgId") UUID orgId, @Param("year") int year);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN ei.documentType = 'TD04'
                    THEN -COALESCE(ei.taxableAmount, ei.totalAmount)
                    ELSE COALESCE(ei.taxableAmount, ei.totalAmount) END), 0)
            FROM ElectronicInvoice ei
            WHERE ei.matchStatus = com.donatodev.bcm_backend.entity.InvoiceMatchStatus.CONFIRMED
              AND YEAR(ei.invoiceDate) = :year
            """)
    BigDecimal sumConfirmedInvoicedAmountByYear(@Param("year") int year);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN ei.documentType = 'TD04'
                    THEN -COALESCE(ei.taxableAmount, ei.totalAmount)
                    ELSE COALESCE(ei.taxableAmount, ei.totalAmount) END), 0)
            FROM ElectronicInvoice ei
            WHERE ei.contract.manager.id = :managerId
              AND ei.matchStatus = com.donatodev.bcm_backend.entity.InvoiceMatchStatus.CONFIRMED
              AND YEAR(ei.invoiceDate) = :year
            """)
    BigDecimal sumConfirmedInvoicedAmountByManagerIdAndYear(@Param("managerId") UUID managerId, @Param("year") int year);

    // Scoped through the contract rather than the row's own org_id column,
    // which older rows may lack: the contract is always set and always knows its org.
    List<ElectronicInvoice> findAllByContract_Organization_Id(UUID organizationId);
}
