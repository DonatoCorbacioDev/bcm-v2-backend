package com.donatodev.bcm_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.ContractTemplate;

@Repository
public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {

    List<ContractTemplate> findAllByOrgIdOrderByCreatedAtDesc(Long orgId);

    Optional<ContractTemplate> findByIdAndOrgId(Long id, Long orgId);
}
