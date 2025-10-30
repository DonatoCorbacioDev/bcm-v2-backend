package com.donatodev.bcm_backend.service;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.PasswordResetToken;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.PasswordResetTokenRepository;

/**
 * Unit tests for {@link PasswordResetTokenService}.
 * <p>
 * This test class verifies the behavior of the service responsible for managing
 * password reset tokens. Includes tests for:
 * <ul>
 *   <li>Creating a new token and removing the old one</li>
 *   <li>Retrieving a token by string value</li>
 *   <li>Handling the case when a token is not found</li>
 *   <li>Deleting an existing token</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class PasswordResetTokenServiceTest {

    @Mock
    private PasswordResetTokenRepository repository;

    @InjectMocks
    private PasswordResetTokenService service;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: PasswordResetTokenService")
    @SuppressWarnings("unused")
    class VerifyPasswordResetTokenService {

    	/**
         * Should create a new password reset token, deleting the old one if it exists.
         */
        @Test
        @Order(1)
        @DisplayName("Should create a new token and delete old one if present")
        void shouldCreateTokenAndDeleteOld() {
            Users user = Users.builder().id(1L).build();
            PasswordResetToken oldToken = PasswordResetToken.builder().id(1L).user(user).build();

            when(repository.findByUser(user)).thenReturn(Optional.of(oldToken));
            doNothing().when(repository).delete(oldToken);
            when(repository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PasswordResetToken result = service.createToken(user);

            verify(repository).delete(oldToken);
            verify(repository).save(any(PasswordResetToken.class));
            assertEquals(user, result.getUser());
            assertNotNull(result.getToken());
            assertTrue(result.getExpiryDate().isAfter(LocalDateTime.now()));
        }

        /**
         * Should return a token if found by its string value.
         */
        @Test
        @Order(2)
        @DisplayName("Should return token if found")
        void shouldReturnTokenIfFound() {
            PasswordResetToken token = PasswordResetToken.builder().token("abc").build();
            when(repository.findByToken("abc")).thenReturn(Optional.of(token));

            PasswordResetToken result = service.getByToken("abc");
            assertEquals(token, result);
        }

        /**
         * Should throw a RuntimeException if no token is found with the given string.
         */
        @Test
        @Order(3)
        @DisplayName("Should throw exception if token not found")
        void shouldThrowIfTokenNotFound() {
            when(repository.findByToken("invalid")).thenReturn(Optional.empty());
            RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getByToken("invalid"));
            assertEquals("Reset token not found", ex.getMessage());
        }

        /**
         * Should delete the given token from the repository.
         */
        @Test
        @Order(4)
        @DisplayName("Should delete the given token")
        void shouldDeleteToken() {
            PasswordResetToken token = PasswordResetToken.builder().token("abc").build();
            doNothing().when(repository).delete(token);

            service.deleteToken(token);
            verify(repository).delete(token);
        }
    }
}