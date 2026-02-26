package tn.esprit.user.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public class UserBannedException extends RuntimeException {

    private final String banReason;
    private final String banDuration;
    private final String banExpiresAt;
    private final boolean permanent;

    public UserBannedException(String message, String banReason, String banDuration, String banExpiresAt) {
        super(message);
        this.banReason = banReason;
        this.banDuration = banDuration;
        this.banExpiresAt = banExpiresAt;
        this.permanent = "permanent".equalsIgnoreCase(banDuration);
    }

    public Map<String, Object> toResponseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("banned", true);
        body.put("message", getMessage());
        body.put("banReason", banReason);
        body.put("banDuration", banDuration);
        body.put("banExpiresAt", banExpiresAt);
        body.put("permanent", permanent);
        return body;
    }

    public String getBanReason() { return banReason; }
    public String getBanDuration() { return banDuration; }
    public String getBanExpiresAt() { return banExpiresAt; }
    public boolean isPermanent() { return permanent; }
}
