package com.donatodev.bcm_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "contract_documents")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class ContractDocument extends StoredFile {

    /** JSON-serialized float array (Ollama embedding of the extracted text),
     * set the first time "Analizza con AI" runs. Null until then. */
    @Column(name = "embedding", columnDefinition = "json")
    private String embedding;

    /** Self-referencing tag (id of the first version) grouping successive
     * uploads of the same logical document for redlining. Null only in the
     * instant between a brand-new document's insert and the follow-up
     * update that sets it to its own generated id. */
    @Column(name = "version_group_id")
    private Long versionGroupId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /** PDFBox/OCR text extracted at upload time, cached so version diffing
     * never has to re-read the file or re-run OCR. Null until extraction
     * succeeds (best-effort, lazily backfilled for older rows). */
    @Column(name = "extracted_text", columnDefinition = "longtext")
    private String extractedText;
}
