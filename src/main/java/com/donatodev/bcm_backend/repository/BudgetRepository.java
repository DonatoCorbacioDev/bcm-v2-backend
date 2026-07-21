package com.donatodev.bcm_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.Budget;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findAllByOrganizationId(Long organizationId);

    Optional<Budget> findByIdAndOrganizationId(Long id, Long organizationId);
}
