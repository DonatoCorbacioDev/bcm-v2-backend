package com.donatodev.bcm_backend.service;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.exception.EmailSendingException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link IEmailService} that sends real emails
 * using Spring's {@link JavaMailSender}.
 * <p>
 * Supports sending verification and password reset emails with HTML content.
 */
@Service
@RequiredArgsConstructor
public class EmailService implements IEmailService {

    protected final JavaMailSender mailSender;

    /**
     * Sends an account verification email to the specified recipient.
     *
     * @param to               the recipient's email address
     * @param verificationLink the verification link to include in the email
     * @throws RuntimeException if there is an error while sending the email
     */
    @Override
    public void sendVerificationEmail(String to, String verificationLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject("Verify your account");
            helper.setText(
                "<p>Hello!</p><p>Click the link below to verify your email:</p>" +
                "<p><a href=\"" + verificationLink + "\">Verify email</a></p>", true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new EmailSendingException("Error sending verification email", e);
        }
    }

    /**
     * Sends a password reset email to the specified recipient.
     *
     * @param to        the recipient's email address
     * @param resetLink the password reset link to include in the email
     * @throws RuntimeException if there is an error while sending the email
     */
    @Override
    public void sendResetPasswordEmail(String to, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject("Reset your password");
            helper.setText(
                "<p>Click below to reset your password:</p>" +
                "<p><a href=\"" + resetLink + "\">Reset Password</a></p>", true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new EmailSendingException("Error sending reset password email", e);
        }
    }
}
