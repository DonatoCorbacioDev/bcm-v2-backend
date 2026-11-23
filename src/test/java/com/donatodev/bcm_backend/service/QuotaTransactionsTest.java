/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** A quota is never checked in a transaction that would count a stale snapshot. */
class QuotaTransactionsTest {

    @AfterEach
    void endTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
    }

    @Test
    @DisplayName("a transaction at the database's default level (REPEATABLE READ in MySQL) is refused")
    void defaultLevelRefused() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(QuotaTransactions::requireReadCommitted).isInstanceOf(IllegalStateException.class);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(QuotaTransactions::requireReadCommitted).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("READ COMMITTED is accepted")
    void readCommittedAccepted() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThatCode(QuotaTransactions::requireReadCommitted).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("outside any transaction (unit tests with mocks) there is nothing to check")
    void noTransaction() {
        assertThatCode(QuotaTransactions::requireReadCommitted).doesNotThrowAnyException();
    }
}
