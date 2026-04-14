package tn.esprit.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.entity.User;
import tn.esprit.user.exception.UserBannedException;
import tn.esprit.user.services.GoogleAuthService;
import tn.esprit.user.services.LoginLogService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class GoogleAuthController {

    @Autowired
    private GoogleAuthService googleAuthService;

    @Autowired
    private LoginLogService loginLogService;

    @PostMapping("/google-auth")
    public ResponseEntity<?> googleAuth(@RequestBody Map<String, String> body, HttpServletRequest request) {
        try {
            String idToken = body.get("idToken");
            if (idToken == null || idToken.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Google ID token is required."));
            }
            User user = googleAuthService.authenticateWithGoogle(idToken);
            loginLogService.record(user, "GOOGLE", true, request);
            return ResponseEntity.ok(user);
        } catch (UserBannedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.toResponseBody());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
