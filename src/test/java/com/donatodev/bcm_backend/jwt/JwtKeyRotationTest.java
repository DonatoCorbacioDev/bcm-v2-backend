package com.donatodev.bcm_backend.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;

import io.jsonwebtoken.security.SignatureException;

/** Rotating the signing key (OWASP Secrets Management): the old key checks, never signs. */
class JwtKeyRotationTest {

    private static final String OLD = key();
    private static final String NEW = key();

    private static String key() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static JwtUtils utils(String secret, String previous) {
        JwtUtils utils = new JwtUtils();
        ReflectionTestUtils.setField(utils, "jwtSecret", secret);
        ReflectionTestUtils.setField(utils, "previousJwtSecret", previous);
        ReflectionTestUtils.setField(utils, "jwtExpirationMs", 900_000);
        return utils;
    }

    private static final Users USER = Users.builder().id(UUID.randomUUID()).username("john").passwordHash("x")
            .role(Roles.builder().id(UUID.randomUUID()).role("ADMIN").build()).verified(true).build();
    private static final User DETAILS = new User("john", "x", List.of(() -> "ROLE_ADMIN"));

    @Test
    void aTokenSignedWithThePreviousKeyIsStillAcceptedDuringTheRotation() {
        String token = utils(OLD, "").generateTokenFromUser(USER);

        JwtUtils rotated = utils(NEW, OLD);

        assertThat(rotated.validateJwtToken(token, DETAILS)).isTrue();
        assertThat(rotated.getUsernameFromToken(token)).isEqualTo("john");
    }

    @Test
    void afterTheRotationTheOldKeyIsRefused() {
        String token = utils(OLD, "").generateTokenFromUser(USER);

        JwtUtils done = utils(NEW, "");

        assertThat(done.validateJwtToken(token, DETAILS)).isFalse();
        assertThatThrownBy(() -> done.getUsernameFromToken(token)).isInstanceOf(SignatureException.class);
    }

    @Test
    void newTokensAreSignedWithTheCurrentKeyOnly() {
        String token = utils(NEW, OLD).generateTokenFromUser(USER);

        assertThat(utils(NEW, "").validateJwtToken(token, DETAILS)).isTrue();
        assertThat(utils(OLD, "").validateJwtToken(token, DETAILS)).isFalse();
    }

    @Test
    void aTokenSignedWithAnUnknownKeyIsRefusedEvenDuringTheRotation() {
        String token = utils(key(), "").generateTokenFromUser(USER);

        assertThat(utils(NEW, OLD).validateJwtToken(token, DETAILS)).isFalse();
    }
}
