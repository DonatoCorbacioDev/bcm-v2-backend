package com.donatodev.bcm_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.Managers;

@Repository
public interface ManagersRepository extends JpaRepository<Managers, Long> {

    Managers findByEmail(String email);

    Page<Managers> findByOrganizationId(Long organizationId, Pageable pageable);

    Page<Managers> findByOrganizationIdAndFirstNameContainingIgnoreCaseOrOrganizationIdAndLastNameContainingIgnoreCaseOrOrganizationIdAndEmailContainingIgnoreCase(
            Long orgId1, String first,
            Long orgId2, String last,
            Long orgId3, String email,
            Pageable pageable);

    Page<Managers> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String first, String last, String email, Pageable pageable);
}
