package com.donatodev.bcm_backend.auth;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;

import com.donatodev.bcm_backend.dto.*;
import com.donatodev.bcm_backend.entity.*;
import com.donatodev.bcm_backend.exception.RegistrationException;
import com.donatodev.bcm_backend.service.*;

import jakarta.validation.Valid;

/**
 * REST controller for handling user authentication, registration, 
 * email verification, password reset, and retrieving the current user.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final VerificationTokenService verificationTokenService;
    private final IEmailService emailService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
    

    @Autowired
    public AuthController(
            AuthService authService,
            UserService userService,
            VerificationTokenService verificationTokenService,
            IEmailService emailService,
            PasswordResetTokenService passwordResetTokenService) {
        this.authService = authService;
        this.userService = userService;
        this.verificationTokenService = verificationTokenService;
        this.emailService = emailService;
        this.passwordResetTokenService = passwordResetTokenService;
    }
    
    public AuthController() {
        this(null, null, null, null, null);
    }

    /**
     * Verifies the user account using the provided verification token.
     *
     * @param token the verification token sent via email
     * @return 200 OK if successful, otherwise 400 BAD REQUEST
     */
    @GetMapping("/verify")
    public ResponseEntity<String> verifyUser(@RequestParam("token") String token) {
        try {
            VerificationToken verificationToken = verificationTokenService.getByToken(token);

            if (verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Verification token expired. Please register again.");
            }

            Users user = verificationToken.getUser();
            user.setVerified(true);
            userService.save(user);
            verificationTokenService.deleteToken(verificationToken);

            return ResponseEntity.ok("Email verified successfully. You can now log in.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid or expired verification token.");
        }
    }

    /**
     * Registers a new user and sends a verification email.
     *
     * @param userDTO the user registration data
     * @return 201 CREATED if successful, otherwise error
     */
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody UserDTO userDTO) {
        try {
            Users createdUser = userService.registerUser(userDTO);
            VerificationToken token = verificationTokenService.createToken(createdUser);
            String verificationLink = "http://localhost:8090/auth/verify?token=" + token.getToken();
            emailService.sendVerificationEmail(createdUser.getManager().getEmail(), verificationLink);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Registered user. Check your email to confirm your account.");
        } catch (Exception e) {
        	throw new RegistrationException("Internal error: " + e.getMessage(), e);
        }
    }

    /**
     * Authenticates a user and returns a JWT token.
     *
     * @param request the login request with username and password
     * @return JWT token if authentication is successful
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody AuthRequestDTO request) {
        String token = authService.authenticate(request.username(), request.password());
        return ResponseEntity.ok(new AuthResponseDTO(token));
    }

    /**
     * Handles password reset requests by sending a reset link.
     *
     * @param request contains the user's email
     * @return 200 OK if email sent, otherwise 404 NOT FOUND
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequestDTO request) {
        return userService.findByEmail(request.email())
                .map(user -> {
                    PasswordResetToken token = passwordResetTokenService.createToken(user);
                    String resetLink = "http://localhost:8090/auth/reset-password?token=" + token.getToken();
                    emailService.sendResetPasswordEmail(request.email(), resetLink);
                    return ResponseEntity.ok("Password reset link sent to your email.");
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("User not found with that email."));
    }

    /**
     * Resets the user's password using a valid reset token.
     *
     * @param request contains the reset token and new password
     * @return 200 OK if successful, otherwise 400 BAD REQUEST
     */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequestDTO request) {
        try {
            PasswordResetToken token = passwordResetTokenService.getByToken(request.token());

            if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Token expired.");
            }

            Users user = token.getUser();
            userService.updatePassword(user, request.newPassword());
            passwordResetTokenService.deleteToken(token);

            return ResponseEntity.ok("Password successfully reset.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid token.");
        }
    }

    /**
     * Returns the profile of the currently authenticated user.
     *
     * @return user profile if authenticated, otherwise 401 UNAUTHORIZED
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> getCurrentUser() {
        if (!isUserAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userService.getCurrentUserProfile());
    }
    
    @PostMapping("/complete-invite")
    public ResponseEntity<Void> completeInvite(@Valid @RequestBody CompleteInviteRequest req) {
        userService.completeInvite(req.getToken(), req.getPassword());
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Checks whether the current user is authenticated and not anonymous.
     *
     * @return true if authenticated, false otherwise
     */
    public boolean isUserAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;
        Object principal = authentication.getPrincipal();
        return !(trustResolver.isAnonymous(authentication)
                || !authentication.isAuthenticated()
                || (principal instanceof String && "anonymousUser".equals(principal)));
    }
}
