/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.export.AuditLogExportDTO;
import com.donatodev.bcm_backend.dto.export.BudgetExportDTO;
import com.donatodev.bcm_backend.dto.export.BusinessAreaExportDTO;
import com.donatodev.bcm_backend.dto.export.ContractDocumentExportDTO;
import com.donatodev.bcm_backend.dto.export.ContractExportDTO;
import com.donatodev.bcm_backend.dto.export.ContractHistoryExportDTO;
import com.donatodev.bcm_backend.dto.export.ContractManagerExportDTO;
import com.donatodev.bcm_backend.dto.export.ContractTemplateExportDTO;
import com.donatodev.bcm_backend.dto.export.ContractWorkflowEventExportDTO;
import com.donatodev.bcm_backend.dto.export.CounterpartyExportDTO;
import com.donatodev.bcm_backend.dto.export.ElectronicInvoiceExportDTO;
import com.donatodev.bcm_backend.dto.export.FinancialTypeExportDTO;
import com.donatodev.bcm_backend.dto.export.FinancialValueExportDTO;
import com.donatodev.bcm_backend.dto.export.ManagerExportDTO;
import com.donatodev.bcm_backend.dto.export.MlResultExportDTO;
import com.donatodev.bcm_backend.dto.export.NotificationExportDTO;
import com.donatodev.bcm_backend.dto.export.RiskFeedbackExportDTO;
import com.donatodev.bcm_backend.dto.export.SepaPaymentBatchExportDTO;
import com.donatodev.bcm_backend.dto.export.TermsAcceptanceExportDTO;
import com.donatodev.bcm_backend.dto.export.UserExportDTO;
import com.donatodev.bcm_backend.entity.MlResultCache;
import com.donatodev.bcm_backend.repository.AuditLogRepository;
import com.donatodev.bcm_backend.repository.BudgetRepository;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.donatodev.bcm_backend.repository.ContractHistoryRepository;
import com.donatodev.bcm_backend.repository.ContractManagerRepository;
import com.donatodev.bcm_backend.repository.ContractTemplateRepository;
import com.donatodev.bcm_backend.repository.ContractWorkflowEventRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.FinancialTypesRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.MlResultCacheRepository;
import com.donatodev.bcm_backend.repository.NotificationRepository;
import com.donatodev.bcm_backend.repository.RiskFeedbackRepository;
import com.donatodev.bcm_backend.repository.SepaPaymentBatchRepository;
import com.donatodev.bcm_backend.repository.TermsAcceptanceRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import lombok.RequiredArgsConstructor;

/**
 * The {@code data/} files of an organization export, in archive order. Adding
 * a file means adding one line to {@link #all()}: a path and a loader that
 * reads the organization's rows and maps them to their export DTO. Every
 * loader must filter by the organization, and never return another one's rows.
 */
@Component
@RequiredArgsConstructor
public class OrganizationExportSections {

    /** One {@code data/} file: a JSON array of the records the loader returns. */
    public record Section(String path, Function<Long, List<?>> loader) {
    }

    private final ManagersRepository managersRepository;
    private final UsersRepository usersRepository;
    private final BusinessAreasRepository businessAreasRepository;
    private final FinancialTypesRepository financialTypesRepository;
    private final CounterpartiesRepository counterpartiesRepository;
    private final ContractsRepository contractsRepository;
    private final ContractManagerRepository contractManagerRepository;
    private final ContractHistoryRepository contractHistoryRepository;
    private final ContractWorkflowEventRepository workflowEventRepository;
    private final FinancialValuesRepository financialValuesRepository;
    private final BudgetRepository budgetRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final ElectronicInvoiceRepository electronicInvoiceRepository;
    private final SepaPaymentBatchRepository sepaPaymentBatchRepository;
    private final ContractTemplateRepository contractTemplateRepository;
    private final RiskFeedbackRepository riskFeedbackRepository;
    private final MlResultCacheRepository mlResultCacheRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final TermsAcceptanceRepository termsAcceptanceRepository;
    private final ObjectMapper objectMapper;

