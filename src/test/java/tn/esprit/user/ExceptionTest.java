package tn.esprit.user;

import org.junit.jupiter.api.Test;
import tn.esprit.user.exception.AccountLockedException;
import tn.esprit.user.exception.UserBannedException;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionTest {

    // ── AccountLockedException ────────────────────────────────────────────────

    @Test
    void accountLockedException_getters_returnCorrectValues() {
        LocalDateTime lockTime = LocalDateTime.of(2026, 5, 2, 12, 0);
        AccountLockedException ex = new AccountLockedException("Locked", lockTime, 3);

        assertEquals("Locked", ex.getMessage());
        assertEquals(lockTime, ex.getLockedUntil());
        assertEquals(3, ex.getFailedAttempts());
    }

    @Test
    void accountLockedException_toResponseBody_containsRequiredKeys() {
        LocalDateTime lockTime = LocalDateTime.of(2026, 5, 2, 12, 0);
        AccountLockedException ex = new AccountLockedException("Too many attempts", lockTime, 3);

        Map<String, Object> body = ex.toResponseBody();

        assertTrue((Boolean) body.get("locked"));
        assertEquals("Too many attempts", body.get("message"));
        assertNotNull(body.get("lockedUntil"));
        assertEquals(3, body.get("failedAttempts"));
    }

    @Test
    void accountLockedException_nullLockedUntil_toResponseBodyHasNullLockedUntil() {
        AccountLockedException ex = new AccountLockedException("Locked", null, 0);

        Map<String, Object> body = ex.toResponseBody();
        assertNull(body.get("lockedUntil"));
    }

    @Test
    void accountLockedException_isRuntimeException() {
        AccountLockedException ex = new AccountLockedException("msg", null, 0);
        assertInstanceOf(RuntimeException.class, ex);
    }

    // ── UserBannedException ───────────────────────────────────────────────────

    @Test
    void userBannedException_getters_returnCorrectValues() {
        UserBannedException ex = new UserBannedException(
                "Banned", "Spam", "7 days", "2026-05-09T00:00:00Z");

        assertEquals("Banned", ex.getMessage());
        assertEquals("Spam", ex.getBanReason());
        assertEquals("7 days", ex.getBanDuration());
        assertEquals("2026-05-09T00:00:00Z", ex.getBanExpiresAt());
        assertFalse(ex.isPermanent());
    }

    @Test
    void userBannedException_permanentDuration_isPermanentTrue() {
        UserBannedException ex = new UserBannedException(
                "Banned", "Abuse", "permanent", null);
        assertTrue(ex.isPermanent());
    }

    @Test
    void userBannedException_permanentDurationCaseInsensitive_isPermanentTrue() {
        UserBannedException ex = new UserBannedException(
                "Banned", "Abuse", "PERMANENT", null);
        assertTrue(ex.isPermanent());
    }

    @Test
    void userBannedException_toResponseBody_containsRequiredKeys() {
        UserBannedException ex = new UserBannedException(
                "You are banned", "Cheating", "permanent", null);

        Map<String, Object> body = ex.toResponseBody();

        assertTrue((Boolean) body.get("banned"));
        assertEquals("You are banned", body.get("message"));
        assertEquals("Cheating", body.get("banReason"));
        assertEquals("permanent", body.get("banDuration"));
        assertNull(body.get("banExpiresAt"));
        assertTrue((Boolean) body.get("permanent"));
    }

    @Test
    void userBannedException_temporaryBan_toResponseBodyHasExpiresAt() {
        UserBannedException ex = new UserBannedException(
                "Banned", "Spam", "7 days", "2026-05-09T00:00:00Z");

        Map<String, Object> body = ex.toResponseBody();
        assertEquals("2026-05-09T00:00:00Z", body.get("banExpiresAt"));
        assertFalse((Boolean) body.get("permanent"));
    }

    @Test
    void userBannedException_isRuntimeException() {
        UserBannedException ex = new UserBannedException("msg", null, null, null);
        assertInstanceOf(RuntimeException.class, ex);
    }
}
