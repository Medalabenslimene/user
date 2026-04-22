package tn.esprit.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.user.entity.User;
import tn.esprit.user.repository.UserRepository;
import tn.esprit.user.services.EmailService;
import tn.esprit.user.services.EmailVerificationService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    // ── Helper ────────────────────────────────────────────────────────────────

    private User buildUnverifiedUser(String email, String code, LocalDateTime expiry) {
        User u = new User();
        u.setId(1L);
        u.setEmail(email);
        u.setEmailVerified(false);
        u.setVerificationCode(code);
        u.setVerificationCodeExpiry(expiry);
        return u;
    }

    // ── sendVerificationCode ──────────────────────────────────────────────────

    @Test
    void sendVerificationCode_savesCodeAndSendsEmail() {
        User u = buildUnverifiedUser("test@example.com", null, null);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendVerificationCode(anyString(), anyString());

        emailVerificationService.sendVerificationCode(u);

        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationCode(emailCaptor.capture(), codeCaptor.capture());

        assertEquals("test@example.com", emailCaptor.getValue());
        String sentCode = codeCaptor.getValue();
        assertNotNull(sentCode);
        assertEquals(6, sentCode.length(), "Verification code should be 6 digits");
        assertTrue(sentCode.matches("\\d{6}"), "Code should be numeric");

        // The code must be stored on the user
        assertEquals(sentCode, u.getVerificationCode());
        assertFalse(Boolean.TRUE.equals(u.getEmailVerified()));
        assertNotNull(u.getVerificationCodeExpiry());
    }

    // ── verifyCode ────────────────────────────────────────────────────────────

    @Test
    void verifyCode_userNotFound_throwsRuntimeException() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> emailVerificationService.verifyCode("ghost@example.com", "123456"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void verifyCode_alreadyVerified_throwsRuntimeException() {
        User u = buildUnverifiedUser("done@example.com", "111111", LocalDateTime.now().plusMinutes(5));
        u.setEmailVerified(true);
        when(userRepository.findByEmail("done@example.com")).thenReturn(Optional.of(u));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> emailVerificationService.verifyCode("done@example.com", "111111"));
        assertTrue(ex.getMessage().contains("already verified"));
    }

    @Test
    void verifyCode_invalidCode_throwsRuntimeException() {
        User u = buildUnverifiedUser("user@example.com", "123456", LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> emailVerificationService.verifyCode("user@example.com", "999999"));
        assertTrue(ex.getMessage().contains("Invalid verification code"));
    }

    @Test
    void verifyCode_expiredCode_throwsRuntimeException() {
        // Code is correct but expired 10 minutes ago
        User u = buildUnverifiedUser("user@example.com", "123456", LocalDateTime.now().minusMinutes(10));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> emailVerificationService.verifyCode("user@example.com", "123456"));
        assertTrue(ex.getMessage().contains("expired"));
    }

    @Test
    void verifyCode_validCode_setsEmailVerifiedAndClearsCode() {
        User u = buildUnverifiedUser("user@example.com", "654321", LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = emailVerificationService.verifyCode("user@example.com", "654321");

        assertTrue(Boolean.TRUE.equals(result.getEmailVerified()));
        assertNull(result.getVerificationCode(), "Code should be cleared after successful verification");
        assertNull(result.getVerificationCodeExpiry(), "Expiry should be cleared after successful verification");
    }

    // ── resendCode ────────────────────────────────────────────────────────────

    @Test
    void resendCode_userNotFound_throwsRuntimeException() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> emailVerificationService.resendCode("nobody@example.com"));
    }

    @Test
    void resendCode_alreadyVerified_throwsRuntimeException() {
        User u = buildUnverifiedUser("done@example.com", null, null);
        u.setEmailVerified(true);
        when(userRepository.findByEmail("done@example.com")).thenReturn(Optional.of(u));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> emailVerificationService.resendCode("done@example.com"));
        assertTrue(ex.getMessage().contains("already verified"));
    }

    @Test
    void resendCode_notVerified_sendsNewCodeAndSaves() {
        User u = buildUnverifiedUser("pending@example.com", "000000", LocalDateTime.now().plusMinutes(1));
        when(userRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendVerificationCode(anyString(), anyString());

        emailVerificationService.resendCode("pending@example.com");

        verify(emailService).sendVerificationCode(eq("pending@example.com"), anyString());
        verify(userRepository, atLeastOnce()).save(u);
    }
}
