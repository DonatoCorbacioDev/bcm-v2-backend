package com.donatodev.bcm_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing the "contract_history" table.
 * <p>
 * Tracks the status changes of a contract over time, including
 * who made the change and when it occurred.
 */
@Entity
@Table(name = "contract_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractHistory {

    /**
     * Primary key - auto-generated.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the contract being modified.
     */
    @ManyToOne
    @JoinColumn(name = "contract_id", nullable = false)
    private Contracts contract;

    /**
     * User who performed the status change.
     */
    @ManyToOne
    @JoinColumn(name = "modified_by", nullable = false)
    private Users modifiedBy;

    /**
     * Timestamp of the status change. Not updatable.
     */
    @Column(name = "modification_date", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime modificationDate = LocalDateTime.now();

    /**
     * Previous status of the contract.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false)
    private ContractStatus previousStatus;

    /**
     * New status of the contract after the change.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private ContractStatus newStatus;
}
