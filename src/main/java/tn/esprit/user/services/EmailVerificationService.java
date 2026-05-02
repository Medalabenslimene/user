package tn.esprit.user.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.user.entity.User;
import tn.esprit.user.repository.UserRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class EmailVerificationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Transactional
    public void sendVerificationCode(User user) {
        // Generate 6-digit code
        String code = String.format("%06d", new SecureRandom().nextInt(1000000));

        user.setVerificationCode(code);
        user.setVerificationCodeExpiry(LocalDateTime.now().plusMinutes(10));
        user.setEmailVerified(false);
        userRepository.save(user);

        emailService.sendVerificationCode(user.getEmail(), code);
    }

    @Transactional
    public User verifyCode(String email, String code) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found."));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new RuntimeException("Email already verified.");
        }

        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(code)) {
            throw new RuntimeException("Invalid verification code.");
        }

        if (user.getVerificationCodeExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Code expired. Please request a new one.");
        }

        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiry(null);
        return userRepository.save(user);
    }

    @Transactional
    public void resendCode(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found."));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new RuntimeException("Email already verified.");
        }

        sendVerificationCode(user);
    }
}