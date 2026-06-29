package com.donatodev.bcm_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ml_result_cache",
       uniqueConstraints = @UniqueConstraint(name = "uq_ml_cache", columnNames = {"org_id", "cache_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MlResultCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "cache_key", nullable = false, length = 50)
    private String cacheKey;

    @Column(name = "json_result", nullable = false, columnDefinition = "LONGTEXT")
    private String jsonResult;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;
}
