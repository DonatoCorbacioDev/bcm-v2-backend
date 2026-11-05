/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.donatodev.bcm_backend.util.AuthenticatedUserUtils;
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
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.InvoiceLineItem;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.DuplicateInvoiceException;
import com.donatodev.bcm_backend.mapper.CounterpartyMapper;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.util.IbanValidator;

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

    public ElectronicInvoiceService(ElectronicInvoiceRepository invoiceRepository,
                                     ContractAccessGuard contractAccessGuard,
                                     LocalStorageService localStorageService,
                                     FatturaPaXmlParserService fatturaPaXmlParserService,
                                     InvoiceMatchingService invoiceMatchingService) {
        this.invoiceRepository = invoiceRepository;
        this.contractAccessGuard = contractAccessGuard;
        this.localStorageService = localStorageService;
        this.fatturaPaXmlParserService = fatturaPaXmlParserService;
        this.invoiceMatchingService = invoiceMatchingService;
    }

    @Transactional(rollbackFor = IOException.class)
    public ElectronicInvoiceDTO uploadInvoice(Long contractId, MultipartFile file) throws IOException {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);

        validateFile(file);

        byte[] bytes = file.getBytes();
        FatturaPaInvoiceData parsed = fatturaPaXmlParserService.parse(bytes);

        Long orgId = TenantContext.get();

        if (invoiceRepository.existsByOrgIdAndSupplierVatNumberAndInvoiceNumberAndDocumentType(
                orgId, parsed.supplierVatNumber(), parsed.invoiceNumber(), parsed.documentType())) {
            throw new DuplicateInvoiceException(
                    "Questa fattura risulta già caricata (fornitore, numero e tipo documento coincidono)");
        }

        String storagePath = localStorageService.storeInvoice(orgId, contractId, bytes);

        ElectronicInvoice invoice = ElectronicInvoice.builder()
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
                .taxableAmount(parsed.taxableAmount())
                .vatAmount(parsed.vatAmount())
                .currency(parsed.currency())
                .supplierIban(parsed.supplierIban())
                .supplierBic(parsed.supplierBic())
                .paymentDueDate(parsed.paymentDueDate())
                .build();
        invoice.setLineItems(toLineItemEntities(parsed.lineItems(), invoice));
        invoice = invoiceRepository.save(invoice);

        try {
            invoiceMatchingService.computeSuggestion(invoice);
            invoice = invoiceRepository.save(invoice);
        } catch (RuntimeException e) {
            String safeMessage = e.getMessage() == null ? null : e.getMessage().replaceAll(CRLF_REGEX, "_");
            logger.warn("Invoice match suggestion failed for invoice {}: {}", invoice.getId(), safeMessage);
        }

        fillCounterpartyAddress(contract.getCounterparty(), parsed);
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

        if (request.supplierIban() != null) {
            String normalizedIban = request.supplierIban().replace(" ", "").toUpperCase(Locale.ROOT);
            if (!normalizedIban.isEmpty() && !IbanValidator.isValid(normalizedIban)) {
                throw new IllegalArgumentException("IBAN non valido");
            }
            invoice.setSupplierIban(normalizedIban.isEmpty() ? null : normalizedIban);
        }
        if (request.supplierBic() != null) {
            String normalizedBic = request.supplierBic().replace(" ", "").toUpperCase(Locale.ROOT);
            invoice.setSupplierBic(normalizedBic.isEmpty() ? null : normalizedBic);
        }
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
        String username = AuthenticatedUserUtils.getUsernameOrThrow();
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

    /**
     * Fills in the supplier's address from its invoice, so nobody types what
     * the invoice already says. Only when the invoice's VAT number is the
     * counterparty's own (an invoice uploaded on the wrong contract must not
     * rewrite another company's data), and only the fields still empty:
     * whatever a person entered is never overwritten. The counterparty is a
     * managed entity: the change is saved with the invoice's transaction.
     */
    static void fillCounterpartyAddress(Counterparty counterparty, FatturaPaInvoiceData parsed) {
        FatturaPaInvoiceData.SupplierAddress address = parsed.supplierAddress();
        if (counterparty == null || address == null) {
            return;
        }
        String counterpartyVat = InvoiceMatchingService.normalizeVat(counterparty.getVatNumber());
        if (counterpartyVat == null || !counterpartyVat.equals(InvoiceMatchingService.normalizeVat(parsed.supplierVatNumber()))) {
            return;
        }
        if (isBlank(counterparty.getAddress())) counterparty.setAddress(address.street());
        if (isBlank(counterparty.getPostalCode())) counterparty.setPostalCode(address.postalCode());
        if (isBlank(counterparty.getCity())) counterparty.setCity(address.city());
        if (isBlank(counterparty.getProvince())) counterparty.setProvince(CounterpartyMapper.upperOrNull(address.province()));
        if (isBlank(counterparty.getCountry())) counterparty.setCountry(CounterpartyMapper.upperOrNull(address.country()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
                toLineItemDTOs(invoice.getLineItems()),
                IbanValidator.maskNullable(invoice.getSupplierIban()),
                IbanValidator.maskNullable(invoice.getSupplierBic()),
                invoice.getPaymentDueDate(),
                invoice.getSepaBatch() != null ? invoice.getSepaBatch().getId() : null,
                invoice.getMatchStatus(),
                invoice.getMatchedFinancialValue() != null ? invoice.getMatchedFinancialValue().getId() : null,
                invoice.getMatchConfidence(),
                invoice.getMatchedAt(),
                invoice.getMatchedByUser() != null ? invoice.getMatchedByUser().getUsername() : null,
                invoice.getTaxableAmount(),
                invoice.getVatAmount(),
                expectedAmount(invoice),
                amountDifference(invoice),
                // Same check as the home page's "IBAN cambiato" alert, so the two never disagree.
                HomeAttentionService.ibanChanged(invoice, invoice.getContract().getCounterparty()));
    }

    /** The amount the contract expected for the period the invoice is matched (or suggested) to. */
    private static BigDecimal expectedAmount(ElectronicInvoice invoice) {
        return invoice.getMatchedFinancialValue() != null
                ? BigDecimal.valueOf(invoice.getMatchedFinancialValue().getFinancialAmount()).setScale(2, java.math.RoundingMode.HALF_UP)
                : null;
    }

    /**
     * Invoice taxable amount minus the expected one, to be checked by a
     * person (a difference can be a valid variation, not an error). Not given
     * for a credit note, which is not an instalment.
     */
    private static BigDecimal amountDifference(ElectronicInvoice invoice) {
        BigDecimal expected = expectedAmount(invoice);
        BigDecimal actual = invoice.comparableAmount();
        if (expected == null || actual == null || ElectronicInvoice.CREDIT_NOTE.equals(invoice.getDocumentType())) {
            return null;
        }
        return actual.subtract(expected);
    }

    private List<InvoiceLineItem> toLineItemEntities(List<InvoiceLineItemDTO> dtos, ElectronicInvoice invoice) {
        // Mutable on purpose: Hibernate wraps this in a PersistentBag and needs
        // to clear()/repopulate it on a later merge (e.g. the second save() in
        // uploadInvoice, after computeSuggestion sets the match fields) -- an
        // immutable list here throws UnsupportedOperationException at that point.
        return dtos.stream()
                .map(dto -> InvoiceLineItem.builder()
                        .invoice(invoice)
                        .lineNumber(dto.lineNumber())
                        .description(dto.description())
                        .quantity(dto.quantity())
                        .unitOfMeasure(dto.unitOfMeasure())
                        .unitPrice(dto.unitPrice())
                        .totalPrice(dto.totalPrice())
                        .vatRate(dto.vatRate())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<InvoiceLineItemDTO> toLineItemDTOs(List<InvoiceLineItem> lineItems) {
        return lineItems.stream()
                .map(item -> new InvoiceLineItemDTO(
                        item.getLineNumber(),
                        item.getDescription(),
                        item.getQuantity(),
                        item.getUnitOfMeasure(),
                        item.getUnitPrice(),
                        item.getTotalPrice(),
                        item.getVatRate()))
                .toList();
    }
}
