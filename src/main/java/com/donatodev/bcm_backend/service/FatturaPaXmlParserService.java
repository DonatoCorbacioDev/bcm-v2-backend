/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.donatodev.bcm_backend.dto.FatturaPaInvoiceData;
import com.donatodev.bcm_backend.dto.InvoiceLineItemDTO;
import com.donatodev.bcm_backend.util.IbanValidator;

import lombok.extern.slf4j.Slf4j;

/**
 * Parses Italian electronic invoices (FatturaPA format) into {@link FatturaPaInvoiceData}.
 *
 * <p>Only the first {@code FatturaElettronicaBody} block is read; batch files
 * containing multiple invoice bodies are out of scope.</p>
 *
 * <p>Every document is validated against the official FatturaPA XSD (v1.2.2,
 * bundled under {@code src/main/resources/fatturapa/} — never fetched from
 * the network at runtime, see that directory's schema file for why) before
 * any field is extracted. This rejects well-formed-but-non-conformant XML
 * (missing mandatory blocks, wrong element order, out-of-range enum values,
 * etc.) that the lenient by-local-name extraction below would otherwise
 * silently tolerate.</p>
 */
@Slf4j
@Service
public class FatturaPaXmlParserService {

    private static final String INVALID_XML = "Invalid or malformed XML";
    private static final String INVALID_SCHEMA = "XML does not conform to the FatturaPA schema";
    private static final String INVALID_VALUE = "Invalid value in FatturaPA document";
    private static final String SCHEMA_RESOURCE_PATH = "/fatturapa/Schema_del_file_xml_FatturaPA_v1.2.2.xsd";

    private static final Schema FATTURAPA_SCHEMA = loadSchema();

    public FatturaPaInvoiceData parse(byte[] xmlBytes) {
        // No root-element-name check here: schema validation in parseDocument
        // already rejects a document with the wrong root element (it can't
        // find a matching global element declaration), with a clearer error
        // than this class could produce on its own.
        Element root = parseDocument(xmlBytes);

        // FatturaElettronicaHeader > CedentePrestatore > DatiAnagrafici and
        // FatturaElettronicaBody > DatiGenerali > DatiGeneraliDocumento are
        // all mandatory per the schema (no minOccurs="0" anywhere on that
        // path), so they're guaranteed present once schema validation above
        // has passed -- no defensive null-guards needed for them.
        Element header = findFirstChildByLocalName(root, "FatturaElettronicaHeader");
        Element datiAnagrafici = findDescendantByLocalName(header, "CedentePrestatore", "DatiAnagrafici");
        String supplierName = extractSupplierName(datiAnagrafici);
        String supplierVatNumber = extractSupplierVatNumber(datiAnagrafici);

        Element body = findFirstChildByLocalName(root, "FatturaElettronicaBody");
        Element datiGeneraliDocumento = findDescendantByLocalName(body, "DatiGenerali", "DatiGeneraliDocumento");
        String documentType = getTextOrNull(datiGeneraliDocumento, "TipoDocumento");
        String invoiceNumber = getTextOrNull(datiGeneraliDocumento, "Numero");
        String currency = getTextOrNull(datiGeneraliDocumento, "Divisa");
        LocalDate invoiceDate = parseOptionalDate(getTextOrNull(datiGeneraliDocumento, "Data"));
        BigDecimal totalAmount = parseOptionalAmount(getTextOrNull(datiGeneraliDocumento, "ImportoTotaleDocumento"));

        List<InvoiceLineItemDTO> lineItems = extractLineItems(body);

        // DatiPagamento is genuinely optional (minOccurs="0"): not every
        // invoice carries payment terms.
        String supplierIban = null;
        String supplierBic = null;
        LocalDate paymentDueDate = null;
        Element dettaglioPagamento = findDescendantByLocalName(body, "DatiPagamento", "DettaglioPagamento");
        if (dettaglioPagamento != null) {
            supplierIban = extractSupplierIban(dettaglioPagamento);
            supplierBic = getTextOrNull(dettaglioPagamento, "BIC");
            paymentDueDate = parseOptionalDate(getTextOrNull(dettaglioPagamento, "DataScadenzaPagamento"));
        }

        return new FatturaPaInvoiceData(supplierName, supplierVatNumber, documentType,
                invoiceNumber, invoiceDate, totalAmount, currency, lineItems,
                supplierIban, supplierBic, paymentDueDate);
    }

    String extractSupplierIban(Element dettaglioPagamento) {
        String rawIban = getTextOrNull(dettaglioPagamento, "IBAN");
        if (rawIban == null) {
            return null;
        }
        String normalized = rawIban.replace(" ", "").toUpperCase(Locale.ROOT);
        if (!IbanValidator.isValid(normalized)) {
            log.warn("Ignoring malformed IBAN found in FatturaPA DatiPagamento block");
            return null;
        }
        return normalized;
    }

    String extractSupplierName(Element datiAnagrafici) {
        Element anagrafica = findFirstChildByLocalName(datiAnagrafici, "Anagrafica");
        if (anagrafica == null) {
            return null;
        }
        String denominazione = getTextOrNull(anagrafica, "Denominazione");
        if (denominazione != null) {
            return denominazione;
        }
        String nome = getTextOrNull(anagrafica, "Nome");
        String cognome = getTextOrNull(anagrafica, "Cognome");
        if (nome != null && cognome != null) {
            return nome + " " + cognome;
        }
        return null;
    }

