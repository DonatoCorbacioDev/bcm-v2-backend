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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.stream.Stream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.donatodev.bcm_backend.dto.FatturaPaInvoiceData;
import com.donatodev.bcm_backend.dto.InvoiceLineItemDTO;

class FatturaPaXmlParserServiceTest {

    private final FatturaPaXmlParserService parserService = new FatturaPaXmlParserService();

    /**
     * Every {@code FatturaElettronicaHeader} block mandated by the official
     * schema (DatiTrasmissione, full CedentePrestatore/CessionarioCommittente
     * with Sede), so fixtures below can vary just the {@code Body} (or a
     * specific header sub-field) and still pass schema validation.
     */
    private static final String MANDATORY_HEADER_XML =
            "<DatiTrasmissione>"
            + "<IdTrasmittente><IdPaese>IT</IdPaese><IdCodice>01234567890</IdCodice></IdTrasmittente>"
            + "<ProgressivoInvio>00001</ProgressivoInvio>"
            + "<FormatoTrasmissione>FPR12</FormatoTrasmissione>"
            + "<CodiceDestinatario>0000000</CodiceDestinatario>"
            + "</DatiTrasmissione>"
            + "<CedentePrestatore>"
            + "<DatiAnagrafici>"
            + "<IdFiscaleIVA><IdPaese>IT</IdPaese><IdCodice>12345678901</IdCodice></IdFiscaleIVA>"
            + "<Anagrafica><Denominazione>Fornitore Test S.r.l.</Denominazione></Anagrafica>"
            + "<RegimeFiscale>RF01</RegimeFiscale>"
            + "</DatiAnagrafici>"
            + "<Sede><Indirizzo>Via Test 1</Indirizzo><CAP>00100</CAP><Comune>Roma</Comune>"
            + "<Provincia>RM</Provincia><Nazione>IT</Nazione></Sede>"
            + "</CedentePrestatore>"
            + "<CessionarioCommittente>"
            + "<DatiAnagrafici>"
            + "<IdFiscaleIVA><IdPaese>IT</IdPaese><IdCodice>98765432109</IdCodice></IdFiscaleIVA>"
            + "<Anagrafica><Denominazione>Cliente Test S.p.A.</Denominazione></Anagrafica>"
            + "</DatiAnagrafici>"
            + "<Sede><Indirizzo>Via Cliente 1</Indirizzo><CAP>20100</CAP><Comune>Milano</Comune>"
            + "<Provincia>MI</Provincia><Nazione>IT</Nazione></Sede>"
            + "</CessionarioCommittente>";

