package com.donatodev.bcm_backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.entity.VerificationToken;

@DataJpaTest
@ActiveProfiles("test")
class VerificationTokenRepositoryTest {

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private ManagersRepository managersRepository;

    private Users user;

    @BeforeEach
    void setup() {
        verificationTokenRepository.deleteAll();
        usersRepository.deleteAll();
        managersRepository.deleteAll();
        rolesRepository.deleteAll();

        Roles role = rolesRepository.save(Roles.builder().role("ADMIN").build());

        Managers manager = managersRepository.save(Managers.builder()
                .firstName("Test")
                .lastName("User")
                .email("test@example.com")
                .phoneNumber("000000")
                .department("IT")
                .build());

        user = usersRepository.save(Users.builder()
                .username("testuser")
                .passwordHash("hash123")
                .verified(false)
                .role(role)
                .manager(manager)
                .build());
    }

    @Nested
    @DisplayName("findByToken")
    class FindByToken {

        @Test
        @DisplayName("Should find a verification token by its string value")
        void shouldFindTokenByString() {
            verificationTokenRepository.save(VerificationToken.builder()
                    .token("verify-abc123")
                    .expiryDate(LocalDateTime.now().plusHours(24))
                    .user(user)
                    .build());

            Optional<VerificationToken> result = verificationTokenRepository.findByToken("verify-abc123");

            assertTrue(result.isPresent());
            assertEquals("verify-abc123", result.get().getToken());
            assertEquals(user.getId(), result.get().getUser().getId());
        }

        @Test
        @DisplayName("Should return empty Optional when token string is not found")
        void shouldReturnEmptyWhenTokenNotFound() {
            Optional<VerificationToken> result = verificationTokenRepository.findByToken("nonexistent-token");

            assertFalse(result.isPresent());
        }
    }
}
