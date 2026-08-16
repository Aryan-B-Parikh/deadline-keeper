package com.deadlinekeeper.integration;

import com.deadlinekeeper.config.GeminiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GeminiClient {

    private final Client client;
    private final String model;
    private final ObjectMapper objectMapper;

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
        this.client = Client.builder()
                .apiKey(config.getApiKey())
                .build();
        this.model = config.getModel();
        this.objectMapper = new ObjectMapper();
    }

    public JsonNode extractFromText(String text) {
        try {
            Content content = Content.builder()
                    .parts(List.of(Part.fromText(EXTRACTION_PROMPT + "\n\nInput text:\n" + text)))
                    .build();

            GenerateContentResponse response = client.models.generateContent(
                    model, content, GenerateContentConfig.builder().build());

            String responseText = response.text();
            return objectMapper.readTree(responseText);
        } catch (Exception e) {
            throw new RuntimeException("Gemini text extraction failed: " + e.getMessage(), e);
        }
    }

    public JsonNode extractFromImage(byte[] imageData, String mimeType) {
        try {
            Part textPart = Part.fromText(EXTRACTION_PROMPT);
            Part imagePart = Part.fromBytes(imageData, mimeType);

            Content content = Content.builder()
                    .parts(List.of(textPart, imagePart))
                    .build();

            GenerateContentResponse response = client.models.generateContent(
                    model, content, GenerateContentConfig.builder().build());

            String responseText = response.text();
            return objectMapper.readTree(responseText);
        } catch (Exception e) {
            throw new RuntimeException("Gemini image extraction failed: " + e.getMessage(), e);
        }
    }
}
