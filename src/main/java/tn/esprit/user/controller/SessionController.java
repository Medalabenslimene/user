package tn.esprit.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.entity.User;
import tn.esprit.user.repository.UserRepository;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users/session")
public class SessionController {

    @Autowired
    private UserRepository userRepository;

    /**
     * Validate whether a session token is still the active one for a user.
     * Returns { "valid": true } when the token matches the stored value.
     * Returns { "valid": false, "reason": "ANOTHER_LOGIN" } when a newer login
     * has replaced the token (i.e. another device logged in).
     *
     * Legacy rows where sessionToken is null are treated as valid so that
     * users registered before this feature was deployed are not kicked out.
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(
            @RequestParam Long userId,
            @RequestParam String token) {

        Optional<User> optUser = userRepository.findById(userId);
        if (optUser.isEmpty()) {
            return ResponseEntity.ok(Map.of("valid", false, "reason", "USER_NOT_FOUND"));
        }

        String stored = optUser.get().getSessionToken();

        // Null means the user predates session tracking — treat as valid.
        if (stored == null) {
            return ResponseEntity.ok(Map.of("valid", true));
        }

        if (stored.equals(token)) {
            return ResponseEntity.ok(Map.of("valid", true));
        }

        return ResponseEntity.ok(Map.of("valid", false, "reason", "ANOTHER_LOGIN"));
    }

    /**
     * Clear the session token on voluntary logout so the DB stays clean.
     */
    @Transactional
    @PostMapping("/{userId}/invalidate")
    public ResponseEntity<Void> invalidate(@PathVariable Long userId) {
        userRepository.clearSessionToken(userId);
        return ResponseEntity.noContent().build();
    }
}
