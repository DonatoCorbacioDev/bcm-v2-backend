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
}
