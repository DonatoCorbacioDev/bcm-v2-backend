package com.donatodev.bcm_backend.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.donatodev.bcm_backend.dto.ElectronicInvoiceDTO;
import com.donatodev.bcm_backend.service.ElectronicInvoiceService;
import com.donatodev.bcm_backend.service.ElectronicInvoiceService.InvoiceDownload;

@RestController
@RequestMapping("/contracts/{contractId}/invoices")
public class ElectronicInvoiceController {

    private final ElectronicInvoiceService invoiceService;

    public ElectronicInvoiceController(ElectronicInvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ElectronicInvoiceDTO> uploadInvoice(
            @PathVariable Long contractId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invoiceService.uploadInvoice(contractId, file));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<ElectronicInvoiceDTO>> getInvoices(@PathVariable Long contractId) {
        return ResponseEntity.ok(invoiceService.getInvoices(contractId));
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ElectronicInvoiceDTO> getInvoice(
            @PathVariable Long contractId,
            @PathVariable Long invoiceId) {
        return ResponseEntity.ok(invoiceService.getInvoice(contractId, invoiceId));
    }

    @GetMapping("/{invoiceId}/download")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<byte[]> downloadInvoice(
            @PathVariable Long contractId,
            @PathVariable Long invoiceId) {
        InvoiceDownload download = invoiceService.downloadInvoice(contractId, invoiceId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.fileName()).build());
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        return ResponseEntity.ok().headers(headers).body(download.bytes());
    }

    @DeleteMapping("/{invoiceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteInvoice(
            @PathVariable Long contractId,
            @PathVariable Long invoiceId) {
        invoiceService.deleteInvoice(contractId, invoiceId);
        return ResponseEntity.noContent().build();
    }
}
