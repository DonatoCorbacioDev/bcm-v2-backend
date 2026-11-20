/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ties what a MySQL rollback cannot undo (an email sent, a file written or
 * deleted) to the outcome of the current transaction (OWASP Database
 * Security, transactions): the rollback restores the rows, never the mailbox
 * or the disk, so those effects wait for the commit, or are cleaned up after
 * a rollback. Outside a transaction there is nothing to wait for: the action
 * runs at once.
 */
public final class AfterTransaction {

    private AfterTransaction() {
    }

    /**
     * Runs {@code action} once the current transaction has committed, and never
     * if it rolls back. An exception from the action reaches the caller, after
     * the commit: the rows are saved whatever the action does.
     */
    public static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /**
     * Runs {@code action} if the current transaction rolls back, for instance to
     * remove a file written for rows that will not exist. Outside a transaction
     * nothing can roll back, so nothing is registered.
     */
    public static void onRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }
}
