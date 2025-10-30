package com.donatodev.bcm_backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.dto.BusinessAreaDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.exception.BusinessAreaNotFoundException;
import com.donatodev.bcm_backend.mapper.BusinessAreaMapper;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;

/**
 * Service class responsible for business logic related to business areas.
 * <p>
 * Provides methods to retrieve, create, update, and delete business areas.
 */
@Service
public class BusinessAreaService {

	private final BusinessAreasRepository businessAreasRepository;
    private final BusinessAreaMapper businessAreaMapper;

    public BusinessAreaService(BusinessAreasRepository businessAreasRepository, BusinessAreaMapper businessAreaMapper) {
        this.businessAreasRepository = businessAreasRepository;
        this.businessAreaMapper = businessAreaMapper;
    }

    /**
     * Retrieves all business areas as DTOs.
     *
     * @return a list of all {@link BusinessAreaDTO}
     */
    public List<BusinessAreaDTO> getAllAreas() {
        return businessAreasRepository.findAll()
                .stream()
                .map(businessAreaMapper::toDTO)
                .toList();
    }

    /**
     * Retrieves a business area by its ID.
     *
     * @param id the ID of the business area
     * @return the corresponding {@link BusinessAreaDTO}
     * @throws BusinessAreaNotFoundException if the area is not found
     */
    public BusinessAreaDTO getAreaById(Long id) {
        return businessAreasRepository.findById(id)
                .map(businessAreaMapper::toDTO)
                .orElseThrow(() -> new BusinessAreaNotFoundException("Business area ID " + id + " not found"));
    }

    /**
     * Creates a new business area.
     *
     * @param dto the business area data transfer object
     * @return the created {@link BusinessAreaDTO}
     */
    public BusinessAreaDTO createArea(BusinessAreaDTO dto) {
        BusinessAreas area = businessAreaMapper.toEntity(dto);
        area = businessAreasRepository.save(area);
        return businessAreaMapper.toDTO(area);
    }

    /**
     * Updates an existing business area identified by ID.
     *
     * @param id  the ID of the business area to update
     * @param dto the updated business area data transfer object
     * @return the updated {@link BusinessAreaDTO}
     * @throws BusinessAreaNotFoundException if the area is not found
     */
    public BusinessAreaDTO updateArea(Long id, BusinessAreaDTO dto) {
        BusinessAreas area = businessAreasRepository.findById(id)
                .orElseThrow(() -> new BusinessAreaNotFoundException("Business area ID " + id + " not found"));

        area.setName(dto.name());
        area.setDescription(dto.description());

        area = businessAreasRepository.save(area);
        return businessAreaMapper.toDTO(area);
    }

    /**
     * Deletes a business area by its ID.
     *
     * @param id the ID of the business area to delete
     */
    public void deleteArea(Long id) {
        businessAreasRepository.deleteById(id);
    }
}