    String extractSupplierVatNumber(Element datiAnagrafici) {
        Element idFiscaleIVA = findFirstChildByLocalName(datiAnagrafici, "IdFiscaleIVA");
        if (idFiscaleIVA != null) {
            String idPaese = getTextOrNull(idFiscaleIVA, "IdPaese");
            String idCodice = getTextOrNull(idFiscaleIVA, "IdCodice");
            if (idPaese != null && idCodice != null) {
                return idPaese + idCodice;
            }
        }
        return getTextOrNull(datiAnagrafici, "CodiceFiscale");
    }

    private List<InvoiceLineItemDTO> extractLineItems(Element body) {
        List<InvoiceLineItemDTO> items = new ArrayList<>();
        for (Element datiBeniServizi : findChildrenByLocalName(body, "DatiBeniServizi")) {
            for (Element dettaglioLinee : findChildrenByLocalName(datiBeniServizi, "DettaglioLinee")) {
                items.add(toLineItem(dettaglioLinee));
            }
        }
        return items;
    }

    private InvoiceLineItemDTO toLineItem(Element dettaglioLinee) {
        Integer lineNumber = parseOptionalInt(getTextOrNull(dettaglioLinee, "NumeroLinea"));
        String description = getTextOrNull(dettaglioLinee, "Descrizione");
        BigDecimal quantity = parseOptionalAmount(getTextOrNull(dettaglioLinee, "Quantita"));
        String unitOfMeasure = getTextOrNull(dettaglioLinee, "UnitaMisura");
        BigDecimal unitPrice = parseOptionalAmount(getTextOrNull(dettaglioLinee, "PrezzoUnitario"));
        BigDecimal totalPrice = parseOptionalAmount(getTextOrNull(dettaglioLinee, "PrezzoTotale"));
        BigDecimal vatRate = parseOptionalAmount(getTextOrNull(dettaglioLinee, "AliquotaIVA"));
        return new InvoiceLineItemDTO(lineNumber, description, quantity, unitOfMeasure, unitPrice, totalPrice, vatRate);
    }

    BigDecimal parseOptionalAmount(String text) {
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(INVALID_VALUE, e);
        }
    }

    LocalDate parseOptionalDate(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(INVALID_VALUE, e);
        }
    }

    Integer parseOptionalInt(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(INVALID_VALUE, e);
        }
    }

    private Element parseDocument(byte[] xmlBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlBytes));
            validateAgainstSchema(document);
            return document.getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException(INVALID_XML, e);
        }
    }

    private void validateAgainstSchema(Document document) {
        try {
            Validator validator = FATTURAPA_SCHEMA.newValidator();
            validator.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            validator.validate(new DOMSource(document));
        } catch (SAXException | IOException e) {
            throw new IllegalArgumentException(INVALID_SCHEMA, e);
        }
    }

    private static Schema loadSchema() {
        return loadSchema(SCHEMA_RESOURCE_PATH);
    }

    // Resource path is a parameter (rather than always reading the private
    // constant) so tests can exercise the "resource missing" / "resource
    // malformed" failure paths below without corrupting the real bundled
    // schema.
    static Schema loadSchema(String resourcePath) {
        try (InputStream in = FatturaPaXmlParserService.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Bundled FatturaPA schema not found on classpath: " + resourcePath);
            }
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            // FEATURE_SECURE_PROCESSING denies external schema access by
            // default, which also blocks the <xs:import> below resolving its
            // own sibling file on the classpath. Scoped to local schemes only
            // (never "http") so this stays a purely local, network-free
            // resolution -- see xmldsig-core-schema.xsd's own comment.
            // "nested" is required in production: Spring Boot's executable jar
            // layout resolves classpath resources via jar:nested:... URLs,
            // not the plain jar:/file: URLs seen under Maven/local test runs.
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar,nested");
            StreamSource source = new StreamSource(in);
            source.setSystemId(FatturaPaXmlParserService.class.getResource(resourcePath).toString());
            return factory.newSchema(source);
        } catch (IOException | SAXException e) {
            throw new IllegalStateException("Failed to compile the bundled FatturaPA schema", e);
        }
    }

    static String localName(Node node) {
        String localName = node.getLocalName();
        if (localName != null) {
            return localName;
        }
        String nodeName = node.getNodeName();
        int colonIndex = nodeName.indexOf(':');
        return colonIndex >= 0 ? nodeName.substring(colonIndex + 1) : nodeName;
    }

    static Element findFirstChildByLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName(child).equals(localName)) {
                return (Element) child;
            }
        }
        return null;
    }

    static List<Element> findChildrenByLocalName(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName(child).equals(localName)) {
                result.add((Element) child);
            }
        }
        return result;
    }

    static Element findDescendantByLocalName(Element root, String... path) {
        Element current = root;
        for (String segment : path) {
            current = findFirstChildByLocalName(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    static String getTextOrNull(Element parent, String localName) {
        Element child = findFirstChildByLocalName(parent, localName);
        if (child == null) {
            return null;
        }
        String text = child.getTextContent();
        if (text == null) {
            return null;
        }
        text = text.trim();
        return text.isEmpty() ? null : text;
    }
}
