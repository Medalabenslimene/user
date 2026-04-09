package tn.esprit.user.services;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service that proxies face recognition requests to the Python Flask
 * microservice running on port 5001.
 */
@Service
public class FaceRecognitionService {

    private static final String FACE_SERVICE_URL = "http://127.0.0.1:5001";

    private final RestTemplate restTemplate;

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<Map<String, Object>>() {};

    public FaceRecognitionService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Register a user's face from a base64-encoded photo.
     */
    public Map<String, Object> registerFace(Long userId, String base64Image) {
        String url = FACE_SERVICE_URL + "/face/register/" + userId;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("image", base64Image), headers
        );

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, MAP_TYPE
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            return parseErrorResponse(e);
        } catch (Exception e) {
            return Map.of("error", "Face service unavailable: " + e.getMessage());
        }
    }

    /**
     * Verify a live photo against the registered face for a user.
     */
    public Map<String, Object> verifyFace(Long userId, String base64Image) {
        String url = FACE_SERVICE_URL + "/face/verify/" + userId;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(
                Map.of("image", base64Image), headers
        );

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, MAP_TYPE
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            return parseErrorResponse(e);
        } catch (Exception e) {
            return Map.of("error", "Face service unavailable: " + e.getMessage(), "verified", false);
        }
    }

    /**
     * Check if a user has a registered face.
     */
    public Map<String, Object> getFaceStatus(Long userId) {
        String url = FACE_SERVICE_URL + "/face/status/" + userId;

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, MAP_TYPE
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            return parseErrorResponse(e);
        } catch (Exception e) {
            return Map.of("error", "Face service unavailable: " + e.getMessage());
        }
    }

    /**
     * Delete stored face data for a user.
     */
    public Map<String, Object> deleteFace(Long userId) {
        String url = FACE_SERVICE_URL + "/face/delete/" + userId;

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.DELETE, null, MAP_TYPE
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            return parseErrorResponse(e);
        } catch (Exception e) {
            return Map.of("error", "Face service unavailable: " + e.getMessage());
        }
    }

    /**
     * Check if the face recognition microservice is running.
     */
    public boolean isServiceHealthy() {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    FACE_SERVICE_URL + "/health", HttpMethod.GET, null, MAP_TYPE
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseErrorResponse(HttpClientErrorException e) {
        try {
            String body = e.getResponseBodyAsString();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(body, Map.class);
        } catch (Exception ex) {
            return Map.of("error", e.getStatusText());
        }
    }
}
