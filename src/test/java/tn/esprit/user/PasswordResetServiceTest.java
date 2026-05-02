package tn.esprit.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.user.entity.PasswordResetToken;
import tn.esprit.user.entity.User;
import tn.esprit.user.repository.PasswordResetTokenRepository;
import tn.esprit.user.repository.UserRepository;
import tn.esprit.user.services.EmailService;
import tn.esprit.user.services.PasswordResetService;
import tn.esprit.user.services.PasswordService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordService passwordService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    // ── requestPasswordReset ──────────────────────────────────────────────────

    @Test
    void requestPasswordReset_userNotFound_throwsRuntimeException() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> passwordResetService.requestPasswordReset("nobody@example.com"));
    }

    @Test
    void requestPasswordReset_validEmail_deletesOldTokenAndSendsEmail() {
        User user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        doNothing().when(tokenRepository).deleteByEmail("user@example.com");
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendPasswordResetEmail(anyString(), anyString());

        passwordResetService.requestPasswordReset("user@example.com");

        verify(tokenRepository).deleteByEmail("user@example.com");

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(emailCaptor.capture(), tokenCaptor.capture());

        assertEquals("user@example.com", emailCaptor.getValue());
        assertNotNull(tokenCaptor.getValue());
        assertFalse(tokenCaptor.getValue().isBlank());
    }

    @Test
    void requestPasswordReset_savesTokenWithCorrectFields() {
        User user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        doNothing().when(tokenRepository).deleteByEmail(anyString());
        doNothing().when(emailService).sendPasswordResetEmail(anyString(), anyString());

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        when(tokenRepository.save(tokenCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        passwordResetService.requestPasswordReset("user@example.com");

        PasswordResetToken saved = tokenCaptor.getValue();
        assertEquals("user@example.com", saved.getEmail());
        assertFalse(saved.isUsed());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    // ── resetPassword ─────────────────────────────────────────────────────────

    @Test
    void resetPassword_tokenNotFound_throwsRuntimeException() {
        when(tokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> passwordResetService.resetPassword("bad-token", "newPass"));
    }

    @Test
    void resetPassword_tokenAlreadyUsed_throwsRuntimeException() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("used-token")
                .email("user@example.com")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(true)
                .build();
        when(tokenRepository.findByToken("used-token")).thenReturn(Optional.of(token));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> passwordResetService.resetPassword("used-token", "newPass"));
        assertTrue(ex.getMessage().contains("already been used"));
    }

    @Test
    void resetPassword_tokenExpired_throwsRuntimeException() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("expired-token")
                .email("user@example.com")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .used(false)
                .build();
        when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> passwordResetService.resetPassword("expired-token", "newPass"));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void resetPassword_validToken_hashesAndSavesNewPassword() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("valid-token")
                .email("user@example.com")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(false)
                .build();
        User user = new User();
        user.setEmail("user@example.com");
        user.setPwd("oldHash");

        when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordService.hashPassword("newPass123")).thenReturn("$2a$10$newHash0000000000000000000000000000000000000000000000");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        passwordResetService.resetPassword("valid-token", "newPass123");

        assertEquals("$2a$10$newHash0000000000000000000000000000000000000000000000", user.getPwd());
        assertTrue(token.isUsed());
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    void resetPassword_userNotFound_throwsRuntimeException() {
        PasswordResetToken token = PasswordResetToken.builder()
                .token("orphan-token")
                .email("deleted@example.com")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(false)
                .build();
        when(tokenRepository.findByToken("orphan-token")).thenReturn(Optional.of(token));
        when(userRepository.findByEmail("deleted@example.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> passwordResetService.resetPassword("orphan-token", "newPass"));
    }
}
