package tn.esprit.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.entity.User;
import tn.esprit.user.exception.UserBannedException;
import tn.esprit.user.repository.UserRepository;
import tn.esprit.user.services.EmailService;
import tn.esprit.user.services.GoogleAuthService;
import tn.esprit.user.services.LoginLogService;
import tn.esprit.user.services.PasswordService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class GoogleAuthController {

    @Autowired
    private GoogleAuthService googleAuthService;

    @Autowired
    private LoginLogService loginLogService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private EmailService emailService;

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

    @PostMapping("/complete-google-signup/{id}")
    public ResponseEntity<?> completeGoogleSignup(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String username = body.get("username");
            String password = body.get("password");
            String parentalEmail = body.get("parentalEmail");

            if (username == null || username.isBlank() || username.length() < 3) {
                return ResponseEntity.badRequest().body(Map.of("message", "Username must be at least 3 characters."));
            }
            if (password == null || password.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("message", "Password must be at least 6 characters."));
            }

            Optional<User> optUser = userRepository.findById(id);
            if (optUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found."));
            }

            User user = optUser.get();
            if (!Boolean.TRUE.equals(user.getNeedsSetup())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Setup already completed."));
            }

            // Ensure username uniqueness
            Optional<User> byUsername = userRepository.findByUsername(username);
            if (byUsername.isPresent() && !byUsername.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Username already taken."));
            }

            user.setUsername(username);
            user.setPwd(passwordService.hashPassword(password));
            if (parentalEmail != null && !parentalEmail.isBlank()) {
                user.setParentalEmail(parentalEmail.trim());
            }
            user.setNeedsSetup(false);
            User saved = userRepository.save(user);

            if (parentalEmail != null && !parentalEmail.isBlank()) {
                try {
                    emailService.sendParentalWelcomeEmail(parentalEmail.trim(), saved.getName());
                } catch (Exception ignored) {}
            }

            return ResponseEntity.ok(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to complete setup."));
        }
    }
}