    public List<Section> all() {
        return List.of(
                section("data/managers.json", orgId -> map(
                        managersRepository.findAllByOrganizationId(orgId), ManagerExportDTO::from)),
                section("data/users.json", orgId -> map(
                        usersRepository.findAllByOrganizationId(orgId), UserExportDTO::from)),
                section("data/business_areas.json", orgId -> map(
                        businessAreasRepository.findAllByOrganizationId(orgId), BusinessAreaExportDTO::from)),
                section("data/financial_types.json", orgId -> map(
                        financialTypesRepository.findAllByOrganizationId(orgId), FinancialTypeExportDTO::from)),
                section("data/counterparties.json", orgId -> map(
                        counterpartiesRepository.findAllByOrganizationId(orgId), CounterpartyExportDTO::from)),
                section("data/contracts.json", orgId -> map(
                        contractsRepository.findByOrganization_Id(orgId), ContractExportDTO::from)),
                section("data/contract_managers.json", orgId -> map(
                        contractManagerRepository.findAllByContract_Organization_Id(orgId),
                        ContractManagerExportDTO::from)),
                section("data/contract_history.json", orgId -> map(
                        contractHistoryRepository.findByContract_Organization_Id(orgId),
                        ContractHistoryExportDTO::from)),
                section("data/contract_workflow_events.json", orgId -> map(
                        workflowEventRepository.findAllByContract_Organization_Id(orgId),
                        ContractWorkflowEventExportDTO::from)),
                section("data/financial_values.json", orgId -> map(
                        financialValuesRepository.findAllByOrganizationId(orgId), FinancialValueExportDTO::from)),
                section("data/budgets.json", orgId -> map(
                        budgetRepository.findAllByOrganizationId(orgId), BudgetExportDTO::from)),
                section("data/contract_documents.json", orgId -> map(
                        contractDocumentRepository.findAllByContract_Organization_Id(orgId),
                        ContractDocumentExportDTO::from)),
                section("data/electronic_invoices.json", orgId -> map(
                        electronicInvoiceRepository.findAllByContract_Organization_Id(orgId),
                        ElectronicInvoiceExportDTO::from)),
                section("data/sepa_payment_batches.json", orgId -> map(
                        sepaPaymentBatchRepository.findAllByContract_Organization_Id(orgId),
                        SepaPaymentBatchExportDTO::from)),
                section("data/contract_templates.json", orgId -> map(
                        contractTemplateRepository.findAllByOrgIdOrderByCreatedAtDesc(orgId),
                        ContractTemplateExportDTO::from)),
                section("data/risk_feedback.json", orgId -> map(
                        riskFeedbackRepository.findByOrganizationIdOrderByCreatedAtDescIdDesc(orgId),
                        RiskFeedbackExportDTO::from)),
                section("data/ml_analyses.json", orgId -> map(
                        mlResultCacheRepository.findAllByOrgId(orgId), this::toMlResult)),
                section("data/notifications.json", orgId -> map(
                        notificationRepository.findAllByOrgId(orgId), NotificationExportDTO::from)),
                section("data/audit_logs.json", orgId -> map(
                        auditLogRepository.findAllByOrgIdOrderByTimestampAsc(orgId), AuditLogExportDTO::from)),
                section("data/terms_acceptances.json", orgId -> map(
                        termsAcceptanceRepository.findByOrganizationIdOrderByAcceptedAtDesc(orgId),
                        TermsAcceptanceExportDTO::from)));
    }

    private static Section section(String path, Function<Long, List<?>> loader) {
        return new Section(path, loader);
    }

    private static <E, D> List<D> map(List<E> rows, Function<E, D> toDto) {
        return rows.stream().map(toDto).toList();
    }

    /**
     * The cached result is stored as a JSON string. If one ever fails to parse
     * it is exported as plain text rather than failing the whole export.
     */
    private MlResultExportDTO toMlResult(MlResultCache entry) {
        JsonNode result;
        try {
            result = objectMapper.readTree(entry.getJsonResult());
        } catch (JsonProcessingException e) {
            result = TextNode.valueOf(entry.getJsonResult());
        }
        return new MlResultExportDTO(entry.getCacheKey(), result, entry.getComputedAt());
    }
}
