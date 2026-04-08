package tn.esprit.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.services.FaceRecognitionService;

import java.util.Map;

/**
 * REST controller that exposes face recognition endpoints,
 * proxying to the Python Flask microservice.
 */
@RestController
@RequestMapping("/api/users/face")
public class FaceController {

    @Autowired
    private FaceRecognitionService faceRecognitionService;

    /**
     * Register a user's face from a base64 photo.
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
        Map<String, Object> result = faceRecognitionService.registerFace(userId, image);
        if (result.containsKey("error")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Verify a live photo against the registered face.
     * Body: { "image": "base64..." }
     */
    @PostMapping("/verify/{userId}")
    public ResponseEntity<Map<String, Object>> verifyFace(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        String image = body.get("image");
        if (image == null || image.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'image' field."));
        }
        Map<String, Object> result = faceRecognitionService.verifyFace(userId, image);
        if (result.containsKey("error")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Check if a user has a registered face.
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
     * Delete stored face data for a user.
     */
    @DeleteMapping("/delete/{userId}")
    public ResponseEntity<Map<String, Object>> deleteFace(@PathVariable Long userId) {
        Map<String, Object> result = faceRecognitionService.deleteFace(userId);
        if (result.containsKey("error")) {
            int status = result.get("error").toString().contains("No face data") ? 404 : 500;
            return ResponseEntity.status(status).body(result);
        }
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
                .body(Map.of("status", "unavailable", "message", "Face recognition service is not running on port 5001."));
    }
}
