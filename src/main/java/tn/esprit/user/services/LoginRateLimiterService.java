package tn.esprit.user.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class LoginRateLimiterService {

    private static final String KEY_PREFIX = "login:attempts:";
    private static final int MAX_ATTEMPTS = 3;
    private static final long LOCK_DURATION_MINUTES = 15;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void recordFailedAttempt(String email) {
        try {
            String key = KEY_PREFIX + email;
            Long attempts = redisTemplate.opsForValue().increment(key);
            if (attempts == 1) {
                redisTemplate.expire(key, LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            System.out.println("Redis unavailable - skipping rate limit recording: " + e.getMessage());
        }
    }

    public boolean isBlocked(String email) {
        try {
            String key = KEY_PREFIX + email;
            String attempts = redisTemplate.opsForValue().get(key);
            if (attempts == null) return false;
            return Integer.parseInt(attempts) >= MAX_ATTEMPTS;
        } catch (Exception e) {
            System.out.println("Redis unavailable - skipping block check: " + e.getMessage());
            return false;
        }
    }

    public int getAttempts(String email) {
        try {
            String key = KEY_PREFIX + email;
            String attempts = redisTemplate.opsForValue().get(key);
            return attempts == null ? 0 : Integer.parseInt(attempts);
        } catch (Exception e) {
            return 0;
        }
    }

    public void resetAttempts(String email) {
        try {
            redisTemplate.delete(KEY_PREFIX + email);
        } catch (Exception e) {
            System.out.println("Redis unavailable - skipping reset: " + e.getMessage());
        }
    }

    public long getTimeToUnlock(String email) {
        try {
            String key = KEY_PREFIX + email;
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl == null ? 0 : ttl;
        } catch (Exception e) {
            return 0;
        }
    }
}
