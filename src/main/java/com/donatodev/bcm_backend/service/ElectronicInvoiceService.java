package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ElectronicInvoiceDTO;
import com.donatodev.bcm_backend.dto.FatturaPaInvoiceData;
import com.donatodev.bcm_backend.dto.InvoiceLineItemDTO;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ElectronicInvoiceService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;
    private static final String INVOICE_NOT_FOUND = "Invoice ID %d not found for contract %d";

    @Value("${app.backend-base-url:http://localhost:8090/api/v1}")
    private String backendBaseUrl;

    private final ElectronicInvoiceRepository invoiceRepository;
    private final ContractsRepository contractsRepository;
    private final LocalStorageService localStorageService;
    private final FatturaPaXmlParserService fatturaPaXmlParserService;
    private final ObjectMapper objectMapper;

    public ElectronicInvoiceService(ElectronicInvoiceRepository invoiceRepository,
                                     ContractsRepository contractsRepository,
                                     LocalStorageService localStorageService,
                                     FatturaPaXmlParserService fatturaPaXmlParserService,
                                     ObjectMapper objectMapper) {
        this.invoiceRepository = invoiceRepository;
        this.contractsRepository = contractsRepository;
        this.localStorageService = localStorageService;
        this.fatturaPaXmlParserService = fatturaPaXmlParserService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ElectronicInvoiceDTO uploadInvoice(Long contractId, MultipartFile file) throws IOException {
        Contracts contract = getContractInScope(contractId);

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
                .build());

        return toDTO(invoice);
    }

    @Transactional(readOnly = true)
    public List<ElectronicInvoiceDTO> getInvoices(Long contractId) {
        getContractInScope(contractId);
        return invoiceRepository.findByContractIdOrderByUploadedAtDesc(contractId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ElectronicInvoiceDTO getInvoice(Long contractId, Long invoiceId) {
        getContractInScope(contractId);
        ElectronicInvoice invoice = invoiceRepository.findByIdAndContractId(invoiceId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(INVOICE_NOT_FOUND, invoiceId, contractId)));
        return toDTO(invoice);
    }

    @Transactional(readOnly = true)
    public FileDownload downloadInvoice(Long contractId, Long invoiceId) {
        getContractInScope(contractId);
        ElectronicInvoice invoice = invoiceRepository.findByIdAndContractId(invoiceId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(INVOICE_NOT_FOUND, invoiceId, contractId)));

        byte[] bytes = localStorageService.readDocument(invoice.getStoragePath());
        return new FileDownload(bytes, invoice.getFileName(), invoice.getContentType());
    }

    @Transactional
    public void deleteInvoice(Long contractId, Long invoiceId) {
        getContractInScope(contractId);
        ElectronicInvoice invoice = invoiceRepository.findByIdAndContractId(invoiceId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(
                        String.format(INVOICE_NOT_FOUND, invoiceId, contractId)));

        localStorageService.deleteDocument(invoice.getStoragePath());
        invoiceRepository.delete(invoice);
    }

    /**
     * Finds the contract by ID, scoped to the current tenant when
     * {@link TenantContext} carries an organization ID. Used to prevent
     * cross-tenant access to a contract's invoices by ID.
     */
    private Contracts getContractInScope(Long contractId) {
        Long orgId = TenantContext.get();
        Optional<Contracts> contract = (orgId != null)
                ? contractsRepository.findByIdAndOrganization_Id(contractId, orgId)
                : contractsRepository.findById(contractId);
        return contract.orElseThrow(() -> new ContractNotFoundException("Contract ID " + contractId + " not found"));
    }

    private void validateFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds the 5 MB limit");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
        if (!content.startsWith("<?xml") && !content.startsWith("<")) {
            throw new IllegalArgumentException("Only XML files are accepted");
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
                deserializeLineItems(invoice.getLineItemsJson()));
    }

    private List<InvoiceLineItemDTO> deserializeLineItems(String lineItemsJson) {
        try {
            return objectMapper.readValue(lineItemsJson, new TypeReference<List<InvoiceLineItemDTO>>() {});
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to deserialize invoice line items", e);
        }
    }
}
