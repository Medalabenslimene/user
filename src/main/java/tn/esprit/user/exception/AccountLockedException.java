package tn.esprit.user.exception;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class AccountLockedException extends RuntimeException {

    private final LocalDateTime lockedUntil;
    private final int failedAttempts;

    public AccountLockedException(String message, LocalDateTime lockedUntil, int failedAttempts) {
        super(message);
        this.lockedUntil = lockedUntil;
        this.failedAttempts = failedAttempts;
    }

    public Map<String, Object> toResponseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("locked", true);
        body.put("message", getMessage());
        body.put("lockedUntil", lockedUntil != null ? lockedUntil.toString() : null);
        body.put("failedAttempts", failedAttempts);
        return body;
    }

    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public int getFailedAttempts() { return failedAttempts; }
}
