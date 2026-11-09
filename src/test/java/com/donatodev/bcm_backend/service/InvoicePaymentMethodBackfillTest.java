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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.donatodev.bcm_backend.dto.FatturaPaInvoiceData;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Backfill of the payment method of already stored invoices")
class InvoicePaymentMethodBackfillTest {

    @Mock private ElectronicInvoiceRepository invoiceRepository;
    @Mock private LocalStorageService localStorageService;
    @Mock private FatturaPaXmlParserService parser;
    @InjectMocks private InvoicePaymentMethodBackfill backfill;

    private static ElectronicInvoice stored(UUID id, String path) {
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(id);
        invoice.setStoragePath(path);
        return invoice;
    }

    private static FatturaPaInvoiceData parsed(String method) {
        return new FatturaPaInvoiceData(null, null, "TD01", "1", null, new BigDecimal("1220.00"), "EUR",
                List.of(), null, null, null, null, null, null, method);
    }

    @Test
    @DisplayName("Re-reads each stored XML; an invoice stating no method gets an empty code, so it isn't read again")
    void fillsFromStoredXml() {
        ElectronicInvoice riba = stored(uid(1), "a.xml");
        ElectronicInvoice none = stored(uid(2), "b.xml");
        byte[] a = "a".getBytes();
        byte[] b = "b".getBytes();
        when(invoiceRepository.findByPaymentMethodIsNull()).thenReturn(List.of(riba, none));
        when(localStorageService.readDocument("a.xml")).thenReturn(a);
        when(localStorageService.readDocument("b.xml")).thenReturn(b);
        when(parser.parse(a)).thenReturn(parsed("MP12"));
        when(parser.parse(b)).thenReturn(parsed(null));

        backfill.backfill();

        assertEquals("MP12", riba.getPaymentMethod());
        assertEquals("", none.getPaymentMethod());
        verify(invoiceRepository).save(riba);
        verify(invoiceRepository).save(none);
    }

    @Test
    @DisplayName("Leaves an unreadable invoice null, to try again on the next start")
    void skipsUnreadableFile() {
        ElectronicInvoice broken = stored(uid(1), "missing.xml");
        when(invoiceRepository.findByPaymentMethodIsNull()).thenReturn(List.of(broken));
        when(localStorageService.readDocument("missing.xml")).thenThrow(new IllegalStateException("gone"));

        backfill.backfill();

        assertNull(broken.getPaymentMethod());
        verify(invoiceRepository, never()).save(broken);
    }

    @Test
    @DisplayName("Does nothing when every invoice already has its method")
    void nothingToDo() {
        when(invoiceRepository.findByPaymentMethodIsNull()).thenReturn(List.of());

        backfill.backfill();

        verify(invoiceRepository, never()).save(any());
    }
}
