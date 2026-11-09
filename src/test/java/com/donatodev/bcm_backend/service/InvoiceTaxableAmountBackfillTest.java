package com.donatodev.bcm_backend.service;

import java.util.UUID;
import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.donatodev.bcm_backend.dto.FatturaPaInvoiceData;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Backfill of the taxable amount of already stored invoices")
class InvoiceTaxableAmountBackfillTest {

    @Mock private ElectronicInvoiceRepository invoiceRepository;
    @Mock private LocalStorageService localStorageService;
    @Mock private FatturaPaXmlParserService parser;
    @InjectMocks private InvoiceTaxableAmountBackfill backfill;

    private static ElectronicInvoice stored(UUID id, String path) {
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(id);
        invoice.setStoragePath(path);
        invoice.setTotalAmount(new BigDecimal("1220.00"));
        return invoice;
    }

    private static FatturaPaInvoiceData parsed(String taxable, String vat) {
        return new FatturaPaInvoiceData(null, null, "TD01", "1", null, new BigDecimal("1220.00"), "EUR",
                List.of(), null, null, null, taxable == null ? null : new BigDecimal(taxable),
                vat == null ? null : new BigDecimal(vat));
    }

    @Test
    @DisplayName("Re-reads each stored XML and fills in taxable amount and VAT")
    void fillsFromStoredXml() {
        ElectronicInvoice invoice = stored(uid(1), "invoices/3/1/a.xml");
        byte[] xml = "<xml/>".getBytes();
        when(invoiceRepository.findByTaxableAmountIsNull()).thenReturn(List.of(invoice));
        when(localStorageService.readDocument("invoices/3/1/a.xml")).thenReturn(xml);
        when(parser.parse(xml)).thenReturn(parsed("1000.00", "220.00"));

        backfill.backfill();

        assertEquals(new BigDecimal("1000.00"), invoice.getTaxableAmount());
        assertEquals(new BigDecimal("220.00"), invoice.getVatAmount());
        verify(invoiceRepository).save(invoice);
    }

    @Test
    @DisplayName("Skips, without failing the startup, a file that cannot be read")
    void skipsUnreadableFile() {
        ElectronicInvoice broken = stored(uid(2), "missing.xml");
        ElectronicInvoice ok = stored(uid(3), "ok.xml");
        byte[] xml = "<xml/>".getBytes();
        when(invoiceRepository.findByTaxableAmountIsNull()).thenReturn(List.of(broken, ok));
        when(localStorageService.readDocument("missing.xml")).thenThrow(new IllegalStateException("gone"));
        when(localStorageService.readDocument("ok.xml")).thenReturn(xml);
        when(parser.parse(xml)).thenReturn(parsed("1000.00", "220.00"));

        backfill.backfill();

        assertNull(broken.getTaxableAmount());
        assertEquals(new BigDecimal("1000.00"), ok.getTaxableAmount());
        verify(invoiceRepository, never()).save(broken);
    }

    @Test
    @DisplayName("Does nothing once every invoice has its taxable amount")
    void idempotent() {
        when(invoiceRepository.findByTaxableAmountIsNull()).thenReturn(List.of());

        backfill.backfill();

        verify(localStorageService, never()).readDocument(anyString());
    }

    @Test
    @DisplayName("Skips an XML that states no taxable amount, and logs nothing when there is nothing to do")
    void skipsXmlWithoutTaxableAmount() {
        ElectronicInvoice invoice = stored(uid(4), "no-taxable.xml");
        byte[] xml = "<xml/>".getBytes();
        when(invoiceRepository.findByTaxableAmountIsNull()).thenReturn(List.of(invoice)).thenReturn(List.of());
        when(localStorageService.readDocument("no-taxable.xml")).thenReturn(xml);
        when(parser.parse(xml)).thenReturn(parsed(null, null));

        backfill.backfill();
        backfill.backfill();

        assertNull(invoice.getTaxableAmount());
        verify(invoiceRepository, never()).save(invoice);
    }
}
