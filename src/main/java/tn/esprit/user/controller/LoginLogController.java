package tn.esprit.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.entity.LoginLog;
import tn.esprit.user.services.LoginLogService;

import java.util.List;

@RestController
@RequestMapping("/api/users/login-logs")
public class LoginLogController {

    @Autowired
    private LoginLogService loginLogService;

    @GetMapping
    public ResponseEntity<List<LoginLog>> getAll() {
        return ResponseEntity.ok(loginLogService.findAll());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<LoginLog>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(loginLogService.findByUser(userId));
    }
}
