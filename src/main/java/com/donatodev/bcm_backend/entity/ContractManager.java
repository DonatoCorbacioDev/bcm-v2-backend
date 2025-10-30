package com.donatodev.bcm_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing the association between Contracts and Managers.
 * <p>
 * Maps the many-to-many relationship between contracts and their assigned managers.
 * Uses a composite key consisting of contract_id and manager_id.
 */
@Entity
@Table(name = "contract_manager")
@IdClass(ContractManagerId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContractManager {

    /**
     * The associated contract.
     */
    @Id
    @ManyToOne
    @JoinColumn(name = "contract_id", nullable = false)
    private Contracts contract;

    /**
     * The associated manager.
     */
    @Id
    @ManyToOne
    @JoinColumn(name = "manager_id", nullable = false)
    private Managers manager;
}
