package com.deadlinekeeper.integration;

import com.deadlinekeeper.config.GeminiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class GeminiClient {

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final String EXTRACTION_PROMPT = """
            You are a deadline extraction assistant. Analyze the provided input (text or image)
            and extract all deadline-related events.

            For each event found, extract:
            - title: The name of the event/deadline (string)
            - type: One of "exam", "submission", "hackathon", or "other" (string)
            - due_date: The due date in ISO format YYYY-MM-DD (string). If a year is missing,
              assume the nearest future occurrence relative to today: %s
            - due_time: The time in HH:mm format (string, nullable if not specified)
            - timezone: The timezone if mentioned, otherwise null
            - confidence: A float between 0.0 and 1.0 indicating how confident you are
              in this extraction
            - needs_clarification: boolean, true if the date is ambiguous (e.g., "next Monday"
              could be this or next week) or critical information is missing

            Rules:
            - If the input contains multiple deadlines, extract each as a separate event.
            - Resolve relative dates like "next Friday", "in 2 weeks" based on today's date.
            - If a date is genuinely ambiguous, set needs_clarification to true.
            - Ignore non-deadline information (marketing text, contact info, etc.).
            - Always provide a confidence score.

            Respond ONLY with valid JSON in this exact format:
            {
              "events": [
                {
                  "title": "...",
                  "type": "...",
                  "due_date": "YYYY-MM-DD",
                  "due_time": "HH:mm" or null,
                  "timezone": "..." or null,
                  "confidence": 0.0-1.0,
                  "needs_clarification": true/false
                }
              ],
              "clarification_needed": "optional question if any event needs clarification, otherwise null"
            }
            """.formatted(java.time.LocalDate.now().toString());

    public GeminiClient(GeminiConfig config) {
        this.apiKey = config.getApiKey();
        this.model = config.getModel();
        this.objectMapper = new ObjectMapper();
        this.restTemplate = new RestTemplate();
    }

    public JsonNode extractFromText(String text) {
        try {
            String url = BASE_URL + model + ":generateContent?key=" + apiKey;

            Map<String, Object> part = Map.of("text", EXTRACTION_PROMPT + "\n\nInput text:\n" + text);
            Map<String, Object> content = Map.of("parts", List.of(part));
            Map<String, Object> body = Map.of("contents", List.of(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return parseResponse(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Gemini text extraction failed: " + e.getMessage(), e);
        }
    }

    public JsonNode extractFromImage(byte[] imageData, String mimeType) {
        try {
            String url = BASE_URL + model + ":generateContent?key=" + apiKey;

            String base64Image = java.util.Base64.getEncoder().encodeToString(imageData);

            Map<String, Object> textPart = Map.of("text", EXTRACTION_PROMPT);
            Map<String, Object> imagePart = Map.of(
                    "inline_data", Map.of(
                            "mime_type", mimeType,
                            "data", base64Image
                    )
            );
            Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
            Map<String, Object> body = Map.of("contents", List.of(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return parseResponse(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Gemini image extraction failed: " + e.getMessage(), e);
        }
    }

    private JsonNode parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // Extract the text from Gemini's response structure
        String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();

        // The text might contain markdown code blocks, strip them
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

        return objectMapper.readTree(text);
    }
}
