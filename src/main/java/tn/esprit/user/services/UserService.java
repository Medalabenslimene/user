package tn.esprit.user.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.User;
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
import java.util.List;
import java.util.Optional;

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

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public User login(String email, String pwd) {
        // 1. Find user by email only
        Optional<User> optUser = userRepository.findByEmail(email);
        if (optUser.isEmpty()) {
            throw new RuntimeException("Invalid email or password");
        }

        User u = optUser.get();

        // 2. Check if account is locked
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException(
                "Account locked for 5 minutes due to too many failed attempts.",
                u.getLockedUntil(),
                u.getFailedAttempts() != null ? u.getFailedAttempts() : MAX_FAILED_ATTEMPTS
            );
        }

        // Auto-unlock if lock period has expired
        if (u.getLockedUntil() != null && u.getLockedUntil().isBefore(LocalDateTime.now())) {
            u.setLockedUntil(null);
            u.setFailedAttempts(0);
            userRepository.saveAndFlush(u);
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
                        userRepository.saveAndFlush(u);
                        // Continue to password check below
                    } else {
                        throw new UserBannedException(
                            "Your account is banned.",
                            u.getBanReason(),
                            u.getBanDuration(),
                            u.getBanExpiresAt()
                        );
                    }
                } catch (UserBannedException e) {
                    throw e;
                } catch (Exception e) {
                    // If date parsing fails, treat as still banned
                    throw new UserBannedException(
                        "Your account is banned.",
                        u.getBanReason(),
                        u.getBanDuration(),
                        u.getBanExpiresAt()
                    );
                }
            } else {
                // Permanent ban
                throw new UserBannedException(
                    "Your account is banned.",
                    u.getBanReason(),
                    u.getBanDuration(),
                    u.getBanExpiresAt()
                );
            }
        }

        // 4. Verify password
        if (!u.getPwd().equals(pwd)) {
            int attempts = (u.getFailedAttempts() != null ? u.getFailedAttempts() : 0) + 1;
            u.setFailedAttempts(attempts);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                // Lock the account for 5 minutes
                LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
                u.setLockedUntil(lockUntil);
                userRepository.saveAndFlush(u);

                // Send security email (non-blocking)
                try {
                    emailService.sendAccountLockedEmail(u.getEmail(), u.getName());
                } catch (Exception e) {
                    // Don't fail login flow if email fails
                }

                throw new AccountLockedException(
                    "Account locked for 5 minutes due to too many failed attempts.",
                    lockUntil,
                    attempts
                );
            } else {
                userRepository.saveAndFlush(u);
                int remaining = MAX_FAILED_ATTEMPTS - attempts;
                throw new RuntimeException(
                    "Invalid credentials. " + remaining + " attempt" + (remaining > 1 ? "s" : "") + " remaining."
                );
            }
        }

        // 5. Successful login — reset failed attempts
        if (u.getFailedAttempts() != null && u.getFailedAttempts() > 0) {
            u.setFailedAttempts(0);
            u.setLockedUntil(null);
        }
        userRepository.saveAndFlush(u)
        return u;
    }

    public User createUser(User user) {
        user.setEmailVerified(false);
        User savedUser = userRepository.save(user);
        emailVerificationService.sendVerificationCode(savedUser);
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

    // Verify current password
    if (!user.getPwd().equals(currentPassword)) {
        throw new RuntimeException("Current password is incorrect.");
    }

    if (newPassword.equals(currentPassword)) {
        throw new RuntimeException("New password must be different from current password.");
    }

    user.setPwd(newPassword);
    userRepository.save(user);
}
}