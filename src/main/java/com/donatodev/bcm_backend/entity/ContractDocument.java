package com.donatodev.bcm_backend.entity;

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
}
