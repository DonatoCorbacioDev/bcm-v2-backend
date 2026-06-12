package com.donatodev.bcm_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.ElectronicInvoice;

@Repository
public interface ElectronicInvoiceRepository extends JpaRepository<ElectronicInvoice, Long> {

    List<ElectronicInvoice> findByContractIdOrderByUploadedAtDesc(Long contractId);

    Optional<ElectronicInvoice> findByIdAndContractId(Long id, Long contractId);
}
