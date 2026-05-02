package tn.esprit.user.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.user.entity.User;
import tn.esprit.user.entity.Role;
import tn.esprit.user.exception.AccountLockedException;
import tn.esprit.user.exception.UserBannedException;
import tn.esprit.user.repository.UserRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int LOCK_DURATION_MINUTES = 5;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private LoginRateLimiterService loginRateLimiterService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.faces.upload.dir:/var/www/faces}")
    private String facesUploadDir;

    public User login(String email, String pwd) {
        if (loginRateLimiterService.isBlocked(email)) {
            long secondsLeft = loginRateLimiterService.getTimeToUnlock(email);
            LocalDateTime unlockTime = LocalDateTime.now().plusSeconds(secondsLeft);
            throw new AccountLockedException(
                    "Too many failed attempts. Try again in " + ((secondsLeft / 60) + 1) + " minutes.",
                    unlockTime, MAX_FAILED_ATTEMPTS);
        }

        // 1. Find user by email only
        Optional<User> optUser = userRepository.findByEmail(email);
        if (optUser.isEmpty()) {
            loginRateLimiterService.recordFailedAttempt(email);
            throw new RuntimeException("Invalid email or password");
        }

        User u = optUser.get();

        // 2. Check if account is locked
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException(
                    "Account locked for 5 minutes due to too many failed attempts.",
                    u.getLockedUntil(),
                    u.getFailedAttempts() != null ? u.getFailedAttempts() : MAX_FAILED_ATTEMPTS);
        }

        // Auto-unlock if lock period has expired
        if (u.getLockedUntil() != null && u.getLockedUntil().isBefore(LocalDateTime.now())) {
            u.setLockedUntil(null);
            u.setFailedAttempts(0);
            userRepository.save(u);
        }

        // 3. Check if user is banned
        if (Boolean.TRUE.equals(u.getBanned())) {
            // Check if temporary ban has expired -> auto-unban
            if (u.getBanExpiresAt() != null && !u.getBanExpiresAt().isEmpty()) {
                try {
                    Instant expiresAt = Instant.parse(u.getBanExpiresAt());
                    if (expiresAt.isBefore(Instant.now())) {
                        u.setBanned(false);
                        u.setBanReason(null);
                        u.setBanDuration(null);
                        u.setBanExpiresAt(null);
                        userRepository.save(u);
                        // Continue to password check below
                    } else {
                        throw new UserBannedException(
                                "Your account is banned.",
                                u.getBanReason(),
                                u.getBanDuration(),
                                u.getBanExpiresAt());
                    }
                } catch (UserBannedException e) {
                    throw e;
                } catch (Exception e) {
                    // If date parsing fails, treat as still banned
                    throw new UserBannedException(
                            "Your account is banned.",
                            u.getBanReason(),
                            u.getBanDuration(),
                            u.getBanExpiresAt());
                }
            } else {
                // Permanent ban
                throw new UserBannedException(
                        "Your account is banned.",
                        u.getBanReason(),
                        u.getBanDuration(),
                        u.getBanExpiresAt());
            }
        }

        // 4. Verify password using BCrypt
        boolean passwordMatches;
        // Check if password is hashed or plain text (for migration)
        if (passwordService.isHashedPassword(u.getPwd())) {
            // Already hashed - use BCrypt verification
            passwordMatches = passwordService.verifyPassword(pwd, u.getPwd());
        } else {
            // Plain text - verify directly and migrate to hashed
            passwordMatches = u.getPwd().equals(pwd);
            if (passwordMatches) {
                // Migrate to hashed password on successful login
                String hashedPassword = passwordService.hashPassword(pwd);
                u.setPwd(hashedPassword);
            }
        }

        if (!passwordMatches) {
            int attempts = (u.getFailedAttempts() != null ? u.getFailedAttempts() : 0) + 1;
            u.setFailedAttempts(attempts);
            loginRateLimiterService.recordFailedAttempt(email);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                // Lock the account for 5 minutes
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
                u.setLockedUntil(lockUntil);
                userRepository.save(u);

                // Send security email (non-blocking)
                try {
                    emailService.sendAccountLockedEmail(u.getEmail(), u.getName());
                } catch (Exception e) {
                    // Don't fail login flow if email fails
                }

                throw new AccountLockedException(
                        "Account locked for 5 minutes due to too many failed attempts.",
                        lockUntil,
                        attempts);
            } else {
                userRepository.save(u);
                int remaining = MAX_FAILED_ATTEMPTS - attempts;
                throw new RuntimeException(
                        "Invalid credentials. " + remaining + " attempt" + (remaining > 1 ? "s" : "") + " remaining.");
            }
        }

        // 5. Successful login — reset failed attempts and issue a new session token
        if (u.getFailedAttempts() != null && u.getFailedAttempts() > 0) {
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
        }
        loginRateLimiterService.resetAttempts(email);
        u.setSessionToken(UUID.randomUUID().toString());
        userRepository.save(u);
        return u;
    }

    public User createUser(User user) {
        if (user.getEmail() != null && userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new RuntimeException("An account with this email already exists.");
        }
        user.setEmailVerified(false);
        String hashedPassword = passwordService.hashPassword(user.getPwd());
        user.setPwd(hashedPassword);
        User savedUser = userRepository.save(user);
        try {
            emailVerificationService.sendVerificationCode(savedUser);
        } catch (Exception e) {
            // Don't block signup on transient SMTP failures — user can use "resend code"
        }
        return savedUser;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> updateUser(Long id, User updatedUser) {
        return userRepository.findById(id).map(existingUser -> {
            existingUser.setName(updatedUser.getName());
            existingUser.setUsername(updatedUser.getUsername());
            existingUser.setEmail(updatedUser.getEmail());
            existingUser.setEmailVerified(updatedUser.getEmailVerified());
            existingUser.setVerificationCode(updatedUser.getVerificationCode());
            existingUser.setVerificationCodeExpiry(updatedUser.getVerificationCodeExpiry());
            // Password is NOT updated here — use changePassword() instead
            existingUser.setNumTel(updatedUser.getNumTel());
            existingUser.setDateNaiss(updatedUser.getDateNaiss());
            existingUser.setRole(updatedUser.getRole());
            existingUser.setInscriptionOk(updatedUser.isInscriptionOk());
            existingUser.setPosterForum(updatedUser.isPosterForum());
            existingUser.setAvatar(updatedUser.getAvatar());
            existingUser.setCIN(updatedUser.getCIN());
            existingUser.setYearsOfExperience(updatedUser.getYearsOfExperience());
            existingUser.setSpecialization(updatedUser.getSpecialization());
            existingUser.setDepartement(updatedUser.getDepartement());
            existingUser.setAdminCIN(updatedUser.getAdminCIN());
            existingUser.setLevel(updatedUser.getLevel());
            existingUser.setXp(updatedUser.getXp());
            existingUser.setStreak(updatedUser.getStreak());
            existingUser.setCoins(updatedUser.getCoins());
            existingUser.setLanguage(updatedUser.getLanguage());
            existingUser.setJoinDate(updatedUser.getJoinDate());
            existingUser.setBio(updatedUser.getBio());
            existingUser.setBanned(updatedUser.getBanned());
            existingUser.setBanReason(updatedUser.getBanReason());
            existingUser.setBanDuration(updatedUser.getBanDuration());
            existingUser.setBanExpiresAt(updatedUser.getBanExpiresAt());
            existingUser.setFaceRegistered(updatedUser.getFaceRegistered());
            existingUser.setFaceImageUrl(updatedUser.getFaceImageUrl());
            return userRepository.save(existingUser);
        });
    }

    @Transactional
    public Optional<User> banUser(Long id, String reason, String duration, String banExpiresAt) {
        Optional<User> optUser = userRepository.findById(id);
        if (optUser.isEmpty()) {
            return Optional.empty();
        }
        User user = optUser.get();
        String expiresAt = (banExpiresAt != null && !banExpiresAt.isEmpty()) ? banExpiresAt : null;
        userRepository.banUserById(id, reason.trim(), duration, expiresAt);
        return userRepository.findById(id);
    }

    @Transactional
    public Optional<User> unbanUser(Long id) {
        Optional<User> optUser = userRepository.findById(id);
        if (optUser.isEmpty()) {
            return Optional.empty();
        }
        User user = optUser.get();
        userRepository.unbanUserById(id);
        return userRepository.findById(id);
    }

    public Optional<User> updateProfile(Long id, String name, String username) {
        return userRepository.findById(id).map(user -> {
            if (name != null)
                user.setName(name);
            if (username != null)
                user.setUsername(username);
            return userRepository.save(user);
        });
    }

    public String saveAvatar(Long id, MultipartFile file) throws IOException {
        Optional<User> optUser = userRepository.findById(id);
        String uploadDir = "uploads/avatars";
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "avatar.jpg";
        String safeFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileName = id + "_" + System.currentTimeMillis() + "_" + safeFilename;
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String avatarUrl = frontendUrl + "/api/users/avatars/" + fileName;
        User user = optUser.get();
        user.setAvatar(avatarUrl);
        userRepository.save(user);
        return avatarUrl;
    }

    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @Transactional
    public void changePassword(Long id, String currentPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found."));

        // Verify current password using BCrypt
        boolean currentPasswordMatches;
        if (passwordService.isHashedPassword(user.getPwd())) {
            currentPasswordMatches = passwordService.verifyPassword(currentPassword, user.getPwd());
        } else {
            // Plain text password - verify and migrate
            currentPasswordMatches = user.getPwd().equals(currentPassword);
            if (currentPasswordMatches) {
                // Migrate to hashed password
                String hashedPassword = passwordService.hashPassword(currentPassword);
                user.setPwd(hashedPassword);
            }
        }

        if (!currentPasswordMatches) {
            throw new RuntimeException("Current password is incorrect.");
        }

        if (newPassword.equals(currentPassword)) {
            throw new RuntimeException("New password must be different from current password.");
        }

        // Hash the new password
        String hashedNewPassword = passwordService.hashPassword(newPassword);
        user.setPwd(hashedNewPassword);
        userRepository.save(user);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Decode a base64 face image and persist it to the faces directory.
     * The file is named {userId}.jpg — re-registering overwrites the old image.
     * Returns the public URL of the saved image.
     */
    public String saveFaceImage(Long userId, String base64Image) throws IOException {
        // Strip the data-URI header if present (e.g. "data:image/jpeg;base64,")
        String data = base64Image.contains(",")
                ? base64Image.split(",", 2)[1]
                : base64Image;

        byte[] imageBytes = Base64.getDecoder().decode(data);

        Path uploadPath = Paths.get(facesUploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileName = userId + ".jpg";
        Path filePath = uploadPath.resolve(fileName);
        Files.write(filePath, imageBytes);

        String imageUrl = frontendUrl + "/api/users/faces/" + fileName;
        userRepository.findById(userId).ifPresent(user -> {
            user.setFaceImageUrl(imageUrl);
            userRepository.save(user);
        });
        return imageUrl;
    }

    @Transactional
    public void setFaceRegistered(Long userId, boolean registered) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFaceRegistered(registered);
            userRepository.save(user);
        });
    }

    @Transactional
    public void clearFaceImageUrl(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFaceImageUrl(null);
            userRepository.save(user);
        });
    }

    public void deleteFaceImageFile(Long userId) {
        try {
            Path filePath = Paths.get(facesUploadDir).resolve(userId + ".jpg");
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Non-fatal — embedding deletion already succeeded
        }
    }

    /**
     * Complete a face login after the Python service has already identified the user.
     * Only performs ban/lock checks — no face verification (already done by the caller).
     */
    public User faceLoginById(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found."));

        // Check if account is locked
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException(
                    "Account locked for 5 minutes due to too many failed attempts.",
                    u.getLockedUntil(),
                    u.getFailedAttempts() != null ? u.getFailedAttempts() : MAX_FAILED_ATTEMPTS);
        }

        // Auto-unlock if lock period has expired
        if (u.getLockedUntil() != null && u.getLockedUntil().isBefore(LocalDateTime.now())) {
            u.setLockedUntil(null);
            u.setFailedAttempts(0);
            userRepository.save(u);
        }

        // Check if user is banned
        if (Boolean.TRUE.equals(u.getBanned())) {
            if (u.getBanExpiresAt() != null && !u.getBanExpiresAt().isEmpty()) {
                try {
                    Instant expiresAt = Instant.parse(u.getBanExpiresAt());
                    if (expiresAt.isBefore(Instant.now())) {
                        u.setBanned(false);
                        u.setBanReason(null);
                        u.setBanDuration(null);
                        u.setBanExpiresAt(null);
                        userRepository.save(u);
                    } else {
                        throw new UserBannedException("Your account is banned.",
                                u.getBanReason(), u.getBanDuration(), u.getBanExpiresAt());
                    }
                } catch (UserBannedException e) {
                    throw e;
                } catch (Exception e) {
                    throw new UserBannedException("Your account is banned.",
                            u.getBanReason(), u.getBanDuration(), u.getBanExpiresAt());
                }
            } else {
                throw new UserBannedException("Your account is banned.",
                        u.getBanReason(), u.getBanDuration(), u.getBanExpiresAt());
            }
        }

        // Reset failed attempts on successful face login and issue a new session token
        if (u.getFailedAttempts() != null && u.getFailedAttempts() > 0) {
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
        }
        u.setSessionToken(UUID.randomUUID().toString());
        userRepository.save(u);
        return u;
    }

    /**
     * Login using face recognition instead of password.
     * Performs the same ban/lock checks as password login.
     */
    public User faceLogin(String email, String base64Image, FaceRecognitionService faceService) {
        // 1. Find user by email
        Optional<User> optUser = userRepository.findByEmail(email);
        if (optUser.isEmpty()) {
            throw new RuntimeException("Invalid email or face not recognized.");
        }

        User u = optUser.get();

        // 2. Check if account is locked
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException(
                    "Account locked for 5 minutes due to too many failed attempts.",
                    u.getLockedUntil(),
                    u.getFailedAttempts() != null ? u.getFailedAttempts() : MAX_FAILED_ATTEMPTS);
        }

        // Auto-unlock if lock period has expired
        if (u.getLockedUntil() != null && u.getLockedUntil().isBefore(LocalDateTime.now())) {
            u.setLockedUntil(null);
            u.setFailedAttempts(0);
            userRepository.save(u);
        }

        // 3. Check if user is banned
        if (Boolean.TRUE.equals(u.getBanned())) {
            if (u.getBanExpiresAt() != null && !u.getBanExpiresAt().isEmpty()) {
                try {
                    Instant expiresAt = Instant.parse(u.getBanExpiresAt());
                    if (expiresAt.isBefore(Instant.now())) {
                        u.setBanned(false);
                        u.setBanReason(null);
                        u.setBanDuration(null);
                        u.setBanExpiresAt(null);
                        userRepository.save(u);
                    } else {
                        throw new UserBannedException(
                                "Your account is banned.",
                                u.getBanReason(),
                                u.getBanDuration(),
                                u.getBanExpiresAt());
                    }
                } catch (UserBannedException e) {
                    throw e;
                } catch (Exception e) {
                    throw new UserBannedException(
                            "Your account is banned.",
                            u.getBanReason(),
                            u.getBanDuration(),
                            u.getBanExpiresAt());
                }
            } else {
                throw new UserBannedException(
                        "Your account is banned.",
                        u.getBanReason(),
                        u.getBanDuration(),
                        u.getBanExpiresAt());
            }
        }

        // 4. Check if user has a registered face
        if (!Boolean.TRUE.equals(u.getFaceRegistered())) {
            throw new RuntimeException("Face recognition is not set up for this account. Please use password login.");
        }

        // 5. Verify face against stored embedding
        Map<String, Object> result = faceService.verifyFace(u.getId(), base64Image);

        if (result.containsKey("error")) {
            String error = result.get("error").toString();
            // Count as a failed attempt if face not detected or service error
            int attempts = (u.getFailedAttempts() != null ? u.getFailedAttempts() : 0) + 1;
            u.setFailedAttempts(attempts);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
                u.setLockedUntil(lockUntil);
                userRepository.save(u);
                try {
                    emailService.sendAccountLockedEmail(u.getEmail(), u.getName());
                } catch (Exception ignored) {}
                throw new AccountLockedException(
                        "Account locked for 5 minutes due to too many failed attempts.",
                        lockUntil, attempts);
            }
            userRepository.save(u);
            throw new RuntimeException("Face verification failed: " + error);
        }

        Boolean verified = (Boolean) result.get("verified");
        if (!Boolean.TRUE.equals(verified)) {
            // Face did not match — count as failed attempt
            int attempts = (u.getFailedAttempts() != null ? u.getFailedAttempts() : 0) + 1;
            u.setFailedAttempts(attempts);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
                u.setLockedUntil(lockUntil);
                userRepository.save(u);
                try {
                    emailService.sendAccountLockedEmail(u.getEmail(), u.getName());
                } catch (Exception ignored) {}
                throw new AccountLockedException(
                        "Account locked for 5 minutes due to too many failed attempts.",
                        lockUntil, attempts);
            }
            userRepository.save(u);
            int remaining = MAX_FAILED_ATTEMPTS - attempts;
            throw new RuntimeException(
                    "Face not recognized. " + remaining + " attempt" + (remaining > 1 ? "s" : "") + " remaining.");
        }

        // 6. Successful face login — reset failed attempts and issue a new session token
        if (u.getFailedAttempts() != null && u.getFailedAttempts() > 0) {
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
        }
        u.setSessionToken(UUID.randomUUID().toString());
        userRepository.save(u);
        return u;
    }
}