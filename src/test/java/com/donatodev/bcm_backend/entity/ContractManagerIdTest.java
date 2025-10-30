package com.donatodev.bcm_backend.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ContractManagerId}, which is an embeddable composite key
 * used for mapping a relationship between {@code Contracts} and {@code Managers}.
 * <p>
 * This class verifies the behavior of the {@code equals} and {@code hashCode} methods
 * under various comparison scenarios, including null handling, different types,
 * same references, and field variations.
 * </p>
 */
class ContractManagerIdTest {

    /**
     * Tests that two instances with the same values are considered equal
     * and have the same hash code.
     */
    @Test
    @DisplayName("Equals and hashCode: same values → true")
    void equalsAndHashCode_sameValues_shouldBeEqual() {
        ContractManagerId id1 = new ContractManagerId(1L, 2L);
        ContractManagerId id2 = new ContractManagerId(1L, 2L);

        assertEquals(id1, id2);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    /**
     * Tests that comparing with {@code null} returns {@code false}.
     */
    @Test
    @DisplayName("Equals: compare with null → false")
    void equals_null_shouldReturnFalse() {
        ContractManagerId id = new ContractManagerId(1L, 2L);
        assertNotEquals(null, id);
    }

    /**
     * Tests that comparing with an object of a different type returns {@code false}.
     */
    @Test
    @DisplayName("Equals: comparison with object of different type → false")
    void equals_differentType_shouldReturnFalse() {
        ContractManagerId id = new ContractManagerId(1L, 2L);
        assertNotEquals("stringa", id);
    }

    /**
     * Tests that two references to the same instance are equal.
     */
    @Test
    @DisplayName("Equals: same references → true")
    void equals_sameReference_shouldReturnTrue() {
        ContractManagerId id = new ContractManagerId(1L, 2L);
        assertEquals(id, id);
    }

    /**
     * Tests that two instances with different values are not equal
     * and do not have the same hash code.
     */
    @Test
    @DisplayName("Equals and hashCode: different values → false")
    void equalsAndHashCode_differentValues_shouldNotBeEqual() {
        ContractManagerId id1 = new ContractManagerId(1L, 2L);
        ContractManagerId id2 = new ContractManagerId(1L, 3L);

        assertNotEquals(id1, id2);
        assertNotEquals(id1.hashCode(), id2.hashCode());
    }

    /**
     * Tests that equals returns false when comparing with a raw {@code Object} instance.
     */
    @Test
    @DisplayName("Equals: comparison with raw Object instance → false")
    void equals_genericObject_shouldReturnFalse() {
    	ContractManagerId id = new ContractManagerId(1L, 2L);
        assertNotEquals(id, new Object());
    }

    /**
     * Tests the execution path when {@code instanceof} is true,
     * ensuring deep comparison logic is triggered.
     */
    @Test
    @DisplayName("Equals: instanceof true, no early return → continue with deeper check")
    void equals_instanceofTrue_shouldContinueCheck() {
    	ContractManagerId id1 = new ContractManagerId(10L, 20L);
        Object id2 = new ContractManagerId(10L, 20L); // instanceof == true

        assertEquals(id1, id2);
    }

    /**
     * Tests that instances are not equal when one has a {@code null} contract ID.
     */
    @Test
    void equals_contractNullInOne_shouldReturnFalse() {
        ContractManagerId id1 = new ContractManagerId(null, 2L);
        ContractManagerId id2 = new ContractManagerId(1L, 2L);
        assertNotEquals(id1, id2);
    }

    /**
     * Tests that two instances with {@code null} contract ID but same manager ID are equal.
     */
    @Test
    void equals_contractNullInBoth_shouldReturnTrue() {
        ContractManagerId id1 = new ContractManagerId(null, 2L);
        ContractManagerId id2 = new ContractManagerId(null, 2L);
        assertEquals(id1, id2);
    }
}
