package com.donatodev.bcm_backend.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.donatodev.bcm_backend.dto.ContractDTO;
import com.donatodev.bcm_backend.dto.ContractTemplateDTO;
import com.donatodev.bcm_backend.dto.InstantiateTemplateDTO;
import com.donatodev.bcm_backend.service.ContractTemplateService;

@RestController
@RequestMapping("/contract-templates")
public class ContractTemplateController {

    private final ContractTemplateService templateService;

    public ContractTemplateController(ContractTemplateService templateService) {
        this.templateService = templateService;
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @GetMapping
    public ResponseEntity<List<ContractTemplateDTO>> getTemplates() {
        return ResponseEntity.ok(templateService.getTemplates());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @GetMapping("/{id}")
    public ResponseEntity<ContractTemplateDTO> getTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getTemplate(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ContractTemplateDTO> createTemplate(
            @Valid @RequestBody ContractTemplateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createTemplate(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ContractTemplateDTO> updateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody ContractTemplateDTO dto) {
        return ResponseEntity.ok(templateService.updateTemplate(id, dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @PostMapping("/{id}/instantiate")
    public ResponseEntity<ContractDTO> instantiateTemplate(
            @PathVariable Long id,
            @Valid @RequestBody InstantiateTemplateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.instantiateTemplate(id, dto));
    }
}
