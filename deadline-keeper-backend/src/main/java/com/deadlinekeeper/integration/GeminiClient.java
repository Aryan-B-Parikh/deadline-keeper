package com.deadlinekeeper.integration;

import com.deadlinekeeper.config.GeminiConfig;
import com.deadlinekeeper.exception.ExternalServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final String EXTRACTION_PROMPT = """
            You are a deadline extraction assistant. Today's date is %s and the current time is %s (server timezone: UTC).
            
            Analyze the provided input (text or image) and extract all deadline-related events.
            
            For each event found, extract:
            - title: The name of the event/deadline (string, required)
            - type: One of "exam", "submission", "hackathon", or "other" (string, required)
            - due_date: The due date in ISO format YYYY-MM-DD (string, required). If a year is missing,
              assume the nearest future occurrence relative to today.
            - due_time: The time in HH:mm format in the USER'S timezone (string, nullable if not specified)
            - timezone: The IANA timezone if mentioned (e.g., "America/New_York"), otherwise null
            - confidence: A float between 0.0 and 1.0 indicating how confident you are
            - needs_clarification: boolean, true if the date is ambiguous
            
            IMPORTANT: Resolve relative dates like "next Friday", "in 2 weeks" based on today's date.
            If a date is genuinely ambiguous, set needs_clarification to true.
            Ignore non-deadline information.
            
            Respond ONLY with valid JSON matching this exact schema:
            {
              "events": [
                {
                  "title": "string",
                  "type": "exam|submission|hackathon|other",
                  "due_date": "YYYY-MM-DD",
                  "due_time": "HH:mm" or null,
                  "timezone": "IANA timezone" or null,
                  "confidence": 0.0-1.0,
                  "needs_clarification": true/false
                }
              ],
              "clarification_needed": "optional question" or null
            }
            """.formatted(
            LocalDate.now().toString(),
            LocalTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    );

    public GeminiClient(GeminiConfig config) {
        this.apiKey = config.getApiKey();
        this.model = config.getModel();
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    public JsonNode extractFromText(String text) {
        return callGemini(EXTRACTION_PROMPT + "\n\nInput text:\n" + text);
    }

    public JsonNode extractFromImage(byte[] imageData, String mimeType) {
        String base64Image = java.util.Base64.getEncoder().encodeToString(imageData);

        Map<String, Object> textPart = Map.of("text", EXTRACTION_PROMPT);
        Map<String, Object> imagePart = Map.of(
                "inline_data", Map.of("mime_type", mimeType, "data", base64Image));
        Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
        Map<String, Object> body = Map.of("contents", List.of(content));

        return callGeminiApi(body);
    }

    private JsonNode callGemini(String prompt) {
        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> body = Map.of("contents", List.of(content));
        return callGeminiApi(body);
    }

    private JsonNode callGeminiApi(Map<String, Object> body) {
        try {
            String url = BASE_URL + model + ":generateContent?key=" + apiKey;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return parseResponse(response.getBody());
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException("Gemini", "Extraction failed: " + e.getMessage(), e);
        }
    }

    private JsonNode parseResponse(String responseBody) {
        try {
            if (responseBody == null || responseBody.isBlank()) {
                throw new ExternalServiceException("Gemini", "Empty response from Gemini API");
            }

            JsonNode root = objectMapper.readTree(responseBody);

            if (!root.has("candidates") || root.get("candidates").isEmpty()) {
                throw new ExternalServiceException("Gemini", "No candidates in response");
            }

            JsonNode candidate = root.get("candidates").get(0);

            if (candidate.has("finishReason") && "SAFETY".equals(candidate.get("finishReason").asText())) {
                throw new ExternalServiceException("Gemini", "Response blocked by safety filter");
            }

            String text = candidate.path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                throw new ExternalServiceException("Gemini", "Empty response text from Gemini");
            }

            // Strip markdown code blocks
            text = text.trim();
            if (text.startsWith("```json")) {
                text = text.substring(7);
            } else if (text.startsWith("```")) {
                text = text.substring(3);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();

            JsonNode parsed = objectMapper.readTree(text);

            // Validate required structure
            if (!parsed.has("events") || !parsed.get("events").isArray()) {
                throw new ExternalServiceException("Gemini", "Response missing 'events' array");
            }

            // Validate each event has required fields
            for (JsonNode eventNode : parsed.get("events")) {
                if (!eventNode.has("title") || eventNode.get("title").isNull()) {
                    log.warn("Gemini returned event without title, skipping validation for that event");
                }
                if (!eventNode.has("due_date") || eventNode.get("due_date").isNull()) {
                    log.warn("Gemini returned event without due_date");
                }
            }

            return parsed;
        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalServiceException("Gemini", "Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }
}
