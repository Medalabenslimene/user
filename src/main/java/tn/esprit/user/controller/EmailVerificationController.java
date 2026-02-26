package tn.esprit.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.entity.User;
import tn.esprit.user.services.EmailVerificationService;

import java.util.Map;

@RestController
@RequestMapping("/api/users")

public class EmailVerificationController {

    @Autowired
    private EmailVerificationService emailVerificationService;

    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String code = request.get("code");

            if (email == null || code == null) {
                return ResponseEntity.badRequest().body("Email and code are required.");
            }

            User user = emailVerificationService.verifyCode(email, code);
            return ResponseEntity.ok(Map.of(
                "message", "Email verified successfully!",
                "role", user.getRole().toString(),
                "user", user
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/resend-code")
    public ResponseEntity<String> resendCode(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            emailVerificationService.resendCode(email);
            return ResponseEntity.ok("Verification code resent successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}