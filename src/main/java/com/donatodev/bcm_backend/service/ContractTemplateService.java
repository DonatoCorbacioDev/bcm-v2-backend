package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.dto.ContractTemplateDTO;
import com.donatodev.bcm_backend.dto.InstantiateTemplateDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.ContractTemplate;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.exception.BusinessAreaNotFoundException;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.ManagerNotFoundException;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractTemplateRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;

@Service
public class ContractTemplateService {

    private final ContractTemplateRepository templateRepository;
    private final BusinessAreasRepository businessAreasRepository;
    private final ManagersRepository managersRepository;
    private final ContractService contractService;

    public ContractTemplateService(
            ContractTemplateRepository templateRepository,
            BusinessAreasRepository businessAreasRepository,
            ManagersRepository managersRepository,
            ContractService contractService) {
        this.templateRepository = templateRepository;
        this.businessAreasRepository = businessAreasRepository;
        this.managersRepository = managersRepository;
        this.contractService = contractService;
    }

    @Transactional(readOnly = true)
    public List<ContractTemplateDTO> getTemplates() {
        Long orgId = requireOrgId();
        return templateRepository.findAllByOrgIdOrderByCreatedAtDesc(orgId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractTemplateDTO getTemplate(Long id) {
        Long orgId = requireOrgId();
        return toDTO(findInScope(id, orgId));
    }

    @Transactional
    public ContractTemplateDTO createTemplate(ContractTemplateDTO dto) {
        Long orgId = requireOrgId();
        ContractTemplate template = ContractTemplate.builder()
                .name(dto.name())
                .description(dto.description())
                .defaultStatus(dto.defaultStatus() != null ? dto.defaultStatus() : ContractStatus.DRAFT)
                .defaultDurationDays(dto.defaultDurationDays())
                .autoRenew(dto.autoRenew())
                .notificationDays(dto.notificationDays())
                .orgId(orgId)
                .build();
        applyRelations(template, dto.businessAreaId(), dto.defaultManagerId());
        return toDTO(templateRepository.save(template));
    }

    @Transactional
    public ContractTemplateDTO updateTemplate(Long id, ContractTemplateDTO dto) {
        Long orgId = requireOrgId();
        ContractTemplate template = findInScope(id, orgId);
        template.setName(dto.name());
        template.setDescription(dto.description());
        template.setDefaultStatus(dto.defaultStatus() != null ? dto.defaultStatus() : ContractStatus.DRAFT);
        template.setDefaultDurationDays(dto.defaultDurationDays());
        template.setAutoRenew(dto.autoRenew());
        template.setNotificationDays(dto.notificationDays());
        applyRelations(template, dto.businessAreaId(), dto.defaultManagerId());
        return toDTO(templateRepository.save(template));
    }

    @Transactional
    public void deleteTemplate(Long id) {
        Long orgId = requireOrgId();
        templateRepository.delete(findInScope(id, orgId));
    }

    @Transactional
    public ContractDTO instantiateTemplate(Long templateId, InstantiateTemplateDTO req) {
        Long orgId = requireOrgId();
        ContractTemplate template = findInScope(templateId, orgId);

        Long areaId = req.businessAreaId() != null ? req.businessAreaId()
                : (template.getBusinessArea() != null ? template.getBusinessArea().getId() : null);
        if (areaId == null) {
            throw new IllegalArgumentException(
                    "Business area is required: not defined in template and not provided in request");
        }

        Long managerId = req.managerId() != null ? req.managerId()
                : (template.getDefaultManager() != null ? template.getDefaultManager().getId() : null);
        ContractStatus status = req.status() != null ? req.status() : template.getDefaultStatus();

        LocalDate endDate = req.endDate();
        if (endDate == null && template.getDefaultDurationDays() != null) {
            endDate = req.startDate().plusDays(template.getDefaultDurationDays());
        }

        ContractDTO contractDTO = new ContractDTO(
                null, req.customerName(), req.contractNumber(),
                req.wbsCode(), req.projectName(), status,
                req.startDate(), endDate,
                areaId, managerId, null, null, null, null);

        return contractService.createContract(contractDTO);
    }

    private void applyRelations(ContractTemplate template, Long businessAreaId, Long defaultManagerId) {
        if (businessAreaId != null) {
            BusinessAreas area = businessAreasRepository.findById(businessAreaId)
                    .orElseThrow(() -> new BusinessAreaNotFoundException(
                            "Business area not found: " + businessAreaId));
            template.setBusinessArea(area);
        } else {
            template.setBusinessArea(null);
        }
        if (defaultManagerId != null) {
            Managers manager = managersRepository.findById(defaultManagerId)
                    .orElseThrow(() -> new ManagerNotFoundException(
                            "Manager not found: " + defaultManagerId));
            template.setDefaultManager(manager);
        } else {
            template.setDefaultManager(null);
        }
    }

    private ContractTemplate findInScope(Long id, Long orgId) {
        return templateRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new ContractNotFoundException("Contract template not found: " + id));
    }

    private Long requireOrgId() {
        Long orgId = TenantContext.get();
        if (orgId == null) {
            throw new IllegalStateException("No organization context for contract template operation");
        }
        return orgId;
    }

    private ContractTemplateDTO toDTO(ContractTemplate t) {
        return new ContractTemplateDTO(
                t.getId(),
                t.getName(),
                t.getDescription(),
                t.getDefaultStatus(),
                t.getDefaultDurationDays(),
                t.getBusinessArea() != null ? t.getBusinessArea().getId() : null,
                t.getDefaultManager() != null ? t.getDefaultManager().getId() : null,
                t.isAutoRenew(),
                t.getNotificationDays(),
                t.getCreatedAt());
    }
}
