package com.donatodev.bcm_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.donatodev.bcm_backend.entity.BusinessAreas;

@Repository
public interface BusinessAreasRepository extends JpaRepository<BusinessAreas, Long> {

    BusinessAreas findByName(String name);

    List<BusinessAreas> findAllByOrganizationId(Long organizationId);
}
