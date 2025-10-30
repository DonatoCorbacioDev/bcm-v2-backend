package com.donatodev.bcm_backend.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.dto.ManagerDTO;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.exception.ManagerNotFoundException;
import com.donatodev.bcm_backend.mapper.ManagerMapper;
import com.donatodev.bcm_backend.repository.ManagersRepository;

/**
 * Service class for managing business logic related to managers.
 * <p>
 * Provides methods to retrieve, create, update, and delete managers.
 */
@Service
public class ManagerService {

    private static final String MANAGER_ID_PREFIX = "Manager ID ";
	private static final String NOT_FOUND_SUFFIX = " not found";


	private final ManagersRepository managersRepository;
    private final ManagerMapper managerMapper;

    public ManagerService(ManagersRepository managersRepository, ManagerMapper managerMapper) {
        this.managersRepository = managersRepository;
        this.managerMapper = managerMapper;
    }

    /**
     * Retrieves all managers.
     *
     * @return a list of {@link ManagerDTO}
     */
    public List<ManagerDTO> getAllManagers() {
        return managersRepository.findAll()
                .stream()
                .map(managerMapper::toDTO)
                .toList();
    }

    /**
     * Retrieves a manager by their ID.
     *
     * @param id the ID of the manager
     * @return the corresponding {@link ManagerDTO}
     * @throws ManagerNotFoundException if manager not found
     */
    public ManagerDTO getManagerById(Long id) {
        return managersRepository.findById(id)
                .map(managerMapper::toDTO)
                .orElseThrow(() -> new ManagerNotFoundException(MANAGER_ID_PREFIX + id + NOT_FOUND_SUFFIX));
    }

    /**
     * Creates a new manager.
     *
     * @param managerDTO the manager data transfer object
     * @return the created {@link ManagerDTO}
     */
    public ManagerDTO createManager(ManagerDTO managerDTO) {
        Managers manager = managerMapper.toEntity(managerDTO);
        manager = managersRepository.save(manager);
        return managerMapper.toDTO(manager);
    }

    /**
     * Updates an existing manager.
     *
     * @param id         the ID of the manager to update
     * @param managerDTO the updated manager data transfer object
     * @return the updated {@link ManagerDTO}
     * @throws ManagerNotFoundException if manager not found
     */
    public ManagerDTO updateManager(Long id, ManagerDTO managerDTO) {
        Managers manager = managersRepository.findById(id)
                .orElseThrow(() -> new ManagerNotFoundException(MANAGER_ID_PREFIX + id + NOT_FOUND_SUFFIX));

        manager.setFirstName(managerDTO.firstName());
        manager.setLastName(managerDTO.lastName());
        manager.setEmail(managerDTO.email());
        manager.setPhoneNumber(managerDTO.phoneNumber());
        manager.setDepartment(managerDTO.department());

        manager = managersRepository.save(manager);
        return managerMapper.toDTO(manager);
    }

    /**
     * Deletes a manager by their ID.
     *
     * @param id the ID of the manager to delete
     */
    public void deleteManager(Long id) {
        managersRepository.deleteById(id);
    }
    
    public Page<ManagerDTO> searchManagers(String q, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastName").ascending().and(Sort.by("firstName").ascending()));
        Page<Managers> result;
        if (q == null || q.isBlank()) {
            result = managersRepository.findAll(pageable);
        } else {
            String term = q.trim();
            result = managersRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    term, term, term, pageable
                );
        }
        return result.map(managerMapper::toDTO);
   }
    
    public Managers getManagerEntity(Long id) {
        return managersRepository.findById(id)
            .orElseThrow(() -> new ManagerNotFoundException(MANAGER_ID_PREFIX + id + NOT_FOUND_SUFFIX));
    }
}