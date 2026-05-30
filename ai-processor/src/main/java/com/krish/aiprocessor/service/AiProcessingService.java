package com.krish.aiprocessor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.aiprocessor.config.OpenAiProperties;
import com.krish.aiprocessor.domain.entity.AiResponseAudit;
import com.krish.aiprocessor.domain.enums.TicketCategory;
import com.krish.aiprocessor.event.TicketCreatedEvent;
import com.krish.aiprocessor.event.TicketProcessedEvent;
import com.krish.aiprocessor.repository.AiResponseAuditRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class AiProcessingService {

    private static final BigDecimal DEFAULT_CONFIDENCE_SCORE = new BigDecimal("0.5");

    private static final String DEFAULT_FAILURE_MESSAGE = "AI classification failed or returned default";

    private final OpenAiClientService openAiClientService;

    private final AiResponseAuditRepository aiResponseAuditRepository;

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper;

    private final OpenAiProperties openAiProperties;

    @Value("${spring.kafka.topics.ticket-processed}")
    private String ticketProcessedTopic;

    public AiProcessingService(
        OpenAiClientService openAiClientService,
        AiResponseAuditRepository aiResponseAuditRepository,
        KafkaTemplate<String, String> kafkaTemplate,
        ObjectMapper objectMapper,
        OpenAiProperties openAiProperties
    ) {
        this.openAiClientService = openAiClientService;
        this.aiResponseAuditRepository = aiResponseAuditRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
    }

    public void processTicket(TicketCreatedEvent event) {
        long startTime = System.currentTimeMillis();

        try {
            OpenAiClientService.AiClassificationResult result =
                openAiClientService.classifyTicket(
                    event.getTitle(),
                    event.getDescription(),
                    event.getCategory()
                );
            long latencyMs = System.currentTimeMillis() - startTime;
            boolean success = isSuccessful(result);
            String errorMessage = success ? null : DEFAULT_FAILURE_MESSAGE;

            saveAudit(event.getTicketId(), latencyMs, success, errorMessage);
            publishProcessedEvent(event, result, latencyMs, success, errorMessage);

            log.info(
                "Ticket {} processed by AI: category={}, confidence={}, escalate={}, latency={}ms",
                event.getTicketId(),
                result.category(),
                result.confidenceScore(),
                result.shouldEscalate(),
                latencyMs
            );
        } catch (Exception exception) {
            long latencyMs = System.currentTimeMillis() - startTime;
            String errorMessage = exception.getMessage();
            log.error("Failed to process ticket {} with AI", event.getTicketId(), exception);

            saveAudit(event.getTicketId(), latencyMs, false, errorMessage);
            publishFailedEvent(event, latencyMs, errorMessage);
        }
    }

    private boolean isSuccessful(OpenAiClientService.AiClassificationResult result) {
        return result.category() != TicketCategory.GENERAL
            || result.confidenceScore().compareTo(DEFAULT_CONFIDENCE_SCORE) > 0;
    }

    private void saveAudit(
        UUID ticketId,
        long latencyMs,
        boolean success,
        String errorMessage
    ) {
        AiResponseAudit audit = AiResponseAudit.builder()
            .ticketId(ticketId)
            .promptTokens(0)
            .completionTokens(0)
            .totalTokens(0)
            .modelName(openAiProperties.getModel())
            .latencyMs(latencyMs)
            .success(success)
            .errorMessage(errorMessage)
            .build();

        aiResponseAuditRepository.save(audit);
    }

    private void publishProcessedEvent(
        TicketCreatedEvent event,
        OpenAiClientService.AiClassificationResult result,
        long latencyMs,
        boolean success,
        String errorMessage
    ) {
        TicketProcessedEvent processedEvent = TicketProcessedEvent.builder()
            .eventId(UUID.randomUUID())
            .ticketId(event.getTicketId())
            .suggestedCategory(result.category())
            .confidenceScore(result.confidenceScore())
            .aiEscalated(result.shouldEscalate())
            .modelUsed(openAiProperties.getModel())
            .promptTokens(0)
            .completionTokens(0)
            .totalTokens(0)
            .latencyMs(latencyMs)
            .success(success)
            .errorMessage(errorMessage)
            .processedAt(LocalDateTime.now())
            .build();

        publish(processedEvent);
    }

    private void publishFailedEvent(
        TicketCreatedEvent event,
        long latencyMs,
        String errorMessage
    ) {
        TicketProcessedEvent processedEvent = TicketProcessedEvent.builder()
            .eventId(UUID.randomUUID())
            .ticketId(event.getTicketId())
            .suggestedCategory(TicketCategory.GENERAL)
            .confidenceScore(DEFAULT_CONFIDENCE_SCORE)
            .aiEscalated(false)
            .modelUsed(openAiProperties.getModel())
            .promptTokens(0)
            .completionTokens(0)
            .totalTokens(0)
            .latencyMs(latencyMs)
            .success(false)
            .errorMessage(errorMessage)
            .processedAt(LocalDateTime.now())
            .build();

        publish(processedEvent);
    }

    private void publish(TicketProcessedEvent processedEvent) {
        try {
            String payload = objectMapper.writeValueAsString(processedEvent);
            kafkaTemplate.send(
                ticketProcessedTopic,
                processedEvent.getTicketId().toString(),
                payload
            );
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize ticket.processed event {}", processedEvent.getEventId(), exception);
        } catch (Exception exception) {
            log.error("Failed to publish ticket.processed event {}", processedEvent.getEventId(), exception);
        }
    }
}
