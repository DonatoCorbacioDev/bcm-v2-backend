package com.donatodev.bcm_backend.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.donatodev.bcm_backend.dto.FatturaPaInvoiceData;
import com.donatodev.bcm_backend.dto.InvoiceLineItemDTO;

/**
 * Parses Italian electronic invoices (FatturaPA format) into {@link FatturaPaInvoiceData}.
 *
 * <p>Only the first {@code FatturaElettronicaBody} block is read; batch files
 * containing multiple invoice bodies are out of scope.</p>
 */
@Service
public class FatturaPaXmlParserService {

    private static final String INVALID_XML = "Invalid or malformed XML";
    private static final String INVALID_VALUE = "Invalid value in FatturaPA document";

    public FatturaPaInvoiceData parse(byte[] xmlBytes) {
        Element root = parseDocument(xmlBytes);

        if (!"FatturaElettronica".equals(localName(root))) {
            throw new IllegalArgumentException(INVALID_XML);
        }

        Element header = findFirstChildByLocalName(root, "FatturaElettronicaHeader");
        Element body = findFirstChildByLocalName(root, "FatturaElettronicaBody");

        String supplierName = null;
        String supplierVatNumber = null;
        if (header != null) {
            Element datiAnagrafici = findDescendantByLocalName(header, "CedentePrestatore", "DatiAnagrafici");
            if (datiAnagrafici != null) {
                supplierName = extractSupplierName(datiAnagrafici);
                supplierVatNumber = extractSupplierVatNumber(datiAnagrafici);
            }
        }

        String documentType = null;
        String invoiceNumber = null;
        LocalDate invoiceDate = null;
        BigDecimal totalAmount = null;
        String currency = null;
        List<InvoiceLineItemDTO> lineItems = new ArrayList<>();

        if (body != null) {
            Element datiGeneraliDocumento = findDescendantByLocalName(body, "DatiGenerali", "DatiGeneraliDocumento");
            if (datiGeneraliDocumento != null) {
                documentType = getTextOrNull(datiGeneraliDocumento, "TipoDocumento");
                invoiceNumber = getTextOrNull(datiGeneraliDocumento, "Numero");
                currency = getTextOrNull(datiGeneraliDocumento, "Divisa");
                invoiceDate = parseOptionalDate(getTextOrNull(datiGeneraliDocumento, "Data"));
                totalAmount = parseOptionalAmount(getTextOrNull(datiGeneraliDocumento, "ImportoTotaleDocumento"));
            }

            lineItems = extractLineItems(body);
        }

        return new FatturaPaInvoiceData(supplierName, supplierVatNumber, documentType,
                invoiceNumber, invoiceDate, totalAmount, currency, lineItems);
    }

    private String extractSupplierName(Element datiAnagrafici) {
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

    private String extractSupplierVatNumber(Element datiAnagrafici) {
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

    private BigDecimal parseOptionalAmount(String text) {
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(INVALID_VALUE, e);
        }
    }

    private LocalDate parseOptionalDate(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(INVALID_VALUE, e);
        }
    }

    private Integer parseOptionalInt(String text) {
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
            return document.getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException(INVALID_XML, e);
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
