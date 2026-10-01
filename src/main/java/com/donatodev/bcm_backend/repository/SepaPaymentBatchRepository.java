/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.SepaPaymentBatch;

@Repository
public interface SepaPaymentBatchRepository extends JpaRepository<SepaPaymentBatch, Long> {

    List<SepaPaymentBatch> findByContractIdOrderByCreatedAtDesc(Long contractId);

    Optional<SepaPaymentBatch> findByIdAndContractId(Long id, Long contractId);

    /**
     * Batches older than the retention cutoff, backing
     * {@code SepaPaymentRetentionService}. Returned as entities (not a bulk
     * delete) because each one's file on disk must be removed too.
     */
    List<SepaPaymentBatch> findByCreatedAtBefore(Instant cutoff);
}
