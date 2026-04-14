package tn.esprit.user.services;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tn.esprit.user.entity.LoginLog;
import tn.esprit.user.entity.User;
import tn.esprit.user.repository.LoginLogRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LoginLogService {

    @Autowired
    private LoginLogRepository loginLogRepository;

    public void record(User user, String method, boolean success, HttpServletRequest request) {
        String ip = null;
        String ua = null;
        if (request != null) {
            String xff = request.getHeader("X-Forwarded-For");
            ip = (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
            ua = request.getHeader("User-Agent");
            if (ua != null && ua.length() > 500) ua = ua.substring(0, 500);
        }
        LoginLog log = LoginLog.builder()
                .userId(user != null ? user.getId() : null)
                .email(user != null ? user.getEmail() : null)
                .loginMethod(method)
                .success(success)
                .ipAddress(ip)
                .userAgent(ua)
                .loginTime(LocalDateTime.now())
                .build();
        try {
            loginLogRepository.save(log);
        } catch (Exception ignored) {
            // Never fail login because of audit logging
        }
    }

    public List<LoginLog> findAll() {
        return loginLogRepository.findAllByOrderByLoginTimeDesc();
    }

    public List<LoginLog> findByUser(Long userId) {
        return loginLogRepository.findByUserIdOrderByLoginTimeDesc(userId);
    }
}
