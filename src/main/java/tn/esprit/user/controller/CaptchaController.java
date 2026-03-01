package tn.esprit.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.user.services.CaptchaService;

import java.util.Map;

@RestController
@RequestMapping("/api/users/captcha")
public class CaptchaController {

    @Autowired
    private CaptchaService captchaService;

    @GetMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateChallenge() {
        return ResponseEntity.ok(captchaService.generateChallenge());
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestBody Map<String, Object> request) {
        String challengeId = (String) request.get("challengeId");
        int selectedIndex = ((Number) request.get("selectedIndex")).intValue();
        boolean valid = captchaService.verify(challengeId, selectedIndex);
        return ResponseEntity.ok(Map.of("valid", valid));
    }
}
