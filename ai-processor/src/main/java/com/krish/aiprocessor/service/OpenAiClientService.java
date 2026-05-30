package com.krish.aiprocessor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.aiprocessor.config.OpenAiProperties;
import com.krish.aiprocessor.domain.enums.TicketCategory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
public class OpenAiClientService {

    private static final BigDecimal DEFAULT_CONFIDENCE_SCORE = new BigDecimal("0.5");

    private final WebClient webClient;

    private final OpenAiProperties openAiProperties;

    private final ObjectMapper objectMapper;

    public OpenAiClientService(
        @Qualifier("openAiWebClient") WebClient webClient,
        OpenAiProperties openAiProperties,
        ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
    }

    public record AiClassificationResult(
        TicketCategory category,
        BigDecimal confidenceScore,
        boolean shouldEscalate,
        String reasoning
    ) {
    }

    public AiClassificationResult classifyTicket(
        String title,
        String description,
        TicketCategory existingCategory
    ) {
        try {
            Map<String, Object> requestBody = buildRequestBody(
                title,
                description,
                existingCategory
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            String content = extractResponseContent(response);

            @SuppressWarnings("unchecked")
            Map<String, Object> classification = objectMapper.readValue(content, Map.class);

            return new AiClassificationResult(
                parseCategory(classification.get("category")),
                parseConfidenceScore(classification.get("confidenceScore")),
                parseShouldEscalate(classification.get("shouldEscalate")),
                parseReasoning(classification.get("reasoning"))
            );
        } catch (Exception exception) {
            log.error("Failed to classify ticket with OpenAI", exception);
            return defaultResult();
        }
    }

    private Map<String, Object> buildRequestBody(
        String title,
        String description,
        TicketCategory existingCategory
    ) {
        return Map.of(
            "model", openAiProperties.getModel(),
            "max_tokens", openAiProperties.getMaxTokens(),
            "temperature", openAiProperties.getTemperature(),
            "messages", List.of(
                Map.of(
                    "role", "system",
                    "content", buildSystemPrompt()
                ),
                Map.of(
                    "role", "user",
                    "content", buildUserPrompt(title, description, existingCategory)
                )
            )
        );
    }

    private String buildSystemPrompt() {
        return """
            You are a customer support ticket classifier.
            Analyze the ticket and respond with valid JSON only.
            No markdown, no explanation, just the JSON object.
            """;
    }

    private String buildUserPrompt(
        String title,
        String description,
        TicketCategory existingCategory
    ) {
        String existingCategoryValue = existingCategory != null ? existingCategory.name() : "NONE";

        return """
            Classify this support ticket:
            Title: %s
            Description: %s

            Respond with this exact JSON structure:
            {
              "category": "one of: BILLING, TECHNICAL, ACCOUNT, SHIPPING, GENERAL, COMPLAINT, FEATURE_REQUEST",
              "confidenceScore": 0.95,
              "shouldEscalate": false,
              "reasoning": "brief explanation"
            }

            Only suggest a category different from the existing one if you are very confident.
            Existing category: %s
            """.formatted(title, description, existingCategoryValue);
    }

    private String extractResponseContent(Map<String, Object> response) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> firstChoice = choices.get(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");

        return message.get("content").toString();
    }

    private TicketCategory parseCategory(Object category) {
        if (category == null) {
            return TicketCategory.GENERAL;
        }

        try {
            return TicketCategory.valueOf(category.toString());
        } catch (IllegalArgumentException exception) {
            return TicketCategory.GENERAL;
        }
    }

    private BigDecimal parseConfidenceScore(Object confidenceScore) {
        if (confidenceScore == null) {
            return DEFAULT_CONFIDENCE_SCORE;
        }

        if (confidenceScore instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }

        if (confidenceScore instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }

        try {
            return new BigDecimal(confidenceScore.toString());
        } catch (NumberFormatException exception) {
            return DEFAULT_CONFIDENCE_SCORE;
        }
    }

    private boolean parseShouldEscalate(Object shouldEscalate) {
        if (shouldEscalate instanceof Boolean booleanValue) {
            return booleanValue;
        }

        return Boolean.parseBoolean(String.valueOf(shouldEscalate));
    }

    private String parseReasoning(Object reasoning) {
        return reasoning != null ? reasoning.toString() : "";
    }

    private AiClassificationResult defaultResult() {
        return new AiClassificationResult(
            TicketCategory.GENERAL,
            DEFAULT_CONFIDENCE_SCORE,
            false,
            "Classification failed"
        );
    }
}
