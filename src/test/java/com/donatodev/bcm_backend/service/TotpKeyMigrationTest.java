package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.UsersRepository;

/** The one-time move of the two-factor secrets to their own key. */
class TotpKeyMigrationTest {

    private static final String JWT_SECRET = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes());
    private static final String TOTP_KEY = Base64.getEncoder().encodeToString(
            "abcdefghijabcdefghijabcdefghij12".getBytes());

    private final UsersRepository users = mock(UsersRepository.class);

    private static Users user(String stored) {
        return Users.builder().id(UUID.randomUUID()).username("u").passwordHash("x").totpSecretEncrypted(stored).build();
    }

    @Test
    void oldValuesAreReEncryptedUnderTheDedicatedKeyAndStillDecrypt() {
        String old = new TotpEncryptionService("", JWT_SECRET, "").encrypt("JBSWY3DPEHPK3PXP");
        TotpEncryptionService encryption = new TotpEncryptionService(TOTP_KEY, JWT_SECRET, "");
        Users legacy = user(old);
        Users current = user(encryption.encrypt("KRSXG5CTMVRXEZLU"));
        String currentBefore = current.getTotpSecretEncrypted();
        when(users.findByTotpSecretEncryptedIsNotNull()).thenReturn(List.of(legacy, current));

        new TotpKeyMigration(users, encryption).migrate();

        assertThat(legacy.getTotpSecretEncrypted()).startsWith("v2:");
        assertThat(encryption.decrypt(legacy.getTotpSecretEncrypted())).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(current.getTotpSecretEncrypted()).isEqualTo(currentBefore);
        verify(users).save(legacy);
        verify(users, never()).save(current);
    }

    @Test
    void anUnreadableValueIsLeftAsItWasAndTheRestGoesOn() {
        TotpEncryptionService encryption = new TotpEncryptionService(TOTP_KEY, JWT_SECRET, "");
        Users broken = user("bm90IGEgcmVhbCBjaXBoZXJ0ZXh0IGF0IGFsbA==");
        Users legacy = user(new TotpEncryptionService("", JWT_SECRET, "").encrypt("JBSWY3DPEHPK3PXP"));
        when(users.findByTotpSecretEncryptedIsNotNull()).thenReturn(List.of(broken, legacy));

        new TotpKeyMigration(users, encryption).migrate();

        assertThat(broken.getTotpSecretEncrypted()).isEqualTo("bm90IGEgcmVhbCBjaXBoZXJ0ZXh0IGF0IGFsbA==");
        assertThat(legacy.getTotpSecretEncrypted()).startsWith("v2:");
        verify(users, never()).save(broken);
        verify(users).save(legacy);
    }

    @Test
    void withoutTheDedicatedKeyNothingIsTouched() {
        new TotpKeyMigration(users, new TotpEncryptionService("", JWT_SECRET, "")).migrate();

        verifyNoInteractions(users);
    }

    @Test
    void aSecondRunFindsNothingToDo() {
        TotpEncryptionService encryption = new TotpEncryptionService(TOTP_KEY, JWT_SECRET, "");
        when(users.findByTotpSecretEncryptedIsNotNull()).thenReturn(List.of(user(encryption.encrypt("A"))));

        new TotpKeyMigration(users, encryption).migrate();

        verify(users, never()).save(any());
    }
}
