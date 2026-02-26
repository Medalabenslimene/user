package tn.esprit.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import tn.esprit.user.entity.User;
import tn.esprit.user.exception.UserBannedException;
import tn.esprit.user.services.UserService;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")

public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        try {
            String email = loginRequest.get("email");
            String pwd = loginRequest.get("pwd");
            User user = userService.login(email, pwd);
            return ResponseEntity.ok(user);
        } catch (UserBannedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.toResponseBody());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/sign-up")
    public ResponseEntity<?> signUp(@RequestBody User user) {
        try {
            User createdUser = userService.createUser(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Registration failed. Please try again."));
        }
    }

    @GetMapping("/get-users")
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/get-user-by-id/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/update-user-by-id/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        return userService.updateUser(id, user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/ban-user/{id}")
    public ResponseEntity<?> banUser(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            String reason = request.get("reason") != null ? request.get("reason").toString() : null;
            String duration = request.get("duration") != null ? request.get("duration").toString() : null;
            String banExpiresAt = request.get("banExpiresAt") != null ? request.get("banExpiresAt").toString() : null;
            return userService.banUser(id, reason, duration, banExpiresAt)
                    .map(user -> ResponseEntity.ok((Object) user))
                    .orElse(ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "An unexpected error occurred."));
        }
    }

    @PutMapping("/unban-user/{id}")
    public ResponseEntity<?> unbanUser(@PathVariable Long id) {
        try {
            return userService.unbanUser(id)
                    .map(user -> ResponseEntity.ok((Object) user))
                    .orElse(ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "An unexpected error occurred."));
        }
    }

    @PutMapping("/update-profile/{id}")
    public ResponseEntity<?> updateProfile(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String name = request.get("name");
            String username = request.get("username");
            return userService.updateProfile(id, name, username)
                    .map(user -> ResponseEntity.ok((Object) user))
                    .orElse(ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/upload-avatar/{id}")
    public ResponseEntity<?> uploadAvatar(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "File is empty."));
            }
            String avatarUrl = userService.saveAvatar(id, file);
            return ResponseEntity.ok(avatarUrl);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to upload avatar."));
        }
    }

    @GetMapping("/avatars/{filename:.+}")
    public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
        try {
            Path filePath = Paths.get("uploads/avatars").resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                String contentType = "image/jpeg";
                if (filename.endsWith(".png")) contentType = "image/png";
                else if (filename.endsWith(".gif")) contentType = "image/gif";
                else if (filename.endsWith(".webp")) contentType = "image/webp";
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(resource);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/delete-user-by-id/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (userService.deleteUser(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
    @PostMapping("/change-password/{id}")
public ResponseEntity<?> changePassword(@PathVariable Long id, @RequestBody Map<String, String> request) {
    try {
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");

        if (currentPassword == null || currentPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Current password is required."));
        }
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "New password must be at least 6 characters."));
        }

        userService.changePassword(id, currentPassword, newPassword);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
}
