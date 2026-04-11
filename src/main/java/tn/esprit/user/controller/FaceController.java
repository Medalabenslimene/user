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

import java.io.IOException;
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

        // Save the face image to /var/www/faces/{userId}.jpg and store the URL on the user
        String faceImageUrl = null;
        try {
            faceImageUrl = userService.saveFaceImage(userId, image);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save face image: " + e.getMessage()));
        }

        // Extract the face embedding and store it in the Python service
        Map<String, Object> result = faceRecognitionService.registerFace(userId, image);
        if (result.containsKey("error")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        // Mark face as registered on the user record
        userService.setFaceRegistered(userId, true);

        Map<String, Object> response = new HashMap<>(result);
        response.put("faceRegistered", true);
        response.put("faceImageUrl", faceImageUrl);
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
     * Login using face recognition without providing an email.
     * The Python service scans all stored embeddings to identify the user.
     * Body: { "image": "base64..." }
     */
    @PostMapping("/identify-login")
    public ResponseEntity<?> faceIdentifyLogin(@RequestBody Map<String, String> body) {
        String image = body.get("image");
        if (image == null || image.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Face image is required."));
        }

        // Ask Python service to find the matching user
        Map<String, Object> identifyResult = faceRecognitionService.identifyFace(image);
        if (identifyResult.containsKey("error")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", identifyResult.get("error").toString()));
        }

        Object rawUserId = identifyResult.get("userId");
        if (rawUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Face not recognized. Please use password login."));
        }

        Long userId;
        try {
            userId = Long.parseLong(rawUserId.toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Invalid user ID returned by face service."));
        }

        try {
            User user = userService.faceLoginById(userId);
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

        // Use Python service as the source of truth for the face embedding.
        // The DB flag can get out of sync if a previous save failed.
        boolean dbFlag = Boolean.TRUE.equals(user.getFaceRegistered());
        boolean serviceFlag = false;
        try {
            Map<String, Object> serviceStatus = faceRecognitionService.getFaceStatus(user.getId());
            serviceFlag = Boolean.TRUE.equals(serviceStatus.get("registered"));
        } catch (Exception ignored) {}

        // If the face file exists on the service but DB flag is missing, sync it
        if (serviceFlag && !dbFlag) {
            userService.setFaceRegistered(user.getId(), true);
        }
        // If DB says registered but service has no file, clear the stale flag
        if (dbFlag && !serviceFlag) {
            userService.setFaceRegistered(user.getId(), false);
        }

        boolean registered = serviceFlag || dbFlag;

        Map<String, Object> response = new HashMap<>();
        response.put("faceRegistered", registered);
        response.put("userId", user.getId());
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
        // Update user record — clear both the flag and the stored image URL
        userService.setFaceRegistered(userId, false);
        userService.clearFaceImageUrl(userId);

        // Also delete the physical image file from /var/www/faces/
        userService.deleteFaceImageFile(userId);

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
