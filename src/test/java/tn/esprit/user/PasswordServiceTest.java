package tn.esprit.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.esprit.user.services.PasswordService;

import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {

    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
    }

    // ── hashPassword ──────────────────────────────────────────────────────────

    @Test
    void hashPassword_returnsNonNullBcryptHash() {
        String hash = passwordService.hashPassword("secret123");
        assertNotNull(hash);
        assertTrue(hash.startsWith("$2"), "Hash should start with $2 (BCrypt prefix)");
        assertEquals(60, hash.length(), "BCrypt hash is always 60 characters");
    }

    @Test
    void hashPassword_producesDifferentHashesForSameInput() {
        // BCrypt uses a random salt, so two hashes of the same password must differ
        String hash1 = passwordService.hashPassword("myPassword");
        String hash2 = passwordService.hashPassword("myPassword");
        assertNotEquals(hash1, hash2, "BCrypt should produce unique hashes each time");
    }

    @Test
    void hashPassword_nullPassword_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> passwordService.hashPassword(null));
    }

    @Test
    void hashPassword_emptyPassword_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> passwordService.hashPassword(""));
    }

    @Test
    void hashPassword_blankPassword_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> passwordService.hashPassword("   "));
    }

    // ── verifyPassword ────────────────────────────────────────────────────────

    @Test
    void verifyPassword_correctPassword_returnsTrue() {
        String plain = "correctPass!";
        String hash = passwordService.hashPassword(plain);
        assertTrue(passwordService.verifyPassword(plain, hash));
    }

    @Test
    void verifyPassword_wrongPassword_returnsFalse() {
        String hash = passwordService.hashPassword("realPassword");
        assertFalse(passwordService.verifyPassword("wrongPassword", hash));
    }

    @Test
    void verifyPassword_nullPlainPassword_returnsFalse() {
        String hash = passwordService.hashPassword("anything");
        assertFalse(passwordService.verifyPassword(null, hash));
    }

    @Test
    void verifyPassword_nullHashedPassword_returnsFalse() {
        assertFalse(passwordService.verifyPassword("anything", null));
    }

    @Test
    void verifyPassword_bothNull_returnsFalse() {
        assertFalse(passwordService.verifyPassword(null, null));
    }

    // ── isHashedPassword ──────────────────────────────────────────────────────

    @Test
    void isHashedPassword_bcryptHash_returnsTrue() {
        String hash = passwordService.hashPassword("test");
        assertTrue(passwordService.isHashedPassword(hash));
    }

    @Test
    void isHashedPassword_plainText_returnsFalse() {
        assertFalse(passwordService.isHashedPassword("plainTextPassword"));
    }

    @Test
    void isHashedPassword_null_returnsFalse() {
        assertFalse(passwordService.isHashedPassword(null));
    }

    @Test
    void isHashedPassword_shortBcryptLikeString_returnsFalse() {
        // Starts with $2 but wrong length
        assertFalse(passwordService.isHashedPassword("$2a$10$short"));
    }

    // ── migratePassword ───────────────────────────────────────────────────────

    @Test
    void migratePassword_alreadyHashed_returnsSameValue() {
        String hash = passwordService.hashPassword("original");
        String result = passwordService.migratePassword(hash);
        assertEquals(hash, result, "Hashed passwords should not be re-hashed");
    }

    @Test
    void migratePassword_plainText_returnsNewBcryptHash() {
        String plain = "plainOldPassword";
        String result = passwordService.migratePassword(plain);
        assertTrue(passwordService.isHashedPassword(result));
        assertTrue(passwordService.verifyPassword(plain, result));
    }
}
