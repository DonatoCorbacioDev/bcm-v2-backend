package com.donatodev.bcm_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.FinancialTypes;

@Repository
public interface FinancialTypesRepository extends JpaRepository<FinancialTypes, Long> {

    FinancialTypes findByName(String name);

    List<FinancialTypes> findAllByOrganizationId(Long organizationId);
}
