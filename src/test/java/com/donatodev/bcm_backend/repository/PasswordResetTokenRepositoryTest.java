package com.donatodev.bcm_backend.repository;

import java.time.LocalDateTime;
import java.time.Month;
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
import com.donatodev.bcm_backend.entity.PasswordResetToken;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;

@DataJpaTest
@ActiveProfiles("test")
class PasswordResetTokenRepositoryTest {

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private RolesRepository rolesRepository;

    @Autowired
    private ManagersRepository managersRepository;

    private Users user;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        passwordResetTokenRepository.deleteAll();
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
                .verified(true)
                .role(role)
                .manager(manager)
                .build());
    }

    @Nested
    @DisplayName("findByToken")
    class FindByToken {

        @Test
        @DisplayName("Should find a token by its string value")
        void shouldFindTokenByString() {
            PasswordResetToken token = passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .token("reset-token-abc123")
                    .expiryDate(LocalDateTime.of(2027, Month.JUNE, 15, 13, 0))
                    .user(user)
                    .build());

            Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("reset-token-abc123");

            assertTrue(result.isPresent());
            assertEquals(token.getId(), result.get().getId());
            assertEquals("reset-token-abc123", result.get().getToken());
        }

        @Test
        @DisplayName("Should return empty Optional when token string is not found")
        void shouldReturnEmptyWhenTokenNotFound() {
            Optional<PasswordResetToken> result = passwordResetTokenRepository.findByToken("nonexistent-token");

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("findByUser")
    class FindByUser {

        @Test
        @DisplayName("Should find a token by its associated user")
        void shouldFindTokenByUser() {
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .token("reset-token-xyz789")
                    .expiryDate(LocalDateTime.of(2027, Month.JUNE, 15, 14, 0))
                    .user(user)
                    .build());

            Optional<PasswordResetToken> result = passwordResetTokenRepository.findByUser(user);

            assertTrue(result.isPresent());
            assertEquals(user.getId(), result.get().getUser().getId());
        }

        @Test
        @DisplayName("Should return empty Optional when user has no reset token")
        void shouldReturnEmptyWhenUserHasNoToken() {
            Roles role = rolesRepository.save(Roles.builder().role("MANAGER").build());
            Managers otherManager = managersRepository.save(Managers.builder()
                    .firstName("Other")
                    .lastName("User")
                    .email("other@example.com")
                    .phoneNumber("111111")
                    .department("HR")
                    .build());
            Users otherUser = usersRepository.save(Users.builder()
                    .username("otheruser")
                    .passwordHash("hash456")
                    .verified(false)
                    .role(role)
                    .manager(otherManager)
                    .build());

            Optional<PasswordResetToken> result = passwordResetTokenRepository.findByUser(otherUser);

            assertFalse(result.isPresent());
        }
    }
}
