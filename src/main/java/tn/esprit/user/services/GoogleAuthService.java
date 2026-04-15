package tn.esprit.user.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.User;
import tn.esprit.user.exception.UserBannedException;
import tn.esprit.user.repository.UserRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
public class GoogleAuthService {

    @Value("${google.client.id}")
    private String googleClientId;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    public User authenticateWithGoogle(String idToken) {
        JsonNode tokenInfo = verifyToken(idToken);

        String aud = tokenInfo.has("aud") ? tokenInfo.get("aud").asText() : "";
        String email = tokenInfo.has("email") ? tokenInfo.get("email").asText() : "";
        String emailVerified = tokenInfo.has("email_verified") ? tokenInfo.get("email_verified").asText() : "false";
        String name = tokenInfo.has("name") ? tokenInfo.get("name").asText() : "";
        String picture = tokenInfo.has("picture") ? tokenInfo.get("picture").asText() : null;

        if (!googleClientId.equals(aud)) {
            throw new RuntimeException("Invalid Google token: client mismatch.");
        }

        if (!"true".equals(emailVerified)) {
            throw new RuntimeException("Google account email is not verified.");
        }

        if (email.isEmpty()) {
            throw new RuntimeException("Could not retrieve email from Google account.");
        }

        Optional<User> existing = userRepository.findByEmail(email);

        if (existing.isPresent()) {
            User user = existing.get();

            if (Boolean.TRUE.equals(user.getBanned())) {
                if (user.getBanExpiresAt() != null && !user.getBanExpiresAt().isEmpty()) {
                    try {
                        Instant expiresAt = Instant.parse(user.getBanExpiresAt());
                        if (expiresAt.isBefore(Instant.now())) {
                            user.setBanned(false);
                            user.setBanReason(null);
                            user.setBanDuration(null);
                            user.setBanExpiresAt(null);
                            userRepository.save(user);
                        } else {
                            throw new UserBannedException(
                                    "Your account is banned.",
                                    user.getBanReason(),
                                    user.getBanDuration(),
                                    user.getBanExpiresAt());
                        }
                    } catch (UserBannedException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new UserBannedException(
                                "Your account is banned.",
                                user.getBanReason(),
                                user.getBanDuration(),
                                user.getBanExpiresAt());
                    }
                } else {
                    throw new UserBannedException(
                            "Your account is banned.",
                            user.getBanReason(),
                            user.getBanDuration(),
                            user.getBanExpiresAt());
                }
            }

            if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                user.setEmailVerified(true);
            }

            user.setSessionToken(UUID.randomUUID().toString());
            userRepository.save(user);
            return user;
        }

        String displayName = name.isBlank() ? email.split("@")[0] : name;
        String username = generateUsername(displayName, email);

        User newUser = User.builder()
                .name(displayName)
                .username(username)
                .email(email)
                .emailVerified(true)
                .pwd(passwordService.hashPassword(UUID.randomUUID().toString() + UUID.randomUUID()))
                .role(Role.ETUDIANT)
                .avatar(picture)
                .joinDate(LocalDate.now())
                .xp(0)
                .streak(0)
                .coins(0)
                .banned(false)
                .failedAttempts(0)
                .sessionToken(UUID.randomUUID().toString())
                .needsSetup(true)
                .build();

        return userRepository.save(newUser);
    }

    private JsonNode verifyToken(String idToken) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Invalid or expired Google token.");
            }

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(response.body());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify Google token: " + e.getMessage());
        }
    }

    private String generateUsername(String name, String email) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (base.length() < 3) {
            base = email.split("@")[0].toLowerCase().replaceAll("[^a-z0-9]", "");
        }
        base = base.length() > 12 ? base.substring(0, 12) : base;
        long suffix = System.currentTimeMillis() % 10000;
        return base + suffix;
    }
}
