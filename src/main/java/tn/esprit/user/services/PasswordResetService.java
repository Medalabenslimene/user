package tn.esprit.user.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.user.entity.PasswordResetToken;
import tn.esprit.user.entity.User;
import tn.esprit.user.repository.PasswordResetTokenRepository;
import tn.esprit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetService {

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    // Use BCrypt if you're already hashing passwords, otherwise remove this
    // private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public void requestPasswordReset(String email) {
        // Check if user exists (don't reveal if they don't)
        userRepository.findByEmail(email).orElseThrow(
            () -> new RuntimeException("If this email exists, a reset link has been sent.")
        );

        // Delete any existing tokens for this email
        tokenRepository.deleteByEmail(email);

        // Generate token
        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken = PasswordResetToken.builder()
            .token(token)
            .email(email)
            .expiresAt(LocalDateTime.now().plusHours(1))
            .used(false)
            .build();

        tokenRepository.save(resetToken);
        emailService.sendPasswordResetEmail(email, token);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Invalid or expired reset token."));

        if (resetToken.isUsed()) {
            throw new RuntimeException("This reset link has already been used.");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("This reset link has expired. Please request a new one.");
        }

        User user = userRepository.findByEmail(resetToken.getEmail())
            .orElseThrow(() -> new RuntimeException("User not found."));

        // If you hash passwords: user.setPwd(passwordEncoder.encode(newPassword));
        user.setPwd(newPassword);
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }
}