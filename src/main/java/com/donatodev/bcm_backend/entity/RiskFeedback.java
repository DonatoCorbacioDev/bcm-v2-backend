package com.donatodev.bcm_backend.entity;

import java.time.LocalDateTime;
import java.time.ZoneId;

import jakarta.persistence.*;
import lombok.*;

/**
 * Records whether a human reviewer agreed with a risk score shown for a
 * contract at a point in time. Kept as an append-only log (not one row per
 * contract) so the same contract can be re-confirmed as its score changes,
 * building a labeled history usable to validate or retrain the ML model.
 */
@Entity
@Table(name = "risk_feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "contract_id", nullable = false)
    private Contracts contract;

    @ManyToOne
    @JoinColumn(name = "submitted_by", nullable = false)
    private Users submittedBy;

    @Column(name = "org_id", nullable = false)
    private Long organizationId;

    /**
     * The rule-based risk score/level shown to the user at the moment of feedback.
     */
    @Column(name = "risk_score", nullable = false)
    private double riskScore;

    @Column(name = "risk_level", nullable = false)
    private String riskLevel;

    /**
     * The ML model's score/level at the same moment, if the model was active.
     */
    @Column(name = "ml_score")
    private Double mlScore;

    @Column(name = "ml_level")
    private String mlLevel;

    /**
     * Whether the reviewer judged the shown risk assessment to be accurate.
     */
    @Column(name = "agree", nullable = false)
    private boolean agree;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.systemDefault());
}
