package com.donatodev.bcm_backend.entity;

import lombok.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key class for the ContractManager entity.
 * <p>
 * Represents the primary key composed of contract and manager IDs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContractManagerId implements Serializable {

    private static final long serialVersionUID = 8735089218631723117L;

    /**
     * ID of the associated contract.
     */
    private Long contract;

    /**
     * ID of the associated manager.
     */
    private Long manager;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContractManagerId)) return false;
        ContractManagerId that = (ContractManagerId) o;
        return Objects.equals(contract, that.contract) &&
               Objects.equals(manager, that.manager);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contract, manager);
    }
}
