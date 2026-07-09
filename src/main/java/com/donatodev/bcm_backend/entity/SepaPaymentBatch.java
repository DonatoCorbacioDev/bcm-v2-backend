package com.donatodev.bcm_backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A generated SEPA Credit Transfer Initiation (pain.001.001.03) file for one contract.
 * Immutable once created: invoices tagged with a batch cannot be re-selected for another one.
 */
@Entity
@Table(name = "sepa_payment_batches")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SepaPaymentBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contracts contract;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "message_id", nullable = false, length = 35)
    private String messageId;

    @Column(name = "execution_date", nullable = false)
    private LocalDate executionDate;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "number_of_transactions", nullable = false)
    private Integer numberOfTransactions;

    @Column(name = "storage_path", nullable = false, unique = true, length = 512)
    private String storagePath;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
