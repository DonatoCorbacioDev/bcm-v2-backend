package com.donatodev.bcm_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.SepaPaymentBatch;

@Repository
public interface SepaPaymentBatchRepository extends JpaRepository<SepaPaymentBatch, Long> {

    List<SepaPaymentBatch> findByContractIdOrderByCreatedAtDesc(Long contractId);

    Optional<SepaPaymentBatch> findByIdAndContractId(Long id, Long contractId);
}
