package com.donatodev.bcm_backend.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.donatodev.bcm_backend.entity.ContractDocument;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.exception.MalwareScanUnavailableException;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.security.SecurityEvents;
import com.donatodev.bcm_backend.service.LocalStorageService;
import com.donatodev.bcm_backend.support.TestUploads;

/** The one-off recheck of files stored before the upload checks: report only. */
class StoredFileRescanTest {

    private static final UUID ORG = UUID.randomUUID();
    private static final byte[] XML = "<?xml version=\"1.0\"?><a/>".getBytes(StandardCharsets.UTF_8);

    private MalwareScanner scanner;
    private ContractDocumentRepository documents;
    private ElectronicInvoiceRepository invoices;
    private LocalStorageService storage;
    private SecurityEvents securityEvents;

    @BeforeEach
    void setUp() {
        scanner = mock(MalwareScanner.class);
        when(scanner.enabled()).thenReturn(true);
        when(scanner.scan(any())).thenReturn(Optional.empty());
        documents = mock(ContractDocumentRepository.class);
        invoices = mock(ElectronicInvoiceRepository.class);
        storage = mock(LocalStorageService.class);
        securityEvents = mock(SecurityEvents.class);
    }

    private StoredFileRescan rescan(boolean enabled) {
        UploadChecks checks = new UploadChecks(scanner, mock(SecurityEvents.class));
        return new StoredFileRescan(enabled, checks, documents, invoices, storage, securityEvents);
    }

    private ContractDocument document(String path, byte[] content) {
        ContractDocument document = ContractDocument.builder().id(UUID.randomUUID()).orgId(ORG).storagePath(path).build();
        when(storage.readDocument(path)).thenReturn(content);
        return document;
    }

    private ElectronicInvoice invoice(String path, byte[] content) {
        ElectronicInvoice invoice = ElectronicInvoice.builder().id(UUID.randomUUID()).orgId(ORG).storagePath(path).build();
        when(storage.readDocument(path)).thenReturn(content);
        return invoice;
    }

    @Test
    void cleanFilesAreCountedAndNothingIsRecorded() {
        var files1 = List.of(document("d1", TestUploads.pdf()));
        when(documents.findAll()).thenReturn(files1);
        var files2 = List.of(invoice("i1", XML), invoice("i2", XML));
        when(invoices.findAll()).thenReturn(files2);

        StoredFileRescan.Report report = rescan(true).run();

        assertThat(report).isEqualTo(new StoredFileRescan.Report(3, 3, 0, 0, true));
        verifyNoInteractions(securityEvents);
    }

    @Test
    void eachFindingIsRecordedWithItsEntityAndOrganization() {
        ContractDocument scripted = document("d1", TestUploads.pdf(d -> d.getDocumentCatalog()
                .setOpenAction(new PDActionJavaScript("app.alert(1)"))));
        ElectronicInvoice infected = invoice("i1", "<virus/>".getBytes(StandardCharsets.UTF_8));
        when(scanner.scan("<virus/>".getBytes(StandardCharsets.UTF_8))).thenReturn(Optional.of("Eicar-Test-Signature"));
        when(documents.findAll()).thenReturn(List.of(scripted));
        ElectronicInvoice clean = invoice("i2", XML);
        when(invoices.findAll()).thenReturn(List.of(infected, clean));

        StoredFileRescan.Report report = rescan(true).run();

        assertThat(report).isEqualTo(new StoredFileRescan.Report(3, 1, 2, 0, true));
        verify(securityEvents).recordAbout(SecurityEvents.Type.UNSAFE_UPLOAD, "ContractDocument", scripted.getId(),
                ORG, "stored file: active content: JS");
        verify(securityEvents).recordAbout(SecurityEvents.Type.UNSAFE_UPLOAD, "ElectronicInvoice", infected.getId(),
                ORG, "stored file: malware: Eicar-Test-Signature");
    }

    @Test
    void nothingIsChangedOnDisk() {
        var files3 = List.of(document("d1", "<html>".getBytes(StandardCharsets.UTF_8)));
        when(documents.findAll()).thenReturn(files3);
        when(invoices.findAll()).thenReturn(List.of());

        assertThat(rescan(true).run().suspicious()).isEqualTo(1);

        verify(storage, never()).deleteDocument(anyString());
        verify(documents, never()).save(any());
        verify(documents, never()).delete(any());
    }

    @Test
    void aFileMissingOnDiskIsCountedAndTheRunGoesOn() {
        ContractDocument gone = ContractDocument.builder().id(UUID.randomUUID()).storagePath("gone").build();
        when(storage.readDocument("gone")).thenThrow(new UncheckedIOException("missing", new java.io.IOException()));
        ContractDocument present = document("d2", TestUploads.pdf());
        when(documents.findAll()).thenReturn(List.of(gone, present));
        when(invoices.findAll()).thenReturn(List.of());

        assertThat(rescan(true).run()).isEqualTo(new StoredFileRescan.Report(1, 1, 0, 1, true));
    }

    @Test
    void clamAvDownStopsTheRunWithoutCallingTheRestClean() {
        when(scanner.scan(any())).thenThrow(new MalwareScanUnavailableException("down", null));
        var files4 = List.of(document("d1", TestUploads.pdf()));
        when(documents.findAll()).thenReturn(files4);
        var files5 = List.of(invoice("i1", XML));
        when(invoices.findAll()).thenReturn(files5);

        StoredFileRescan.Report report = rescan(true).run();

        assertThat(report.complete()).isFalse();
        assertThat(report.clean()).isZero();
        verify(invoices, never()).findAll();
        verifyNoInteractions(securityEvents);
    }

    @Test
    void withTheAntivirusOffItRefusesToRun() {
        when(scanner.enabled()).thenReturn(false);

        StoredFileRescan.Report report = rescan(true).run();

        assertThat(report).isEqualTo(new StoredFileRescan.Report(0, 0, 0, 0, false));
        verifyNoInteractions(documents, invoices, storage, securityEvents);
    }

    @Test
    void atStartupItRunsOnlyWhenSwitchedOn() {
        rescan(false).onReady();
        verifyNoInteractions(documents, invoices);

        when(documents.findAll()).thenReturn(List.of());
        when(invoices.findAll()).thenReturn(List.of());
        rescan(true).onReady();
        verify(documents).findAll();
        verify(invoices).findAll();
    }
}
