package com.donatodev.bcm_backend.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;

class TwoFactorPolicyTest {

    private final TwoFactorPolicy policy = new TwoFactorPolicy(" demo_admin , other_exempt ");

    private static Users user(String username, String role, boolean totp) {
        return Users.builder().username(username).role(Roles.builder().role(role).build()).totpEnabled(totp).build();
    }

    @Test
    @DisplayName("An ADMIN must have 2FA, and without it gets no session")
    void adminRequiresTwoFactor() {
        assertTrue(policy.isRequired(user("boss", "ADMIN", false)));
        assertTrue(policy.blocksSession(user("boss", "ADMIN", false)));
        assertFalse(policy.blocksSession(user("boss", "ADMIN", true)));
    }

    @Test
    @DisplayName("A manager may choose")
    void managerMayChoose() {
        assertFalse(policy.isRequired(user("anna", "MANAGER", false)));
        assertFalse(policy.blocksSession(user("anna", "MANAGER", false)));
    }

    @Test
    @DisplayName("Listed usernames are exempt, spaces around the commas ignored")
    void listedAdminsAreExempt() {
        assertFalse(policy.blocksSession(user("demo_admin", "ADMIN", false)));
        assertFalse(policy.blocksSession(user("other_exempt", "ADMIN", false)));
    }

    @Test
    @DisplayName("An empty list exempts nobody, and a user without a role is not an admin")
    void emptyListExemptsNobody() {
        TwoFactorPolicy strict = new TwoFactorPolicy("");
        assertTrue(strict.blocksSession(user("demo_admin", "ADMIN", false)));
        assertFalse(strict.isRequired(Users.builder().username("x").build()));
    }
}
