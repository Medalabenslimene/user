package tn.esprit.user;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.user.entity.LoginLog;
import tn.esprit.user.entity.User;
import tn.esprit.user.repository.LoginLogRepository;
import tn.esprit.user.services.LoginLogService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginLogServiceTest {

    @Mock
    private LoginLogRepository loginLogRepository;

    @InjectMocks
    private LoginLogService loginLogService;

    // ── record ────────────────────────────────────────────────────────────────

    @Test
    void record_withValidUserAndRequest_savesLoginLog() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        when(loginLogRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        loginLogService.record(user, "PASSWORD", true, request);

        LoginLog saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals("user@example.com", saved.getEmail());
        assertEquals("PASSWORD", saved.getLoginMethod());
        assertTrue(saved.getSuccess());
        assertEquals("127.0.0.1", saved.getIpAddress());
        assertEquals("Mozilla/5.0", saved.getUserAgent());
        assertNotNull(saved.getLoginTime());
    }

    @Test
    void record_withXForwardedFor_usesFirstIp() {
        User user = new User();
        user.setId(2L);
        user.setEmail("user@example.com");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        when(loginLogRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        loginLogService.record(user, "GOOGLE", true, request);

        assertEquals("10.0.0.1", captor.getValue().getIpAddress());
    }

    @Test
    void record_withNullRequest_savesLogWithNullIpAndAgent() {
        User user = new User();
        user.setId(3L);
        user.setEmail("user@example.com");

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        when(loginLogRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        loginLogService.record(user, "FACE", false, null);

        LoginLog saved = captor.getValue();
        assertNull(saved.getIpAddress());
        assertNull(saved.getUserAgent());
        assertFalse(saved.getSuccess());
    }

    @Test
    void record_withNullUser_savesLogWithNullUserFields() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Agent");

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        when(loginLogRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        loginLogService.record(null, "PASSWORD", false, request);

        LoginLog saved = captor.getValue();
        assertNull(saved.getUserId());
        assertNull(saved.getEmail());
    }

    @Test
    void record_userAgentOver500Chars_isTruncatedTo500() {
        User user = new User();
        user.setId(4L);
        user.setEmail("user@example.com");

        String longAgent = "A".repeat(600);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn(longAgent);

        ArgumentCaptor<LoginLog> captor = ArgumentCaptor.forClass(LoginLog.class);
        when(loginLogRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        loginLogService.record(user, "PASSWORD", true, request);

        assertEquals(500, captor.getValue().getUserAgent().length());
    }

    @Test
    void record_repositoryThrows_doesNotPropagateException() {
        User user = new User();
        user.setId(5L);
        user.setEmail("user@example.com");

        when(loginLogRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() -> loginLogService.record(user, "PASSWORD", true, null));
    }

    // ── findAll / findByUser ──────────────────────────────────────────────────

    @Test
    void findAll_returnsLogsFromRepository() {
        List<LoginLog> logs = List.of(new LoginLog(), new LoginLog());
        when(loginLogRepository.findAllByOrderByLoginTimeDesc()).thenReturn(logs);

        assertEquals(2, loginLogService.findAll().size());
    }

    @Test
    void findByUser_returnsLogsForGivenUserId() {
        List<LoginLog> logs = List.of(new LoginLog());
        when(loginLogRepository.findByUserIdOrderByLoginTimeDesc(7L)).thenReturn(logs);

        assertEquals(1, loginLogService.findByUser(7L).size());
    }
}
