package com.donatodev.bcm_backend.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.SepaPaymentBatchDTO;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.SepaPaymentBatch;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.SepaPaymentBatchRepository;

@Service
public class SepaPaymentService {

    private static final String PAIN_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.03";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String NOT_PROVIDED = "NOTPROVIDED";
    private static final String BATCH_NOT_FOUND = "Lotto pagamento SEPA ID %d non trovato per il contratto %d";

    private final ContractAccessGuard contractAccessGuard;
    private final ElectronicInvoiceRepository invoiceRepository;
    private final OrganizationRepository organizationRepository;
    private final SepaPaymentBatchRepository batchRepository;
    private final LocalStorageService localStorageService;

    public SepaPaymentService(ContractAccessGuard contractAccessGuard,
                               ElectronicInvoiceRepository invoiceRepository,
                               OrganizationRepository organizationRepository,
                               SepaPaymentBatchRepository batchRepository,
                               LocalStorageService localStorageService) {
        this.contractAccessGuard = contractAccessGuard;
        this.invoiceRepository = invoiceRepository;
        this.organizationRepository = organizationRepository;
        this.batchRepository = batchRepository;
        this.localStorageService = localStorageService;
    }

    @Transactional
    public FileDownload createSepaPayment(Long contractId, List<Long> invoiceIds, LocalDate requestedExecutionDate) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);

        List<ElectronicInvoice> invoices = invoiceRepository.findByContractIdAndIdIn(contractId, invoiceIds);
        if (invoices.size() != invoiceIds.size()) {
            throw new IllegalArgumentException("Una o più fatture non sono state trovate per questo contratto");
        }

        Organization organization = resolveOrganization(contract);
        if (organization.getIban() == null || organization.getIban().isBlank()) {
            throw new IllegalArgumentException("L'organizzazione non ha un IBAN configurato per i pagamenti SEPA");
        }

        String currency = resolveCurrency(invoices);
        for (ElectronicInvoice invoice : invoices) {
            if (invoice.getSepaBatch() != null) {
                throw new IllegalArgumentException("La fattura " + invoice.getId() + " è già inclusa in un pagamento SEPA");
            }
            if (invoice.getSupplierIban() == null || invoice.getSupplierIban().isBlank()) {
                throw new IllegalArgumentException("La fattura " + invoice.getId() + " non ha un IBAN fornitore");
            }
        }

        LocalDate executionDate = requestedExecutionDate != null
                ? requestedExecutionDate
                : LocalDate.now(ZoneId.systemDefault());
        if (executionDate.isBefore(LocalDate.now(ZoneId.systemDefault()))) {
            throw new IllegalArgumentException("La data di esecuzione non può essere nel passato");
        }

        BigDecimal totalAmount = invoices.stream()
                .map(ElectronicInvoice::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        String messageId = "MSG" + UUID.randomUUID().toString().replace("-", "").substring(0, 28).toUpperCase(Locale.ROOT);
        byte[] xmlBytes = buildXml(organization, invoices, currency, executionDate, messageId, totalAmount);

        Long orgId = TenantContext.get();
        String storagePath = localStorageService.storeSepaPayment(orgId, contractId, xmlBytes);
        String fileName = "sepa-" + contractId + "-" + executionDate + ".xml";

        SepaPaymentBatch batch = batchRepository.save(SepaPaymentBatch.builder()
                .contract(contract)
                .orgId(orgId)
                .messageId(messageId)
                .executionDate(executionDate)
                .totalAmount(totalAmount)
                .currency(currency)
                .numberOfTransactions(invoices.size())
                .storagePath(storagePath)
                .fileName(fileName)
                .build());

        for (ElectronicInvoice invoice : invoices) {
            invoice.setSepaBatch(batch);
        }
        invoiceRepository.saveAll(invoices);

        return new FileDownload(xmlBytes, fileName, "application/xml");
    }

    @Transactional(readOnly = true)
    public List<SepaPaymentBatchDTO> getPayments(Long contractId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        return batchRepository.findByContractIdOrderByCreatedAtDesc(contractId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public FileDownload downloadPayment(Long contractId, Long batchId) {
        Contracts contract = contractAccessGuard.getContractInScope(contractId);
        contractAccessGuard.checkManagerCanAccess(contract);
        SepaPaymentBatch batch = batchRepository.findByIdAndContractId(batchId, contractId)
                .orElseThrow(() -> new ContractNotFoundException(String.format(BATCH_NOT_FOUND, batchId, contractId)));

        byte[] bytes = localStorageService.readDocument(batch.getStoragePath());
        return new FileDownload(bytes, batch.getFileName(), "application/xml");
    }

    private SepaPaymentBatchDTO toDTO(SepaPaymentBatch batch) {
        return new SepaPaymentBatchDTO(
                batch.getId(),
                batch.getContract().getId(),
                batch.getExecutionDate(),
                batch.getTotalAmount(),
                batch.getCurrency(),
                batch.getNumberOfTransactions(),
                batch.getFileName(),
                batch.getCreatedAt());
    }

    private Organization resolveOrganization(Contracts contract) {
        Long orgId = TenantContext.get();
        if (orgId != null) {
            return organizationRepository.findById(orgId)
                    .orElseThrow(() -> new IllegalArgumentException("Organizzazione non trovata"));
        }
        if (contract.getOrganization() != null) {
            return contract.getOrganization();
        }
        throw new IllegalArgumentException("Il contratto non ha un'organizzazione da cui risolvere un conto debitore");
    }

    private String resolveCurrency(List<ElectronicInvoice> invoices) {
        String currency = null;
        for (ElectronicInvoice invoice : invoices) {
            String invoiceCurrency = invoice.getCurrency() != null ? invoice.getCurrency() : "EUR";
            if (currency == null) {
                currency = invoiceCurrency;
            } else if (!currency.equals(invoiceCurrency)) {
                throw new IllegalArgumentException("Non è possibile mischiare valute diverse in un unico pagamento SEPA");
            }
        }
        return currency != null ? currency : "EUR";
    }

    private byte[] buildXml(Organization organization, List<ElectronicInvoice> invoices, String currency,
                             LocalDate executionDate, String messageId, BigDecimal totalAmount) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element root = doc.createElementNS(PAIN_NAMESPACE, "Document");
            doc.appendChild(root);

            Element cstmrCdtTrfInitn = append(doc, root, "CstmrCdtTrfInitn");

            Element grpHdr = append(doc, cstmrCdtTrfInitn, "GrpHdr");
            appendText(doc, grpHdr, "MsgId", messageId);
            appendText(doc, grpHdr, "CreDtTm", Instant.now().toString());
            appendText(doc, grpHdr, "NbOfTxs", String.valueOf(invoices.size()));
            appendText(doc, grpHdr, "CtrlSum", totalAmount.toPlainString());
            Element initgPty = append(doc, grpHdr, "InitgPty");
            appendText(doc, initgPty, "Nm", sanitize(organization.getName(), 70));

            Element pmtInf = append(doc, cstmrCdtTrfInitn, "PmtInf");
            appendText(doc, pmtInf, "PmtInfId", "PMT" + messageId.substring(3));
            appendText(doc, pmtInf, "PmtMtd", "TRF");
            appendText(doc, pmtInf, "BtchBookg", "true");
            appendText(doc, pmtInf, "NbOfTxs", String.valueOf(invoices.size()));
            appendText(doc, pmtInf, "CtrlSum", totalAmount.toPlainString());
            Element pmtTpInf = append(doc, pmtInf, "PmtTpInf");
            Element svcLvl = append(doc, pmtTpInf, "SvcLvl");
            appendText(doc, svcLvl, "Cd", "SEPA");
            appendText(doc, pmtInf, "ReqdExctnDt", executionDate.format(DATE_FMT));

            Element dbtr = append(doc, pmtInf, "Dbtr");
            appendText(doc, dbtr, "Nm", sanitize(organization.getName(), 70));
            Element dbtrAcct = append(doc, pmtInf, "DbtrAcct");
            Element dbtrAcctId = append(doc, dbtrAcct, "Id");
            appendText(doc, dbtrAcctId, "IBAN", organization.getIban());
            appendAgent(doc, pmtInf, "DbtrAgt", organization.getBic());
            appendText(doc, pmtInf, "ChrgBr", "SLEV");

            for (ElectronicInvoice invoice : invoices) {
                Element txInf = append(doc, pmtInf, "CdtTrfTxInf");
                Element pmtId = append(doc, txInf, "PmtId");
                String endToEndId = invoice.getInvoiceNumber() != null
                        ? sanitize(invoice.getInvoiceNumber(), 35)
                        : NOT_PROVIDED;
                appendText(doc, pmtId, "EndToEndId", endToEndId.isEmpty() ? NOT_PROVIDED : endToEndId);

                Element amt = append(doc, txInf, "Amt");
                Element instdAmt = append(doc, amt, "InstdAmt");
                BigDecimal invoiceAmount = invoice.getTotalAmount() != null
                        ? invoice.getTotalAmount().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                instdAmt.setAttribute("Ccy", currency);
                instdAmt.setTextContent(invoiceAmount.toPlainString());

                appendAgent(doc, txInf, "CdtrAgt", invoice.getSupplierBic());

                Element cdtr = append(doc, txInf, "Cdtr");
                appendText(doc, cdtr, "Nm", sanitize(invoice.getSupplierName(), 70));

                Element cdtrAcct = append(doc, txInf, "CdtrAcct");
                Element cdtrAcctId = append(doc, cdtrAcct, "Id");
                appendText(doc, cdtrAcctId, "IBAN", invoice.getSupplierIban());

                Element rmtInf = append(doc, txInf, "RmtInf");
                String remittance = invoice.getInvoiceNumber() != null
                        ? "Fattura " + invoice.getInvoiceNumber()
                        : "Pagamento fattura " + invoice.getId();
                appendText(doc, rmtInf, "Ustrd", sanitize(remittance, 140));
            }

            return serialize(doc);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Failed to build SEPA payment XML", e);
        }
    }

    private void appendAgent(Document doc, Element parent, String agentTag, String bic) {
        Element agt = append(doc, parent, agentTag);
        Element finInstnId = append(doc, agt, "FinInstnId");
        if (bic != null && !bic.isBlank()) {
            appendText(doc, finInstnId, "BIC", bic);
        } else {
            Element othr = append(doc, finInstnId, "Othr");
            appendText(doc, othr, "Id", NOT_PROVIDED);
        }
    }

    private Element append(Document doc, Element parent, String tagName) {
        Element element = doc.createElement(tagName);
        parent.appendChild(element);
        return element;
    }

    private void appendText(Document doc, Element parent, String tagName, String text) {
        Element element = doc.createElement(tagName);
        element.setTextContent(text);
        parent.appendChild(element);
    }

    private String sanitize(String input, int maxLen) {
        if (input == null) {
            return "";
        }
        String stripped = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String sanitized = stripped.replaceAll("[^A-Za-z0-9/\\-?:().,'+ ]", "").trim();
        return sanitized.length() > maxLen ? sanitized.substring(0, maxLen) : sanitized;
    }

    private byte[] serialize(Document doc) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(out));
            return out.toByteArray();
        } catch (TransformerException e) {
            throw new UncheckedIOException(new IOException("Failed to serialize SEPA payment XML", e));
        }
    }
}
