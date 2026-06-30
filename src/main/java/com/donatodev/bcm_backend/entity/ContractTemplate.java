package com.donatodev.bcm_backend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contract_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_status", nullable = false)
    @Builder.Default
    private ContractStatus defaultStatus = ContractStatus.DRAFT;

    @Column(name = "default_duration_days")
    private Integer defaultDurationDays;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_area_id")
    private BusinessAreas businessArea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_manager_id")
    private Managers defaultManager;

    @Builder.Default
    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew = false;

    @Column(name = "notification_days")
    private Integer notificationDays;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
