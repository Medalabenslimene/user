package tn.esprit.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.entity.User;
import tn.esprit.user.exception.AccountLockedException;
import tn.esprit.user.exception.UserBannedException;
import tn.esprit.user.services.FaceRecognitionService;
import tn.esprit.user.services.UserService;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for face recognition endpoints.
 * Integrates face registration into signup and face verification into login.
 */
@RestController
@RequestMapping("/api/users/face")
public class FaceController {

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    @Autowired
    private UserService userService;

    /**
     * Register a face during or after signup.
     * The user must already exist (created via /sign-up).
     * Body: { "image": "base64..." }
     */
    @PostMapping("/register/{userId}")
    public ResponseEntity<Map<String, Object>> registerFace(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        String image = body.get("image");
        if (image == null || image.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'image' field."));
        }

        // Verify user exists
        if (userService.getUserById(userId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found."));
        }

        Map<String, Object> result = faceRecognitionService.registerFace(userId, image);
        if (result.containsKey("error")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        // Mark face as registered on the user record
        userService.setFaceRegistered(userId, true);

        Map<String, Object> response = new HashMap<>(result);
        response.put("faceRegistered", true);
        return ResponseEntity.ok(response);
    }

    /**
     * Login using face recognition.
     * Body: { "email": "user@example.com", "image": "base64..." }
     * Performs all the same ban/lock checks as password login.
     */
    @PostMapping("/login")
    public ResponseEntity<?> faceLogin(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String image = body.get("image");

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required."));
        }
        if (image == null || image.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Face image is required."));
        }

        try {
            User user = userService.faceLogin(email, image, faceRecognitionService);
            return ResponseEntity.ok(user);
        } catch (UserBannedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.toResponseBody());
        } catch (AccountLockedException e) {
            return ResponseEntity.status(423).body(Map.of(
                    "type", "ACCOUNT_LOCKED",
                    "message", "Too many failed attempts. Your account is locked for 5 minutes.",
                    "minutesLeft", 5,
                    "lockedUntil", e.getLockedUntil() != null ? e.getLockedUntil().toString() : ""));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Check if a user has a registered face.
     * Can be called by email (for login page) or by userId.
     */
    @GetMapping("/status/{userId}")
    public ResponseEntity<Map<String, Object>> faceStatus(@PathVariable Long userId) {
        Map<String, Object> result = faceRecognitionService.getFaceStatus(userId);
        if (result.containsKey("error")) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Check face registration status by email (for the login page).
     */
    @GetMapping("/status-by-email")
    public ResponseEntity<Map<String, Object>> faceStatusByEmail(@RequestParam String email) {
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required."));
        }

        var optUser = userService.getUserByEmail(email);
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found.", "faceRegistered", false));
        }

        User user = optUser.get();
        boolean registered = Boolean.TRUE.equals(user.getFaceRegistered());
        Map<String, Object> response = new HashMap<>();
        response.put("faceRegistered", registered);
        response.put("userId", user.getId());

        if (registered) {
            // Also check the face service for consistency
            Map<String, Object> serviceStatus = faceRecognitionService.getFaceStatus(user.getId());
            response.put("serviceStatus", serviceStatus.get("registered"));
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Delete stored face data for a user.
     */
    @DeleteMapping("/delete/{userId}")
    public ResponseEntity<Map<String, Object>> deleteFace(@PathVariable Long userId) {
        Map<String, Object> result = faceRecognitionService.deleteFace(userId);
        if (result.containsKey("error")) {
            int status = result.get("error").toString().contains("No face data") ? 404 : 500;
            return ResponseEntity.status(status).body(result);
        }
        // Update user record
        userService.setFaceRegistered(userId, false);
        return ResponseEntity.ok(result);
    }

    /**
     * Health check for the face recognition service.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean healthy = faceRecognitionService.isServiceHealthy();
        if (healthy) {
            return ResponseEntity.ok(Map.of("status", "ok", "service", "face-recognition"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "unavailable", "message",
                        "Face recognition service is not running on port 5001."));
    }
}
