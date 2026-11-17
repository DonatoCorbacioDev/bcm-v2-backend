/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.UsersRepository;

/**
 * Re-encrypts, once, the two-factor secrets stored before they had a key of
 * their own (see {@link TotpEncryptionService}) under the dedicated key.
 * Idempotent: a value already in the new format is left alone, so after the
 * first start with the key it does nothing. A value that cannot be read is
 * logged (by user id, never the value) and left as it was.
 */
@Service
public class TotpKeyMigration {

    private static final Logger logger = LoggerFactory.getLogger(TotpKeyMigration.class);

    private final UsersRepository usersRepository;
    private final TotpEncryptionService encryption;

    public TotpKeyMigration(UsersRepository usersRepository, TotpEncryptionService encryption) {
        this.usersRepository = usersRepository;
        this.encryption = encryption;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        if (!encryption.hasDedicatedKey()) {
            return;
        }
        int migrated = 0;
        int failed = 0;
        for (Users user : usersRepository.findByTotpSecretEncryptedIsNotNull()) {
            String stored = user.getTotpSecretEncrypted();
            if (!encryption.needsReEncryption(stored)) {
                continue;
            }
            try {
                user.setTotpSecretEncrypted(encryption.encrypt(encryption.decrypt(stored)));
                usersRepository.save(user);
                migrated++;
            } catch (IllegalStateException e) {
                failed++;
                logger.warn("Could not re-encrypt the two-factor secret of user {}", user.getId());
            }
        }
        if (migrated > 0 || failed > 0) {
            logger.info("Two-factor secrets re-encrypted under TOTP_ENCRYPTION_KEY: {} done, {} failed", migrated, failed);
        }
    }
}
