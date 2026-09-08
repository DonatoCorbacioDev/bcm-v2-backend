/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ConfirmInvoiceMatchRequest;
import com.donatodev.bcm_backend.dto.ElectronicInvoiceDTO;
import com.donatodev.bcm_backend.dto.FatturaPaInvoiceData;
import com.donatodev.bcm_backend.dto.InvoiceLineItemDTO;
import com.donatodev.bcm_backend.dto.UpdateInvoicePaymentDetailsRequest;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.util.IbanValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ElectronicInvoiceService {

    private static final Logger logger = LoggerFactory.getLogger(ElectronicInvoiceService.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;
    private static final String INVOICE_NOT_FOUND = "Fattura ID %d non trovata per il contratto %d";
    private static final String CRLF_REGEX = "[\r\n]";

    @Value("${app.backend-base-url:http://localhost:8090/api/v1}")
    private String backendBaseUrl;

    private final ElectronicInvoiceRepository invoiceRepository;
    private final ContractAccessGuard contractAccessGuard;
    private final LocalStorageService localStorageService;
    private final FatturaPaXmlParserService fatturaPaXmlParserService;
    private final InvoiceMatchingService invoiceMatchingService;
    private final ObjectMapper objectMapper;

    public ElectronicInvoiceService(ElectronicInvoiceRepository invoiceRepository,
                                     ContractAccessGuard contractAccessGuard,
                                     LocalStorageService localStorageService,
                                     FatturaPaXmlParserService fatturaPaXmlParserService,
                                     InvoiceMatchingService invoiceMatchingService,
                                     ObjectMapper objectMapper) {
        this.invoiceRepository = invoiceRepository;
        this.contractAccessGuard = contractAccessGuard;
        this.localStorageService = localStorageService;
        this.fatturaPaXmlParserService = fatturaPaXmlParserService;
        this.invoiceMatchingService = invoiceMatchingService;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = IOException.class)
    public ElectronicInvoiceDTO uploadInvoice(Long contractId, MultipartFile file) throws IOException {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);

        validateFile(file);

        byte[] bytes = file.getBytes();
        FatturaPaInvoiceData parsed = fatturaPaXmlParserService.parse(bytes);

        Long orgId = TenantContext.get();
        String storagePath = localStorageService.storeInvoice(orgId, contractId, bytes);

        String lineItemsJson = objectMapper.writeValueAsString(parsed.lineItems());

        ElectronicInvoice invoice = invoiceRepository.save(ElectronicInvoice.builder()
                .contract(contract)
                .storagePath(storagePath)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType("application/xml")
                .orgId(orgId)
                .supplierName(parsed.supplierName())
                .supplierVatNumber(parsed.supplierVatNumber())
                .documentType(parsed.documentType())
                .invoiceNumber(parsed.invoiceNumber())
                .invoiceDate(parsed.invoiceDate())
                .totalAmount(parsed.totalAmount())
                .currency(parsed.currency())
                .lineItemsJson(lineItemsJson)
                .supplierIban(parsed.supplierIban())
                .supplierBic(parsed.supplierBic())
                .paymentDueDate(parsed.paymentDueDate())
                .build());

        try {
            invoiceMatchingService.computeSuggestion(invoice);
            invoice = invoiceRepository.save(invoice);
        } catch (RuntimeException e) {
            String safeMessage = e.getMessage() == null ? null : e.getMessage().replaceAll(CRLF_REGEX, "_");
            logger.warn("Invoice match suggestion failed for invoice {}: {}", invoice.getId(), safeMessage);
        }

        return toDTO(invoice);
    }

    @Transactional(readOnly = true)
    public List<ElectronicInvoiceDTO> getInvoices(Long contractId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        return invoiceRepository.findByContractIdOrderByUploadedAtDesc(contractId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ElectronicInvoiceDTO getInvoice(Long contractId, Long invoiceId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        ElectronicInvoice invoice = invoiceRepository.findByIdAndContractId(invoiceId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(INVOICE_NOT_FOUND, invoiceId, contractId)));
        return toDTO(invoice);
    }

    @Transactional(readOnly = true)
    public FileDownload downloadInvoice(Long contractId, Long invoiceId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        ElectronicInvoice invoice = invoiceRepository.findByIdAndContractId(invoiceId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(INVOICE_NOT_FOUND, invoiceId, contractId)));

        byte[] bytes = localStorageService.readDocument(invoice.getStoragePath());
        return new FileDownload(bytes, invoice.getFileName(), invoice.getContentType());
    }

    @Transactional
    public void deleteInvoice(Long contractId, Long invoiceId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        ElectronicInvoice invoice = invoiceRepository.findByIdAndContractId(invoiceId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(INVOICE_NOT_FOUND, invoiceId, contractId)));

        localStorageService.deleteDocument(invoice.getStoragePath());
        invoiceRepository.delete(invoice);
    }

    @Transactional
    public ElectronicInvoiceDTO updatePaymentDetails(Long contractId, Long invoiceId, UpdateInvoicePaymentDetailsRequest request) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        ElectronicInvoice invoice = invoiceRepository.findByIdAndContractId(invoiceId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(INVOICE_NOT_FOUND, invoiceId, contractId)));

        if (invoice.getSepaBatch() != null) {
            throw new IllegalArgumentException("La fattura è già inclusa in un pagamento SEPA e non può più essere modificata");
        }

        String normalizedIban = request.supplierIban().replace(" ", "").toUpperCase(Locale.ROOT);
        if (!IbanValidator.isValid(normalizedIban)) {
            throw new IllegalArgumentException("IBAN non valido");
        }
        invoice.setSupplierIban(normalizedIban);
        invoice.setSupplierBic(request.supplierBic() != null
                ? request.supplierBic().replace(" ", "").toUpperCase(Locale.ROOT)
                : null);
        invoice.setPaymentDueDate(request.paymentDueDate());

        return toDTO(invoiceRepository.save(invoice));
    }

    @Transactional
    public ElectronicInvoiceDTO confirmMatch(Long contractId, Long invoiceId, ConfirmInvoiceMatchRequest request) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        ElectronicInvoice invoice = invoiceRepository.findByIdAndContractId(invoiceId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(INVOICE_NOT_FOUND, invoiceId, contractId)));

        Long financialValueId = request != null ? request.financialValueId() : null;
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return toDTO(invoiceMatchingService.confirmMatch(invoice, financialValueId, username));
    }

    @Transactional
    public ElectronicInvoiceDTO rejectMatch(Long contractId, Long invoiceId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        ElectronicInvoice invoice = invoiceRepository.findByIdAndContractId(invoiceId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(INVOICE_NOT_FOUND, invoiceId, contractId)));

        return toDTO(invoiceMatchingService.rejectMatch(invoice));
    }

    @Transactional
    public List<ElectronicInvoiceDTO> recomputeMatches(Long contractId) {
        contractAccessGuard.checkManagerCanAccess(contractAccessGuard.getContractInScope(contractId));
        return invoiceMatchingService.recomputeForContract(contractId).stream().map(this::toDTO).toList();
    }

    private void validateFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Il file è vuoto");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Il file supera il limite di 5 MB");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
        if (!content.startsWith("<?xml") && !content.startsWith("<")) {
            throw new IllegalArgumentException("Sono supportati solo file XML");
        }
    }

    private ElectronicInvoiceDTO toDTO(ElectronicInvoice invoice) {
        String downloadUrl = String.format("%s/contracts/%d/invoices/%d/download",
                backendBaseUrl, invoice.getContract().getId(), invoice.getId());
        return new ElectronicInvoiceDTO(
                invoice.getId(),
                invoice.getContract().getId(),
                invoice.getFileName(),
                invoice.getFileSize(),
                invoice.getUploadedAt(),
                downloadUrl,
                invoice.getSupplierName(),
                invoice.getSupplierVatNumber(),
                invoice.getDocumentType(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                deserializeLineItems(invoice.getLineItemsJson()),
                invoice.getSupplierIban(),
                invoice.getSupplierBic(),
                invoice.getPaymentDueDate(),
                invoice.getSepaBatch() != null ? invoice.getSepaBatch().getId() : null,
                invoice.getMatchStatus(),
                invoice.getMatchedFinancialValue() != null ? invoice.getMatchedFinancialValue().getId() : null,
                invoice.getMatchConfidence(),
                invoice.getMatchedAt(),
                invoice.getMatchedByUser() != null ? invoice.getMatchedByUser().getUsername() : null);
    }

    private List<InvoiceLineItemDTO> deserializeLineItems(String lineItemsJson) {
        try {
            return objectMapper.readValue(lineItemsJson, new TypeReference<List<InvoiceLineItemDTO>>() {});
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to deserialize invoice line items", e);
        }
    }
}
