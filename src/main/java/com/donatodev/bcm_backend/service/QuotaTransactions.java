/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * What a transaction that uses up a quota must be (TierLimitService,
 * StorageQuotaService). The organization's row is locked, then what it already
 * has is counted; under MySQL's default REPEATABLE READ that count would still
 * see the database as it was at the transaction's first read, made before the
 * lock, and two requests that queued on the lock would both count the same
 * free place (found on real MySQL, TransactionIntegrityIT). Under READ
 * COMMITTED every read sees what has been committed, so once the lock is
 * held the count is the true one. Every entry point that creates a contract
 * or stores a file declares {@code isolation = Isolation.READ_COMMITTED};
 * a caller that forgets fails here, at once, instead of letting the quota be
 * passed quietly.
 */
final class QuotaTransactions {

    private QuotaTransactions() {
    }

    /** Throws unless the current transaction, if any, is READ COMMITTED. */
    static void requireReadCommitted() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        Integer level = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (level == null || level != TransactionDefinition.ISOLATION_READ_COMMITTED) {
            throw new IllegalStateException(
                    "A quota is checked in a transaction that is not READ COMMITTED: declare it on the entry point");
        }
    }
}
