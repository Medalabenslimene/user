package tn.esprit.user.services;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {

    private static final long CHALLENGE_TTL_SECONDS = 300; // 5 minutes

    // In-memory store: challengeId -> ChallengeData
    private final Map<String, ChallengeData> challenges = new ConcurrentHashMap<>();

    // Image categories: each category has a label and multiple image URLs
    private static final List<Category> CATEGORIES = List.of(
        new Category("Cat", List.of(
            "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1573865526739-10659fec78a5?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1495360010541-f48722b34f7d?w=200&h=200&fit=crop"
        )),
        new Category("Dog", List.of(
            "https://images.unsplash.com/photo-1587300003388-59208cc962cb?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1561037404-61cd46aa615b?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1517849845537-4d257902454a?w=200&h=200&fit=crop"
        )),
        new Category("Car", List.of(
            "https://images.unsplash.com/photo-1494976388531-d1058494cdd8?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1502877338535-766e1452684a?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1583121274602-3e2820c69888?w=200&h=200&fit=crop"
        )),
        new Category("Flower", List.of(
            "https://images.unsplash.com/photo-1490750967868-88aa4f44baee?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1462275646964-a0e3c11f18a6?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1487530811176-3780de880c2d?w=200&h=200&fit=crop"
        )),
        new Category("Mountain", List.of(
            "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1454496522488-7a8e488e8606?w=200&h=200&fit=crop"
        )),
        new Category("Beach", List.of(
            "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1519046904884-53103b34b206?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1473116763249-2faaef81ccda?w=200&h=200&fit=crop"
        )),
        new Category("Pizza", List.of(
            "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1574071318508-1cdbab80d002?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=200&h=200&fit=crop"
        )),
        new Category("Guitar", List.of(
            "https://images.unsplash.com/photo-1510915361894-db8b60106cb1?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1525201548942-d8732f6617a0?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1564186763535-ebb21ef5277f?w=200&h=200&fit=crop"
        )),
        new Category("Book", List.of(
            "https://images.unsplash.com/photo-1512820790803-83ca734da794?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?w=200&h=200&fit=crop"
        )),
        new Category("Bicycle", List.of(
            "https://images.unsplash.com/photo-1485965120184-e220f721d03e?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1532298229144-0ec0c57515c7?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=200&h=200&fit=crop"
        )),
        new Category("Bird", List.of(
            "https://images.unsplash.com/photo-1444464666168-49d633b86797?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1452570053594-1b985d6ea890?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1522926193341-e9ffd686c60f?w=200&h=200&fit=crop"
        )),
        new Category("Airplane", List.of(
            "https://images.unsplash.com/photo-1436491865332-7a61a109db05?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1474302770737-173ee21bab63?w=200&h=200&fit=crop",
            "https://images.unsplash.com/photo-1556388158-158ea5ccacbd?w=200&h=200&fit=crop"
        ))
    );

    private final Random random = new Random();

    /**
     * Generate a new CAPTCHA challenge with 4 images (one correct).
     */
    public Map<String, Object> generateChallenge() {
        cleanupExpired();

        // Pick 4 distinct random categories
        List<Category> shuffled = new ArrayList<>(CATEGORIES);
        Collections.shuffle(shuffled, random);
        List<Category> selected = shuffled.subList(0, 4);

        // Pick a random correct index (0-3)
        int correctIndex = random.nextInt(4);
        String targetWord = selected.get(correctIndex).label;

        // Build image list with one random image per category
        List<Map<String, Object>> images = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Category cat = selected.get(i);
            String imageUrl = cat.images.get(random.nextInt(cat.images.size()));
            images.add(Map.of(
                "index", i,
                "url", imageUrl
            ));
        }

        // Store challenge
        String challengeId = UUID.randomUUID().toString();
        challenges.put(challengeId, new ChallengeData(correctIndex, Instant.now().plusSeconds(CHALLENGE_TTL_SECONDS)));

        return Map.of(
            "challengeId", challengeId,
            "targetWord", targetWord,
            "images", images
        );
    }

    /**
     * Verify a CAPTCHA challenge. Returns true if the selected index is correct.
     * The challenge is consumed (one-time use).
     */
    public boolean verify(String challengeId, int selectedIndex) {
        if (challengeId == null) return false;
        ChallengeData data = challenges.remove(challengeId);
        if (data == null) return false;
        if (data.expiresAt.isBefore(Instant.now())) return false;
        return data.correctIndex == selectedIndex;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        challenges.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
    }

    // --- Inner classes ---

    private static class Category {
        final String label;
        final List<String> images;

        Category(String label, List<String> images) {
            this.label = label;
            this.images = images;
        }
    }

    private static class ChallengeData {
        final int correctIndex;
        final Instant expiresAt;

        ChallengeData(int correctIndex, Instant expiresAt) {
            this.correctIndex = correctIndex;
            this.expiresAt = expiresAt;
        }
    }
}
