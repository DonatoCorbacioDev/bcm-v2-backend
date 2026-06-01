package com.donatodev.bcm_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.ContractDocument;

@Repository
public interface ContractDocumentRepository extends JpaRepository<ContractDocument, Long> {

    List<ContractDocument> findByContractIdOrderByUploadedAtDesc(Long contractId);

    Optional<ContractDocument> findByIdAndContractId(Long id, Long contractId);
}
