package tn.esprit.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.services.PasswordResetService;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class PasswordResetController {

    @Autowired
    private PasswordResetService passwordResetService;

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            if (email == null || email.isBlank()) {
                return ResponseEntity.badRequest().body("Email is required.");
            }
            passwordResetService.requestPasswordReset(email.trim());
            // Always return success message to avoid email enumeration
            return ResponseEntity.ok("If this email is registered, you will receive a reset link shortly.");
        } catch (Exception e) {
            // Still return 200 to avoid revealing if email exists
            return ResponseEntity.ok("If this email is registered, you will receive a reset link shortly.");
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            String newPassword = request.get("newPassword");

            if (token == null || token.isBlank()) {
                return ResponseEntity.badRequest().body("Token is required.");
            }
            if (newPassword == null || newPassword.length() < 6) {
                return ResponseEntity.badRequest().body("Password must be at least 6 characters.");
            }

            passwordResetService.resetPassword(token, newPassword);
            return ResponseEntity.ok("Password reset successfully. You can now log in.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}