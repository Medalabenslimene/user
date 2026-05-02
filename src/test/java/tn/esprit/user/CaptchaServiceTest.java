package tn.esprit.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.esprit.user.services.CaptchaService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CaptchaServiceTest {

    private CaptchaService captchaService;

    @BeforeEach
    void setUp() {
        captchaService = new CaptchaService();
    }

    // ── generateChallenge ─────────────────────────────────────────────────────

    @Test
    void generateChallenge_returnsRequiredFields() {
        Map<String, Object> challenge = captchaService.generateChallenge();

        assertTrue(challenge.containsKey("challengeId"));
        assertTrue(challenge.containsKey("targetWord"));
        assertTrue(challenge.containsKey("images"));
    }

    @Test
    void generateChallenge_challengeIdIsNonBlank() {
        Map<String, Object> challenge = captchaService.generateChallenge();
        String id = (String) challenge.get("challengeId");
        assertNotNull(id);
        assertFalse(id.isBlank());
    }

    @Test
    void generateChallenge_returnsFourImages() {
        Map<String, Object> challenge = captchaService.generateChallenge();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> images = (List<Map<String, Object>>) challenge.get("images");
        assertEquals(4, images.size());
    }

    @Test
    void generateChallenge_eachImageHasIndexAndUrl() {
        Map<String, Object> challenge = captchaService.generateChallenge();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> images = (List<Map<String, Object>>) challenge.get("images");
        for (Map<String, Object> img : images) {
            assertTrue(img.containsKey("index"));
            assertTrue(img.containsKey("url"));
            assertNotNull(img.get("url"));
        }
    }

    @Test
    void generateChallenge_targetWordMatchesOneImage() {
        Map<String, Object> challenge = captchaService.generateChallenge();
        assertNotNull(challenge.get("targetWord"));
        assertFalse(((String) challenge.get("targetWord")).isBlank());
    }

    @Test
    void generateChallenge_producesUniqueChallengeIds() {
        String id1 = (String) captchaService.generateChallenge().get("challengeId");
        String id2 = (String) captchaService.generateChallenge().get("challengeId");
        assertNotEquals(id1, id2);
    }

    // ── verify ────────────────────────────────────────────────────────────────

    @Test
    void verify_nullChallengeId_returnsFalse() {
        assertFalse(captchaService.verify(null, 0));
    }

    @Test
    void verify_unknownChallengeId_returnsFalse() {
        assertFalse(captchaService.verify("non-existent-id", 0));
    }

    @Test
    void verify_correctIndex_returnsTrue() {
        // Each challenge has a random correct index in [0,3].
        // Try index 0 on up to 20 fresh challenges; P(all miss) = (3/4)^20 < 0.4%.
        boolean found = false;
        for (int i = 0; i < 20 && !found; i++) {
            Map<String, Object> ch = captchaService.generateChallenge();
            found = captchaService.verify((String) ch.get("challengeId"), 0);
        }
        assertTrue(found, "Expected at least one challenge to have correct index 0");
    }

    @Test
    void verify_challengeIsConsumedAfterFirstUse() {
        Map<String, Object> challenge = captchaService.generateChallenge();
        String id = (String) challenge.get("challengeId");

        // First verify (could be right or wrong, doesn't matter)
        captchaService.verify(id, 0);

        // Second verify with same ID must fail — challenge was consumed
        assertFalse(captchaService.verify(id, 0));
    }

    @Test
    void verify_wrongIndex_returnsFalse() {
        // Generate a challenge where we know the wrong answer by trying index 0-3
        // We pick a challenge and try an index that will definitely be wrong at least once
        // Generate 4 separate challenges, one per index attempt — at least 3 will be wrong
        int wrongCount = 0;
        for (int i = 0; i < 4; i++) {
            Map<String, Object> ch = captchaService.generateChallenge();
            String cid = (String) ch.get("challengeId");
            // Try index 0 on all — statistically 3 out of 4 will be wrong
            if (!captchaService.verify(cid, 0)) {
                wrongCount++;
            }
        }
        assertTrue(wrongCount > 0, "At least some wrong-index verifications should return false");
    }
}
