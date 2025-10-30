package com.donatodev.bcm_backend.service;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.entity.VerificationToken;
import com.donatodev.bcm_backend.repository.VerificationTokenRepository;

/**
 * Unit tests for {@link VerificationTokenService}.
 * <p>
 * Verifies the behavior of token generation, retrieval, and deletion for email verification logic.
 * This includes:
 * <ul>
 *   <li>Creating a token with a valid expiry date</li>
 *   <li>Finding a token by its value</li>
 *   <li>Handling missing token cases</li>
 *   <li>Deleting an existing token</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("test")
class VerificationTokenServiceTest {

    @Mock
    private VerificationTokenRepository tokenRepository;

    @InjectMocks
    private VerificationTokenService tokenService;

    /**
     * Should generate a new token for the user and save it to the repository.
     */
    @Test
    @Order(1)
    @DisplayName("Create token and save it")
    void shouldCreateAndSaveToken() {
        Users user = Users.builder().id(1L).build();

        when(tokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VerificationToken token = tokenService.createToken(user);

        assertNotNull(token.getToken());
        assertEquals(user, token.getUser());
        assertTrue(token.getExpiryDate().isAfter(LocalDateTime.now()));
    }

    /**
     * Should retrieve the token object by its string value.
     */
    @Test
    @Order(2)
    @DisplayName("Get token by string")
    void shouldGetByToken() {
        VerificationToken token = VerificationToken.builder()
                .token("abc")
                .user(Users.builder().id(1L).build())
                .expiryDate(LocalDateTime.now().plusHours(1))
                .build();

        when(tokenRepository.findByToken("abc")).thenReturn(Optional.of(token));

        VerificationToken result = tokenService.getByToken("abc");

        assertEquals("abc", result.getToken());
    }

    /**
     * Should throw RuntimeException if the token is not found in the database.
     */
    @Test
    @Order(3)
    @DisplayName("Throw if token not found")
    void shouldThrowIfTokenNotFound() {
        when(tokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> tokenService.getByToken("missing"));
        assertEquals("Invalid verification token", ex.getMessage());
    }

    /**
     * Should delete the given token from the repository.
     */
    @Test
    @Order(4)
    @DisplayName("Delete token")
    void shouldDeleteToken() {
        VerificationToken token = VerificationToken.builder().token("abc").build();

        tokenService.deleteToken(token);

        verify(tokenRepository).delete(token);
    }
}