    /**
     * Wraps a caller-supplied {@code FatturaElettronicaBody} in a
     * schema-mandatory envelope. Uses a prefixed namespace declaration on
     * just the root element (matching fattura-pa-sample.xml) rather than a
     * default {@code xmlns}, since the schema's local elements are
     * unqualified -- a default namespace on the root would incorrectly pull
     * every descendant into the target namespace too.
     */
    private static String withMandatoryHeader(String bodyXml) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p:FatturaElettronica xmlns:p=\"http://ivaservizi.agenziaentrate.gov.it/docs/xsd/fatture/v1.2\" versione=\"FPR12\">"
                + "<FatturaElettronicaHeader>" + MANDATORY_HEADER_XML + "</FatturaElettronicaHeader>"
                + bodyXml
                + "</p:FatturaElettronica>";
    }

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
    @DisplayName("loadSchema: bundled resource failure paths")
    class LoadSchema {

        @Test
        @DisplayName("throws when the resource is not found on the classpath")
        void throwsWhenResourceMissing() {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> FatturaPaXmlParserService.loadSchema("/fatturapa/does-not-exist.xsd"));
            assertTrue(ex.getMessage().contains("not found on classpath"));
        }

        @Test
        @DisplayName("throws when the resource is not a valid XSD")
        void throwsWhenResourceIsMalformed() {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> FatturaPaXmlParserService.loadSchema("/broken-schema.xsd"));
            assertEquals("Failed to compile the bundled FatturaPA schema", ex.getMessage());
        }
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
            assertNull(data.supplierIban());
            assertNull(data.supplierBic());
            assertNull(data.paymentDueDate());

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
        @DisplayName("minimal invoice: Nome/Cognome fallback (Denominazione absent), optional fields null")
        void shouldParseMinimalInvoice() throws Exception {
            FatturaPaInvoiceData data = parserService.parse(loadResource("fattura-pa-minimal.xml"));

            assertEquals("Mario Rossi", data.supplierName());
            assertEquals("ITRSSMRA80A01H501U", data.supplierVatNumber());
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

        @Test
        @DisplayName("real output from scripts/generate_fatturapa_samples.py (used by demo-reset.sh) passes schema validation")
        void shouldParseGeneratorOutput() throws Exception {
            FatturaPaInvoiceData data = parserService.parse(loadResource("fattura-pa-generator-output.xml"));

            assertNotNull(data.supplierName());
            assertNotNull(data.supplierVatNumber());
            assertNotNull(data.documentType());
            assertNotNull(data.invoiceDate());
            assertEquals(1, data.lineItems().size());
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

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("schemaRejectionScenarios")
        @DisplayName("documents violating the FatturaPA schema are rejected")
        void shouldRejectSchemaInvalidDocument(String description, String xml) {
            byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
            assertThrows(IllegalArgumentException.class, () -> parserService.parse(bytes));
        }

        static Stream<Arguments> schemaRejectionScenarios() {
            return Stream.of(
                    Arguments.of("missing DatiTrasmissione/CessionarioCommittente/etc.", NO_ANAGRAFICA_XML),
                    Arguments.of("empty Anagrafica violates the mandatory xs:choice", EMPTY_ANAGRAFICA_XML),
                    Arguments.of("malformed Data", INVALID_DATE_XML),
                    Arguments.of("malformed ImportoTotaleDocumento", INVALID_AMOUNT_XML),
                    Arguments.of("malformed NumeroLinea", INVALID_LINE_NUMBER_XML),
                    Arguments.of("empty FatturaElettronica (no Header, no Body)", EMPTY_ROOT_XML),
                    Arguments.of("Anagrafica with Nome but no Cognome violates the mandatory xs:choice", NOME_ONLY_XML),
                    Arguments.of("empty IdFiscaleIVA (missing mandatory IdPaese/IdCodice)", EMPTY_ID_FISCALE_IVA_XML),
                    Arguments.of("IdFiscaleIVA missing IdCodice", PARTIAL_ID_FISCALE_IVA_XML)
            );
        }
    }

    @Nested
    @DisplayName("parse: DatiPagamento (supplier IBAN/BIC/due date)")
    class ParseDatiPagamento {

        private static final String BASE_DATI_GENERALI_XML =
                "<DatiGenerali><DatiGeneraliDocumento>"
                + "<TipoDocumento>TD01</TipoDocumento><Divisa>EUR</Divisa><Data>2024-01-01</Data><Numero>1</Numero>"
                + "</DatiGeneraliDocumento></DatiGenerali>";

        private static final String BASE_DATI_BENI_SERVIZI_XML =
                "<DatiBeniServizi><DettaglioLinee>"
                + "<NumeroLinea>1</NumeroLinea><Descrizione>Test</Descrizione>"
                + "<PrezzoUnitario>10.00</PrezzoUnitario><PrezzoTotale>10.00</PrezzoTotale><AliquotaIVA>22.00</AliquotaIVA>"
                + "</DettaglioLinee>"
                + "<DatiRiepilogo><AliquotaIVA>22.00</AliquotaIVA>"
                + "<ImponibileImporto>10.00</ImponibileImporto><Imposta>2.20</Imposta></DatiRiepilogo>"
                + "</DatiBeniServizi>";

        private static final String WITH_VALID_IBAN_XML = withMandatoryHeader(
                "<FatturaElettronicaBody>" + BASE_DATI_GENERALI_XML + BASE_DATI_BENI_SERVIZI_XML
                + "<DatiPagamento><CondizioniPagamento>TP02</CondizioniPagamento><DettaglioPagamento>"
                + "<ModalitaPagamento>MP05</ModalitaPagamento>"
                + "<DataScadenzaPagamento>2024-06-30</DataScadenzaPagamento>"
                + "<ImportoPagamento>1220.00</ImportoPagamento>"
                + "<IBAN>DE89370400440532013000</IBAN>"
                + "<BIC>COBADEFFXXX</BIC>"
                + "</DettaglioPagamento></DatiPagamento>"
                + "</FatturaElettronicaBody>");

        private static final String WITH_MALFORMED_IBAN_XML = withMandatoryHeader(
                "<FatturaElettronicaBody>" + BASE_DATI_GENERALI_XML + BASE_DATI_BENI_SERVIZI_XML
                + "<DatiPagamento><CondizioniPagamento>TP02</CondizioniPagamento><DettaglioPagamento>"
                + "<ModalitaPagamento>MP05</ModalitaPagamento>"
                + "<DataScadenzaPagamento>2024-06-30</DataScadenzaPagamento>"
                + "<ImportoPagamento>1220.00</ImportoPagamento>"
                + "<IBAN>IT00X0000000000000000000000</IBAN>"
                + "<BIC>COBADEFFXXX</BIC>"
                + "</DettaglioPagamento></DatiPagamento>"
                + "</FatturaElettronicaBody>");

        private static final String WITHOUT_IBAN_XML = withMandatoryHeader(
                "<FatturaElettronicaBody>" + BASE_DATI_GENERALI_XML + BASE_DATI_BENI_SERVIZI_XML
                + "<DatiPagamento><CondizioniPagamento>TP02</CondizioniPagamento><DettaglioPagamento>"
                + "<ModalitaPagamento>MP05</ModalitaPagamento>"
                + "<DataScadenzaPagamento>2024-06-30</DataScadenzaPagamento>"
                + "<ImportoPagamento>1220.00</ImportoPagamento>"
                + "<BIC>COBADEFFXXX</BIC>"
                + "</DettaglioPagamento></DatiPagamento>"
                + "</FatturaElettronicaBody>");

        private static final String WITHOUT_DATI_PAGAMENTO_XML = withMandatoryHeader(
                "<FatturaElettronicaBody>" + BASE_DATI_GENERALI_XML + BASE_DATI_BENI_SERVIZI_XML
                + "</FatturaElettronicaBody>");

        @Test
        @DisplayName("valid IBAN/BIC/DataScadenzaPagamento are extracted and normalized")
        void shouldExtractValidPaymentDetails() {
            FatturaPaInvoiceData data = parserService.parse(WITH_VALID_IBAN_XML.getBytes(StandardCharsets.UTF_8));

            assertEquals("DE89370400440532013000", data.supplierIban());
            assertEquals("COBADEFFXXX", data.supplierBic());
            assertEquals(LocalDate.of(2024, Month.JUNE, 30), data.paymentDueDate());
        }

        @Test
        @DisplayName("malformed IBAN (bad checksum) is dropped, other payment fields still extracted")
        void shouldDropMalformedIban() {
            FatturaPaInvoiceData data = parserService.parse(WITH_MALFORMED_IBAN_XML.getBytes(StandardCharsets.UTF_8));

            assertNull(data.supplierIban());
            assertEquals("COBADEFFXXX", data.supplierBic());
            assertEquals(LocalDate.of(2024, Month.JUNE, 30), data.paymentDueDate());
        }

        @Test
        @DisplayName("DettaglioPagamento without an IBAN element: supplierIban is null")
        void shouldReturnNullIbanWhenElementAbsent() {
            FatturaPaInvoiceData data = parserService.parse(WITHOUT_IBAN_XML.getBytes(StandardCharsets.UTF_8));

            assertNull(data.supplierIban());
            assertEquals("COBADEFFXXX", data.supplierBic());
        }

        @Test
        @DisplayName("no DatiPagamento block: payment fields are null")
        void shouldReturnNullPaymentFieldsWhenAbsent() {
            FatturaPaInvoiceData data = parserService.parse(WITHOUT_DATI_PAGAMENTO_XML.getBytes(StandardCharsets.UTF_8));

            assertNull(data.supplierIban());
            assertNull(data.supplierBic());
            assertNull(data.paymentDueDate());
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

    /**
     * Direct unit tests of the package-private extraction/parsing helpers.
     * Several of their defensive branches (missing Anagrafica, IdFiscaleIVA
     * without both children, etc.) describe shapes the FatturaPA schema now
     * forbids outright -- {@code parse()} itself can never reach them once
     * schema validation runs first (see the "rejected by schema validation"
     * tests above). Tested directly here anyway, both to document the
     * fallback behavior for anyone calling these helpers in isolation and to
     * keep them from silently rotting into unreachable dead code.
     */
    @Nested
    @DisplayName("direct unit tests: extraction and value-parsing helpers")
    class ExtractionHelpers {

        private Element datiAnagraficiFrom(String innerXml) throws Exception {
            return buildElement("<DatiAnagrafici>" + innerXml + "</DatiAnagrafici>", true);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("supplierNameScenarios")
        @DisplayName("extractSupplierName resolves Denominazione or Nome+Cognome, else null")
        void shouldExtractSupplierName(String description, String datiAnagraficiXml, String expected) throws Exception {
            Element datiAnagrafici = datiAnagraficiFrom(datiAnagraficiXml);
            assertEquals(expected, parserService.extractSupplierName(datiAnagrafici));
        }

        static Stream<Arguments> supplierNameScenarios() {
            return Stream.of(
                    Arguments.of("no Anagrafica element at all -> null",
                            "<CodiceFiscale>RSSMRA80A01H501U</CodiceFiscale>", null),
                    Arguments.of("Denominazione present -> returned as-is",
                            "<Anagrafica><Denominazione>Acme S.r.l.</Denominazione></Anagrafica>", "Acme S.r.l."),
                    Arguments.of("Nome+Cognome present, no Denominazione -> concatenated",
                            "<Anagrafica><Nome>Mario</Nome><Cognome>Rossi</Cognome></Anagrafica>", "Mario Rossi"),
                    Arguments.of("Nome without Cognome -> null",
                            "<Anagrafica><Nome>Mario</Nome></Anagrafica>", null),
                    Arguments.of("Cognome without Nome -> null",
                            "<Anagrafica><Cognome>Rossi</Cognome></Anagrafica>", null),
                    Arguments.of("empty Anagrafica (neither choice populated) -> null",
                            "<Anagrafica/>", null)
            );
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("supplierVatNumberScenarios")
        @DisplayName("extractSupplierVatNumber resolves VAT from IdFiscaleIVA or falls back to CodiceFiscale")
        void shouldExtractSupplierVatNumber(String description, String datiAnagraficiXml, String expected) throws Exception {
            Element datiAnagrafici = datiAnagraficiFrom(datiAnagraficiXml);
            assertEquals(expected, parserService.extractSupplierVatNumber(datiAnagrafici));
        }

        static Stream<Arguments> supplierVatNumberScenarios() {
            return Stream.of(
                    Arguments.of("IdFiscaleIVA with both IdPaese/IdCodice -> concatenated",
                            "<IdFiscaleIVA><IdPaese>IT</IdPaese><IdCodice>12345678901</IdCodice></IdFiscaleIVA>",
                            "IT12345678901"),
                    Arguments.of("no IdFiscaleIVA -> falls back to CodiceFiscale",
                            "<CodiceFiscale>RSSMRA80A01H501U</CodiceFiscale>",
                            "RSSMRA80A01H501U"),
                    Arguments.of("IdFiscaleIVA missing IdPaese -> falls back to CodiceFiscale",
                            "<IdFiscaleIVA><IdCodice>12345678901</IdCodice></IdFiscaleIVA>"
                                    + "<CodiceFiscale>RSSMRA80A01H501U</CodiceFiscale>",
                            "RSSMRA80A01H501U"),
                    Arguments.of("IdFiscaleIVA missing IdCodice -> falls back to CodiceFiscale",
                            "<IdFiscaleIVA><IdPaese>IT</IdPaese></IdFiscaleIVA>"
                                    + "<CodiceFiscale>RSSMRA80A01H501U</CodiceFiscale>",
                            "RSSMRA80A01H501U"),
                    Arguments.of("neither IdFiscaleIVA nor CodiceFiscale -> null",
                            "<Anagrafica><Denominazione>Acme</Denominazione></Anagrafica>",
                            null)
            );
        }

        @Test
        @DisplayName("extractSupplierIban: no IBAN element -> null")
        void extractSupplierIbanAbsent() throws Exception {
            Element dettaglioPagamento = buildElement("<DettaglioPagamento><BIC>COBADEFFXXX</BIC></DettaglioPagamento>", true);
            assertNull(parserService.extractSupplierIban(dettaglioPagamento));
        }

        @Test
        @DisplayName("extractSupplierIban: valid IBAN -> normalized (spaces stripped, uppercased)")
        void extractSupplierIbanValid() throws Exception {
            Element dettaglioPagamento = buildElement(
                    "<DettaglioPagamento><IBAN>de89 3704 0044 0532 0130 00</IBAN></DettaglioPagamento>", true);
            assertEquals("DE89370400440532013000", parserService.extractSupplierIban(dettaglioPagamento));
        }

        @Test
        @DisplayName("extractSupplierIban: malformed IBAN (bad checksum) -> null")
        void extractSupplierIbanMalformed() throws Exception {
            Element dettaglioPagamento = buildElement(
                    "<DettaglioPagamento><IBAN>IT00X0000000000000000000000</IBAN></DettaglioPagamento>", true);
            assertNull(parserService.extractSupplierIban(dettaglioPagamento));
        }

        @Test
        @DisplayName("parseOptionalAmount: null text -> null")
        void parseOptionalAmountNull() {
            assertNull(parserService.parseOptionalAmount(null));
        }

        @Test
        @DisplayName("parseOptionalAmount: valid text -> parsed BigDecimal")
        void parseOptionalAmountValid() {
            assertEquals(0, new BigDecimal("1220.00").compareTo(parserService.parseOptionalAmount("1220.00")));
        }

        @Test
        @DisplayName("parseOptionalAmount: malformed text -> IllegalArgumentException")
        void parseOptionalAmountMalformed() {
            assertThrows(IllegalArgumentException.class, () -> parserService.parseOptionalAmount("not-a-number"));
        }

        @Test
        @DisplayName("parseOptionalDate: null text -> null")
        void parseOptionalDateNull() {
            assertNull(parserService.parseOptionalDate(null));
        }

        @Test
        @DisplayName("parseOptionalDate: valid text -> parsed LocalDate")
        void parseOptionalDateValid() {
            assertEquals(LocalDate.of(2024, Month.MARCH, 15), parserService.parseOptionalDate("2024-03-15"));
        }

        @Test
        @DisplayName("parseOptionalDate: malformed text -> IllegalArgumentException")
        void parseOptionalDateMalformed() {
            assertThrows(IllegalArgumentException.class, () -> parserService.parseOptionalDate("not-a-date"));
        }

        @Test
        @DisplayName("parseOptionalInt: null text -> null")
        void parseOptionalIntNull() {
            assertNull(parserService.parseOptionalInt(null));
        }

        @Test
        @DisplayName("parseOptionalInt: valid text -> parsed Integer")
        void parseOptionalIntValid() {
            assertEquals(1, parserService.parseOptionalInt("1"));
        }

        @Test
        @DisplayName("parseOptionalInt: malformed text -> IllegalArgumentException")
        void parseOptionalIntMalformed() {
            assertThrows(IllegalArgumentException.class, () -> parserService.parseOptionalInt("abc"));
        }
    }
}
