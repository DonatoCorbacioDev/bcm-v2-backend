package com.donatodev.bcm_backend.service;

import java.util.Arrays;
import java.util.Objects;

/**
 * Bytes and metadata for a file downloaded from local storage.
 * Shared by {@link ContractDocumentService} and {@link ElectronicInvoiceService}.
 */
public record FileDownload(byte[] bytes, String fileName, String contentType) {

    @Override
    @SuppressWarnings({"java:S6880", "java:S6878"}) // record pattern deconstruction breaks JaCoCo branch coverage
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileDownload other)) return false;
        return Arrays.equals(bytes, other.bytes)
                && Objects.equals(fileName, other.fileName)
                && Objects.equals(contentType, other.contentType);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(bytes);
        result = 31 * result + Objects.hashCode(fileName);
        result = 31 * result + Objects.hashCode(contentType);
        return result;
    }

    @Override
    public String toString() {
        return "FileDownload[bytes=" + Arrays.toString(bytes)
                + ", fileName=" + fileName
                + ", contentType=" + contentType + "]";
    }
}
