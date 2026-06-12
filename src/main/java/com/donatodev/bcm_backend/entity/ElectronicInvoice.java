package com.donatodev.bcm_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "electronic_invoices")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ElectronicInvoice extends StoredFile {

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "supplier_vat_number", length = 30)
    private String supplierVatNumber;

    @Column(name = "document_type", length = 10)
    private String documentType;

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Lob
    @Column(name = "line_items_json")
    private String lineItemsJson;
}
