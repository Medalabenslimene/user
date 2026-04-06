package tn.esprit.user.services;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Password service for hashing and verifying passwords using BCrypt
 * Provides secure password storage with salt and proper strength
 */
@Service
public class PasswordService {

    private final BCryptPasswordEncoder passwordEncoder;

    public PasswordService() {
        // Use BCrypt with default strength (10 rounds)
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Hash a plain text password
     * @param plainPassword The plain text password to hash
     * @return The hashed password with salt
     */
    public String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return passwordEncoder.encode(plainPassword);
    }

    /**
     * Verify a plain text password against a hashed password
     * @param plainPassword The plain text password to verify
     * @param hashedPassword The hashed password to verify against
     * @return true if passwords match, false otherwise
     */
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(plainPassword, hashedPassword);
    }

    /**
     * Check if a password is already hashed (BCrypt format)
     * @param password The password to check
     * @return true if the password appears to be BCrypt hashed
     */
    public boolean isHashedPassword(String password) {
        if (password == null) {
            return false;
        }
        // BCrypt hashes start with $2a$, $2b$, or $2y$ and are 60 characters long
        return password.startsWith("$2") && password.length() == 60;
    }

    /**
     * Migrate a plain text password to hashed format
     * @param plainPassword The plain text password
     * @return The hashed password
     */
    public String migratePassword(String plainPassword) {
        if (isHashedPassword(plainPassword)) {
            return plainPassword; // Already hashed
        }
        return hashPassword(plainPassword);
    }
}
