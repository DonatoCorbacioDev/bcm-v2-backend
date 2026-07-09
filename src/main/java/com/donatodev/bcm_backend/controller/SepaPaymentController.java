package com.donatodev.bcm_backend.controller;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.donatodev.bcm_backend.dto.CreateSepaPaymentRequest;
import com.donatodev.bcm_backend.dto.SepaPaymentBatchDTO;
import com.donatodev.bcm_backend.service.FileDownload;
import com.donatodev.bcm_backend.service.SepaPaymentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/contracts/{contractId}/sepa-payments")
public class SepaPaymentController {

    private final SepaPaymentService sepaPaymentService;

    public SepaPaymentController(SepaPaymentService sepaPaymentService) {
        this.sepaPaymentService = sepaPaymentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<byte[]> createSepaPayment(
            @PathVariable Long contractId,
            @Valid @RequestBody CreateSepaPaymentRequest request) {
        FileDownload download = sepaPaymentService.createSepaPayment(
                contractId, request.invoiceIds(), request.executionDate());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.fileName()).build());
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        return ResponseEntity.status(HttpStatus.CREATED).headers(headers).body(download.bytes());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<SepaPaymentBatchDTO>> getPayments(@PathVariable Long contractId) {
        return ResponseEntity.ok(sepaPaymentService.getPayments(contractId));
    }

    @GetMapping("/{batchId}/download")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<byte[]> downloadPayment(
            @PathVariable Long contractId,
            @PathVariable Long batchId) {
        FileDownload download = sepaPaymentService.downloadPayment(contractId, batchId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.fileName()).build());
        headers.setContentType(MediaType.parseMediaType(download.contentType()));
        return ResponseEntity.ok().headers(headers).body(download.bytes());
    }
}
