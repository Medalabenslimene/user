package tn.esprit.user.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.User;
import tn.esprit.user.exception.UserBannedException;
import tn.esprit.user.repository.UserRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public User login(String email, String pwd) {
        Optional<User> user = userRepository.findByEmailAndPwd(email, pwd);
        if (user.isPresent()) {
            User u = user.get();
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
                            return u;
                        }
                    } catch (Exception e) {
                        // If date parsing fails, treat as still banned
                    }
                }
                // Still banned -> throw structured exception
                throw new UserBannedException(
                    "Your account is banned.",
                    u.getBanReason(),
                    u.getBanDuration(),
                    u.getBanExpiresAt()
                );
            }
            return u;
        } else {
            throw new RuntimeException("Invalid email or password");
        }
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
            existingUser.setPwd(updatedUser.getPwd());
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