package com.donatodev.bcm_backend.entity;

import java.time.LocalDateTime;
import java.time.ZoneId;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a single transition in a contract's approval workflow
 * (submit for review, approve, reject), recorded in the
 * "contract_workflow_events" table.
 */
@Entity
@Table(name = "contract_workflow_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractWorkflowEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "contract_id", nullable = false)
    private Contracts contract;

    /**
     * Stage before this transition. {@code null} only doesn't occur in
     * practice (every event starts from DRAFT or IN_REVIEW) but the column
     * is nullable to keep the shape symmetric with {@code toStage}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage")
    private WorkflowStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", nullable = false)
    private WorkflowStage toStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private WorkflowAction action;

    @ManyToOne
    @JoinColumn(name = "actor_user_id", nullable = false)
    private Users actor;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(ZoneId.systemDefault());
}
