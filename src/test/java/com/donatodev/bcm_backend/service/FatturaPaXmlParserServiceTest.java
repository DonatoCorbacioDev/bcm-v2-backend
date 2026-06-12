package com.donatodev.bcm_backend.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.donatodev.bcm_backend.dto.FatturaPaInvoiceData;
import com.donatodev.bcm_backend.dto.InvoiceLineItemDTO;

class FatturaPaXmlParserServiceTest {

    private final FatturaPaXmlParserService parserService = new FatturaPaXmlParserService();

    private byte[] loadResource(String name) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/" + name)) {
            assertNotNull(is, "Test resource not found: " + name);
            return is.readAllBytes();
        }
    }

    private Element buildElement(String xml, boolean namespaceAware) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(namespaceAware);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return document.getDocumentElement();
    }

    @Nested
    @DisplayName("parse: valid FatturaPA documents")
    class ParseValidDocuments {

        @Test
        @DisplayName("full sample invoice: all fields and one line item extracted")
        void shouldParseSampleInvoice() throws Exception {
            FatturaPaInvoiceData data = parserService.parse(loadResource("fattura-pa-sample.xml"));

            assertEquals("Acme Forniture S.r.l.", data.supplierName());
            assertEquals("IT12345678901", data.supplierVatNumber());
            assertEquals("TD01", data.documentType());
            assertEquals("2024/001", data.invoiceNumber());
            assertEquals(LocalDate.of(2024, Month.MARCH, 15), data.invoiceDate());
            assertEquals(0, new BigDecimal("1220.00").compareTo(data.totalAmount()));
            assertEquals("EUR", data.currency());

            assertEquals(1, data.lineItems().size());
            InvoiceLineItemDTO line = data.lineItems().get(0);
            assertEquals(1, line.lineNumber());
            assertEquals("Servizio di consulenza informatica", line.description());
            assertEquals(0, new BigDecimal("10.00").compareTo(line.quantity()));
            assertEquals("HUR", line.unitOfMeasure());
            assertEquals(0, new BigDecimal("100.00").compareTo(line.unitPrice()));
            assertEquals(0, new BigDecimal("1000.00").compareTo(line.totalPrice()));
            assertEquals(0, new BigDecimal("22.00").compareTo(line.vatRate()));
        }

        @Test
        @DisplayName("minimal invoice: CodiceFiscale + Nome/Cognome fallback, optional fields null")
        void shouldParseMinimalInvoice() throws Exception {
            FatturaPaInvoiceData data = parserService.parse(loadResource("fattura-pa-minimal.xml"));

            assertEquals("Mario Rossi", data.supplierName());
            assertEquals("RSSMRA80A01H501U", data.supplierVatNumber());
            assertEquals("TD01", data.documentType());
            assertEquals("1", data.invoiceNumber());
            assertEquals(LocalDate.of(2024, Month.MAY, 1), data.invoiceDate());
            assertNull(data.totalAmount());
            assertEquals("EUR", data.currency());

            assertEquals(1, data.lineItems().size());
            InvoiceLineItemDTO line = data.lineItems().get(0);
            assertEquals(1, line.lineNumber());
            assertEquals("Prestazione occasionale", line.description());
            assertNull(line.quantity());
            assertNull(line.unitOfMeasure());
            assertEquals(0, new BigDecimal("500.00").compareTo(line.unitPrice()));
            assertEquals(0, new BigDecimal("500.00").compareTo(line.totalPrice()));
            assertEquals(0, BigDecimal.ZERO.compareTo(line.vatRate()));
        }

        @Test
        @DisplayName("multi-line invoice: line items flattened across DatiBeniServizi blocks, order preserved")
        void shouldParseMultiLineInvoice() throws Exception {
            FatturaPaInvoiceData data = parserService.parse(loadResource("fattura-pa-multi-line.xml"));

            assertEquals(3, data.lineItems().size());
            assertEquals("Prodotto A", data.lineItems().get(0).description());
            assertEquals("Prodotto B", data.lineItems().get(1).description());
            assertEquals("Servizio C", data.lineItems().get(2).description());
            assertEquals(1, data.lineItems().get(0).lineNumber());
            assertEquals(2, data.lineItems().get(1).lineNumber());
            assertEquals(3, data.lineItems().get(2).lineNumber());
        }
    }

    @Nested
    @DisplayName("parse: invalid documents are rejected")
    class ParseInvalidDocuments {

        @ParameterizedTest
        @ValueSource(strings = {
                "fattura-pa-malformed.xml",
                "fattura-pa-wrong-root.xml",
                "fattura-pa-xxe-attempt.xml"
        })
        @DisplayName("malformed XML, wrong root element, or XXE attempt throws IllegalArgumentException")
        void shouldRejectInvalidDocuments(String resourceName) throws Exception {
            byte[] xml = loadResource(resourceName);
            assertThrows(IllegalArgumentException.class, () -> parserService.parse(xml));
        }
    }

    @Nested
    @DisplayName("parse: optional fields and invalid values")
    class ParseOptionalAndInvalidValues {

        private static final String NO_ANAGRAFICA_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<FatturaElettronica>"
                + "<FatturaElettronicaHeader>"
                + "<CedentePrestatore><DatiAnagrafici>"
                + "<CodiceFiscale>RSSMRA80A01H501U</CodiceFiscale>"
                + "</DatiAnagrafici></CedentePrestatore>"
                + "</FatturaElettronicaHeader>"
                + "<FatturaElettronicaBody>"
                + "<DatiGenerali><DatiGeneraliDocumento>"
                + "<TipoDocumento>TD01</TipoDocumento><Numero>1</Numero><Divisa>EUR</Divisa>"
                + "</DatiGeneraliDocumento></DatiGenerali>"
                + "<DatiBeniServizi><DettaglioLinee>"
                + "<Descrizione>Test item</Descrizione>"
                + "<PrezzoUnitario>10.00</PrezzoUnitario><PrezzoTotale>10.00</PrezzoTotale>"
                + "<AliquotaIVA>22.00</AliquotaIVA>"
                + "</DettaglioLinee></DatiBeniServizi>"
                + "</FatturaElettronicaBody>"
                + "</FatturaElettronica>";

        private static final String EMPTY_ANAGRAFICA_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<FatturaElettronica>"
                + "<FatturaElettronicaHeader>"
                + "<CedentePrestatore><DatiAnagrafici>"
                + "<Anagrafica/>"
                + "<CodiceFiscale>RSSMRA80A01H501U</CodiceFiscale>"
                + "</DatiAnagrafici></CedentePrestatore>"
                + "</FatturaElettronicaHeader>"
                + "<FatturaElettronicaBody/>"
                + "</FatturaElettronica>";

        private static final String INVALID_DATE_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<FatturaElettronica>"
                + "<FatturaElettronicaHeader/>"
                + "<FatturaElettronicaBody>"
                + "<DatiGenerali><DatiGeneraliDocumento>"
                + "<TipoDocumento>TD01</TipoDocumento><Data>not-a-date</Data>"
                + "</DatiGeneraliDocumento></DatiGenerali>"
                + "</FatturaElettronicaBody>"
                + "</FatturaElettronica>";

        private static final String INVALID_AMOUNT_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<FatturaElettronica>"
                + "<FatturaElettronicaHeader/>"
                + "<FatturaElettronicaBody>"
                + "<DatiGenerali><DatiGeneraliDocumento>"
                + "<TipoDocumento>TD01</TipoDocumento><Data>2024-01-01</Data>"
                + "<ImportoTotaleDocumento>not-a-number</ImportoTotaleDocumento>"
                + "</DatiGeneraliDocumento></DatiGenerali>"
                + "</FatturaElettronicaBody>"
                + "</FatturaElettronica>";

        private static final String INVALID_LINE_NUMBER_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<FatturaElettronica>"
                + "<FatturaElettronicaHeader/>"
                + "<FatturaElettronicaBody>"
                + "<DatiGenerali><DatiGeneraliDocumento>"
                + "<TipoDocumento>TD01</TipoDocumento><Data>2024-01-01</Data>"
                + "</DatiGeneraliDocumento></DatiGenerali>"
                + "<DatiBeniServizi><DettaglioLinee>"
                + "<NumeroLinea>abc</NumeroLinea>"
                + "<Descrizione>Test item</Descrizione>"
                + "<PrezzoUnitario>10.00</PrezzoUnitario><PrezzoTotale>10.00</PrezzoTotale>"
                + "<AliquotaIVA>22.00</AliquotaIVA>"
                + "</DettaglioLinee></DatiBeniServizi>"
                + "</FatturaElettronicaBody>"
                + "</FatturaElettronica>";

        private static final String EMPTY_ROOT_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<FatturaElettronica/>";

        private static final String NOME_ONLY_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<FatturaElettronica>"
                + "<FatturaElettronicaHeader>"
                + "<CedentePrestatore><DatiAnagrafici>"
                + "<Anagrafica><Nome>Mario</Nome></Anagrafica>"
                + "</DatiAnagrafici></CedentePrestatore>"
                + "</FatturaElettronicaHeader>"
                + "<FatturaElettronicaBody/>"
                + "</FatturaElettronica>";

        private static final String EMPTY_ID_FISCALE_IVA_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<FatturaElettronica>"
                + "<FatturaElettronicaHeader>"
                + "<CedentePrestatore><DatiAnagrafici>"
                + "<IdFiscaleIVA/>"
                + "<CodiceFiscale>RSSMRA80A01H501U</CodiceFiscale>"
                + "</DatiAnagrafici></CedentePrestatore>"
                + "</FatturaElettronicaHeader>"
                + "<FatturaElettronicaBody/>"
                + "</FatturaElettronica>";

        private static final String PARTIAL_ID_FISCALE_IVA_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<FatturaElettronica>"
                + "<FatturaElettronicaHeader>"
                + "<CedentePrestatore><DatiAnagrafici>"
                + "<IdFiscaleIVA><IdPaese>IT</IdPaese></IdFiscaleIVA>"
                + "<CodiceFiscale>RSSMRA80A01H501U</CodiceFiscale>"
                + "</DatiAnagrafici></CedentePrestatore>"
                + "</FatturaElettronicaHeader>"
                + "<FatturaElettronicaBody/>"
                + "</FatturaElettronica>";

        @Test
        @DisplayName("missing Anagrafica/Data/NumeroLinea: supplierName, invoiceDate, lineNumber are null")
        void shouldReturnNullForMissingOptionalFields() {
            FatturaPaInvoiceData data = parserService.parse(NO_ANAGRAFICA_XML.getBytes(StandardCharsets.UTF_8));

            assertNull(data.supplierName());
            assertEquals("RSSMRA80A01H501U", data.supplierVatNumber());
            assertNull(data.invoiceDate());

            assertEquals(1, data.lineItems().size());
            assertNull(data.lineItems().get(0).lineNumber());
        }

        @Test
        @DisplayName("empty Anagrafica (no Denominazione/Nome/Cognome): supplierName is null")
        void shouldReturnNullSupplierNameWhenAnagraficaHasNoNameFields() {
            FatturaPaInvoiceData data = parserService.parse(EMPTY_ANAGRAFICA_XML.getBytes(StandardCharsets.UTF_8));

            assertNull(data.supplierName());
        }

        @Test
        @DisplayName("malformed Data throws IllegalArgumentException")
        void shouldRejectInvalidDate() {
            byte[] xml = INVALID_DATE_XML.getBytes(StandardCharsets.UTF_8);
            assertThrows(IllegalArgumentException.class, () -> parserService.parse(xml));
        }

        @Test
        @DisplayName("malformed ImportoTotaleDocumento throws IllegalArgumentException")
        void shouldRejectInvalidAmount() {
            byte[] xml = INVALID_AMOUNT_XML.getBytes(StandardCharsets.UTF_8);
            assertThrows(IllegalArgumentException.class, () -> parserService.parse(xml));
        }

        @Test
        @DisplayName("malformed NumeroLinea throws IllegalArgumentException")
        void shouldRejectInvalidLineNumber() {
            byte[] xml = INVALID_LINE_NUMBER_XML.getBytes(StandardCharsets.UTF_8);
            assertThrows(IllegalArgumentException.class, () -> parserService.parse(xml));
        }

        @Test
        @DisplayName("missing Header and Body elements entirely: all fields null, empty line items")
        void shouldHandleMissingHeaderAndBody() {
            FatturaPaInvoiceData data = parserService.parse(EMPTY_ROOT_XML.getBytes(StandardCharsets.UTF_8));

            assertNull(data.supplierName());
            assertNull(data.supplierVatNumber());
            assertNull(data.documentType());
            assertNull(data.invoiceNumber());
            assertNull(data.invoiceDate());
            assertNull(data.totalAmount());
            assertNull(data.currency());
            assertEquals(0, data.lineItems().size());
        }

        @Test
        @DisplayName("Anagrafica with Nome but no Cognome: supplierName is null")
        void shouldReturnNullSupplierNameWhenCognomeMissing() {
            FatturaPaInvoiceData data = parserService.parse(NOME_ONLY_XML.getBytes(StandardCharsets.UTF_8));

            assertNull(data.supplierName());
        }

        @Test
        @DisplayName("empty IdFiscaleIVA: falls back to CodiceFiscale")
        void shouldFallBackToCodiceFiscaleWhenIdFiscaleIvaEmpty() {
            FatturaPaInvoiceData data = parserService.parse(EMPTY_ID_FISCALE_IVA_XML.getBytes(StandardCharsets.UTF_8));

            assertEquals("RSSMRA80A01H501U", data.supplierVatNumber());
        }

        @Test
        @DisplayName("IdFiscaleIVA with only IdPaese: falls back to CodiceFiscale")
        void shouldFallBackToCodiceFiscaleWhenIdCodiceMissing() {
            FatturaPaInvoiceData data = parserService.parse(PARTIAL_ID_FISCALE_IVA_XML.getBytes(StandardCharsets.UTF_8));

            assertEquals("RSSMRA80A01H501U", data.supplierVatNumber());
        }
    }

    @Nested
    @DisplayName("local-name lookup helpers")
    class HelperMethods {

        private static final String XML = "<root xmlns:p=\"urn:test\">"
                + "<p:Child>value1</p:Child>"
                + "<Other>value2</Other>"
                + "<Repeating>a</Repeating>"
                + "<Repeating>b</Repeating>"
                + "<Empty></Empty>"
                + "<Nested><Inner>deep</Inner></Nested>"
                + "</root>";

        @Test
        @DisplayName("localName: namespace-aware element returns local name without prefix")
        void localNameNamespaceAware() throws Exception {
            Element root = buildElement(XML, true);
            Element child = FatturaPaXmlParserService.findFirstChildByLocalName(root, "Child");
            assertNotNull(child);
            assertEquals("Child", FatturaPaXmlParserService.localName(child));
        }

        @Test
        @DisplayName("localName: non-namespace-aware prefixed element strips prefix")
        void localNameStripsPrefixWhenNotNamespaceAware() throws Exception {
            Element root = buildElement(XML, false);
            Element child = FatturaPaXmlParserService.findFirstChildByLocalName(root, "Child");
            assertNotNull(child);
            assertEquals("Child", FatturaPaXmlParserService.localName(child));
        }

        @Test
        @DisplayName("localName: non-namespace-aware unprefixed element returns node name as-is")
        void localNameUnprefixedWhenNotNamespaceAware() throws Exception {
            Element root = buildElement(XML, false);
            Element other = FatturaPaXmlParserService.findFirstChildByLocalName(root, "Other");
            assertNotNull(other);
            assertEquals("Other", FatturaPaXmlParserService.localName(other));
        }

        @Test
        @DisplayName("findFirstChildByLocalName: returns null when not found")
        void findFirstChildByLocalNameNotFound() throws Exception {
            Element root = buildElement(XML, true);
            assertNull(FatturaPaXmlParserService.findFirstChildByLocalName(root, "DoesNotExist"));
        }

        @Test
        @DisplayName("findChildrenByLocalName: returns all repeating elements")
        void findChildrenByLocalNameReturnsAllMatches() throws Exception {
            Element root = buildElement(XML, true);
            List<Element> repeating = FatturaPaXmlParserService.findChildrenByLocalName(root, "Repeating");
            assertEquals(2, repeating.size());
            assertEquals("a", repeating.get(0).getTextContent());
            assertEquals("b", repeating.get(1).getTextContent());
        }

        @Test
        @DisplayName("findChildrenByLocalName: returns empty list when none match")
        void findChildrenByLocalNameReturnsEmptyList() throws Exception {
            Element root = buildElement(XML, true);
            assertEquals(0, FatturaPaXmlParserService.findChildrenByLocalName(root, "DoesNotExist").size());
        }

        @Test
        @DisplayName("findDescendantByLocalName: resolves nested path")
        void findDescendantByLocalNameResolvesNestedPath() throws Exception {
            Element root = buildElement(XML, true);
            Element inner = FatturaPaXmlParserService.findDescendantByLocalName(root, "Nested", "Inner");
            assertNotNull(inner);
            assertEquals("deep", inner.getTextContent());
        }

        @Test
        @DisplayName("findDescendantByLocalName: returns null when an intermediate segment is missing")
        void findDescendantByLocalNameReturnsNullWhenMissing() throws Exception {
            Element root = buildElement(XML, true);
            assertNull(FatturaPaXmlParserService.findDescendantByLocalName(root, "DoesNotExist", "Inner"));
        }

        @Test
        @DisplayName("getTextOrNull: returns trimmed text when present")
        void getTextOrNullReturnsText() throws Exception {
            Element root = buildElement(XML, true);
            assertEquals("value2", FatturaPaXmlParserService.getTextOrNull(root, "Other"));
        }

        @Test
        @DisplayName("getTextOrNull: returns null for empty element")
        void getTextOrNullReturnsNullForEmptyElement() throws Exception {
            Element root = buildElement(XML, true);
            assertNull(FatturaPaXmlParserService.getTextOrNull(root, "Empty"));
        }

        @Test
        @DisplayName("getTextOrNull: returns null when element not found")
        void getTextOrNullReturnsNullWhenNotFound() throws Exception {
            Element root = buildElement(XML, true);
            assertNull(FatturaPaXmlParserService.getTextOrNull(root, "DoesNotExist"));
        }

        @Test
        @DisplayName("getTextOrNull: returns null when element's text content is null")
        void getTextOrNullReturnsNullWhenTextContentIsNull() {
            Element parent = mock(Element.class);
            Element child = mock(Element.class);
            NodeList children = mock(NodeList.class);

            when(parent.getChildNodes()).thenReturn(children);
            when(children.getLength()).thenReturn(1);
            when(children.item(0)).thenReturn(child);
            when(child.getNodeType()).thenReturn(Node.ELEMENT_NODE);
            when(child.getLocalName()).thenReturn("Target");
            when(child.getTextContent()).thenReturn(null);

            assertNull(FatturaPaXmlParserService.getTextOrNull(parent, "Target"));
        }
    }
}
