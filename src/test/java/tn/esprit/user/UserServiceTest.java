package tn.esprit.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tn.esprit.user.entity.Role;
import tn.esprit.user.entity.User;
import tn.esprit.user.exception.AccountLockedException;
import tn.esprit.user.exception.UserBannedException;
import tn.esprit.user.repository.UserRepository;
import tn.esprit.user.services.EmailService;
import tn.esprit.user.services.EmailVerificationService;
import tn.esprit.user.services.PasswordService;
import tn.esprit.user.services.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordService passwordService;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void injectValues() {
        ReflectionTestUtils.setField(userService, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(userService, "facesUploadDir", "/tmp/faces");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private User buildUser(Long id, String email, String hashedPwd) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setPwd(hashedPwd);
        u.setName("Test User");
        u.setRole(Role.ETUDIANT);
        u.setFailedAttempts(0);
        u.setBanned(false);
        return u;
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_userNotFound_throwsRuntimeException() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login("nobody@example.com", "pass"));
        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    void login_accountLocked_throwsAccountLockedException() {
        User u = buildUser(1L, "locked@example.com", "$2a$10$hashedpassword123456789012345678901234567890123456789");
        u.setLockedUntil(LocalDateTime.now().plusMinutes(3));
        u.setFailedAttempts(3);
        when(userRepository.findByEmail("locked@example.com")).thenReturn(Optional.of(u));

        assertThrows(AccountLockedException.class,
                () -> userService.login("locked@example.com", "wrong"));
    }

    @Test
    void login_permanentBan_throwsUserBannedException() {
        User u = buildUser(2L, "banned@example.com", "$2a$10$hashedpassword123456789012345678901234567890123456789");
        u.setBanned(true);
        u.setBanReason("Violating terms");
        u.setBanDuration("permanent");
        u.setBanExpiresAt(null);
        when(userRepository.findByEmail("banned@example.com")).thenReturn(Optional.of(u));

        assertThrows(UserBannedException.class,
                () -> userService.login("banned@example.com", "any"));
    }

    @Test
    void login_wrongPassword_firstAttempt_throwsRuntimeExceptionWithRemainingCount() {
        User u = buildUser(3L, "user@example.com", "$2a$10$hashedpassword123456789012345678901234567890123456789");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));
        when(passwordService.isHashedPassword(anyString())).thenReturn(true);
        when(passwordService.verifyPassword("wrong", u.getPwd())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(u);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.login("user@example.com", "wrong"));

        assertTrue(ex.getMessage().contains("2 attempts remaining"));
        assertEquals(1, u.getFailedAttempts());
    }

    @Test
    void login_wrongPassword_thirdAttempt_locksAccountAndThrowsAccountLockedException() {
        User u = buildUser(4L, "user@example.com", "$2a$10$hashedpassword123456789012345678901234567890123456789");
        u.setFailedAttempts(2); // already 2 failed attempts
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));
        when(passwordService.isHashedPassword(anyString())).thenReturn(true);
        when(passwordService.verifyPassword("wrong", u.getPwd())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(u);

        assertThrows(AccountLockedException.class,
                () -> userService.login("user@example.com", "wrong"));

        assertNotNull(u.getLockedUntil(), "Account should be locked after 3 failed attempts");
        verify(emailService).sendAccountLockedEmail(u.getEmail(), u.getName());
    }

    @Test
    void login_successfulLogin_resetsAttemptsAndReturnsUserWithSessionToken() {
        User u = buildUser(5L, "good@example.com", "$2a$10$hashedpassword123456789012345678901234567890123456789");
        u.setFailedAttempts(1);
        when(userRepository.findByEmail("good@example.com")).thenReturn(Optional.of(u));
        when(passwordService.isHashedPassword(anyString())).thenReturn(true);
        when(passwordService.verifyPassword("correctPass", u.getPwd())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.login("good@example.com", "correctPass");

        assertNotNull(result);
        assertEquals(0, result.getFailedAttempts());
        assertNotNull(result.getSessionToken());
    }

    @Test
    void login_expiredLock_autoUnlocksBeforePasswordCheck() {
        User u = buildUser(6L, "expired@example.com", "$2a$10$hashedpassword123456789012345678901234567890123456789");
        u.setLockedUntil(LocalDateTime.now().minusMinutes(1)); // lock expired
        u.setFailedAttempts(3);
        when(userRepository.findByEmail("expired@example.com")).thenReturn(Optional.of(u));
        when(passwordService.isHashedPassword(anyString())).thenReturn(true);
        when(passwordService.verifyPassword("pass", u.getPwd())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Should NOT throw AccountLockedException — lock expired, auto-unlock happens
        User result = userService.login("expired@example.com", "pass");
        assertNull(result.getLockedUntil());
        assertEquals(0, result.getFailedAttempts());
    }

    // ── createUser ────────────────────────────────────────────────────────────

    @Test
    void createUser_duplicateEmail_throwsRuntimeException() {
        User existing = buildUser(1L, "exists@example.com", "hash");
        when(userRepository.findByEmail("exists@example.com")).thenReturn(Optional.of(existing));

        User newUser = new User();
        newUser.setEmail("exists@example.com");
        newUser.setPwd("plainPwd");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.createUser(newUser));
        assertTrue(ex.getMessage().contains("email already exists"));
    }

    @Test
    void createUser_newUser_hashesPasswordAndSaves() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordService.hashPassword("plain123")).thenReturn("$2a$10$fakeHashedValue000000000000000000000000000000000000000");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(99L);
            return u;
        });
        doNothing().when(emailVerificationService).sendVerificationCode(any(User.class));

        User newUser = new User();
        newUser.setEmail("new@example.com");
        newUser.setPwd("plain123");

        User result = userService.createUser(newUser);

        assertNotNull(result.getId());
        assertFalse(Boolean.TRUE.equals(result.getEmailVerified()));
        assertEquals("$2a$10$fakeHashedValue000000000000000000000000000000000000000", result.getPwd());
        verify(emailVerificationService).sendVerificationCode(result);
    }

    // ── deleteUser ────────────────────────────────────────────────────────────

    @Test
    void deleteUser_userExists_returnsTrue() {
        when(userRepository.existsById(10L)).thenReturn(true);
        doNothing().when(userRepository).deleteById(10L);

        assertTrue(userService.deleteUser(10L));
        verify(userRepository).deleteById(10L);
    }

    @Test
    void deleteUser_userNotFound_returnsFalse() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertFalse(userService.deleteUser(99L));
        verify(userRepository, never()).deleteById(anyLong());
    }

    // ── getAllUsers / getUserById ──────────────────────────────────────────────

    @Test
    void getAllUsers_returnsListFromRepository() {
        List<User> users = List.of(buildUser(1L, "a@a.com", "h"), buildUser(2L, "b@b.com", "h"));
        when(userRepository.findAll()).thenReturn(users);

        List<User> result = userService.getAllUsers();
        assertEquals(2, result.size());
    }

    @Test
    void getUserById_existingId_returnsOptionalWithUser() {
        User u = buildUser(7L, "find@example.com", "hash");
        when(userRepository.findById(7L)).thenReturn(Optional.of(u));

        Optional<User> result = userService.getUserById(7L);
        assertTrue(result.isPresent());
        assertEquals("find@example.com", result.get().getEmail());
    }

    @Test
    void getUserById_missingId_returnsEmptyOptional() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertTrue(userService.getUserById(999L).isEmpty());
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    void changePassword_userNotFound_throwsRuntimeException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> userService.changePassword(1L, "current", "newPass"));
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsRuntimeException() {
        User u = buildUser(2L, "u@u.com", "$2a$10$hashedpassword123456789012345678901234567890123456789");
        when(userRepository.findById(2L)).thenReturn(Optional.of(u));
        when(passwordService.isHashedPassword(anyString())).thenReturn(true);
        when(passwordService.verifyPassword("wrong", u.getPwd())).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.changePassword(2L, "wrong", "newPass"));
        assertTrue(ex.getMessage().contains("incorrect"));
    }

    @Test
    void changePassword_newPasswordSameAsCurrent_throwsRuntimeException() {
        User u = buildUser(3L, "u@u.com", "$2a$10$hashedpassword123456789012345678901234567890123456789");
        when(userRepository.findById(3L)).thenReturn(Optional.of(u));
        when(passwordService.isHashedPassword(anyString())).thenReturn(true);
        when(passwordService.verifyPassword("samePass", u.getPwd())).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.changePassword(3L, "samePass", "samePass"));
        assertTrue(ex.getMessage().contains("different"));
    }

    @Test
    void changePassword_success_savesNewHashedPassword() {
        User u = buildUser(4L, "u@u.com", "$2a$10$hashedpassword123456789012345678901234567890123456789");
        when(userRepository.findById(4L)).thenReturn(Optional.of(u));
        when(passwordService.isHashedPassword(anyString())).thenReturn(true);
        when(passwordService.verifyPassword("oldPass", u.getPwd())).thenReturn(true);
        when(passwordService.hashPassword("newPass")).thenReturn("$2a$10$newHashedPassword0000000000000000000000000000000000000");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.changePassword(4L, "oldPass", "newPass");

        assertEquals("$2a$10$newHashedPassword0000000000000000000000000000000000000", u.getPwd());
        verify(userRepository).save(u);
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void updateProfile_existingUser_updatesNameAndUsername() {
        User u = buildUser(5L, "p@p.com", "hash");
        u.setName("Old Name");
        u.setUsername("oldUser");
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<User> result = userService.updateProfile(5L, "New Name", "newUser");

        assertTrue(result.isPresent());
        assertEquals("New Name", result.get().getName());
        assertEquals("newUser", result.get().getUsername());
    }
}
