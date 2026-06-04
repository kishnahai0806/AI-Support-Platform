package com.krish.aiprocessor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.aiprocessor.config.OpenAiProperties;
import com.krish.aiprocessor.domain.entity.AiResponseAudit;
import com.krish.aiprocessor.domain.enums.TicketCategory;
import com.krish.aiprocessor.event.TicketCreatedEvent;
import com.krish.aiprocessor.event.TicketProcessedEvent;
import com.krish.aiprocessor.repository.AiResponseAuditRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
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

    private static final String AI_PROCESSING_TOTAL_METRIC = "ai.processing.total";

    private static final String AI_PROCESSING_DURATION_METRIC = "ai.processing.duration";

    private static final String AI_CONFIDENCE_SCORE_METRIC = "ai.confidence.score";

    private static final String SUCCESS_TAG = "success";

    private static final String MODEL_TAG = "model";

    private final OpenAiClientService openAiClientService;

    private final AiResponseAuditRepository aiResponseAuditRepository;

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper;

    private final OpenAiProperties openAiProperties;

    private final String ticketProcessedTopic;

    private final MeterRegistry meterRegistry;

    public AiProcessingService(
        OpenAiClientService openAiClientService,
        AiResponseAuditRepository aiResponseAuditRepository,
        KafkaTemplate<String, String> kafkaTemplate,
        ObjectMapper objectMapper,
        OpenAiProperties openAiProperties,
        @Value("${spring.kafka.topics.ticket-processed}") String ticketProcessedTopic,
        MeterRegistry meterRegistry
    ) {
        this.openAiClientService = openAiClientService;
        this.aiResponseAuditRepository = aiResponseAuditRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
        this.ticketProcessedTopic = ticketProcessedTopic;
        this.meterRegistry = meterRegistry;
    }

    public void processTicket(TicketCreatedEvent event) {
        Sample sample = Timer.start(meterRegistry);
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

            saveAudit(
                event.getTicketId(),
                latencyMs,
                success,
                errorMessage,
                result.promptTokens(),
                result.completionTokens(),
                result.totalTokens()
            );

            recordProcessingMetrics(success, result);

            publishProcessedEvent(event, result, latencyMs, success, errorMessage);

            log.info(
                "Ticket {} processed by AI: category={}, confidence={}, escalate={}, latency={}ms",
                event.getTicketId(),
                result.category(),
                result.confidenceScore(),
                result.shouldEscalate(),
                latencyMs
            );
        } catch (RuntimeException exception) {
            log.error("Failed to process ticket {} due to infrastructure failure",
                event.getTicketId(), exception);
            throw exception;
        } finally {
            sample.stop(Timer.builder(AI_PROCESSING_DURATION_METRIC)
                .register(meterRegistry));
        }
    }

    private boolean isSuccessful(OpenAiClientService.AiClassificationResult result) {
        return result.category() != TicketCategory.GENERAL
            || result.confidenceScore().compareTo(DEFAULT_CONFIDENCE_SCORE) > 0;
    }

    private void recordProcessingMetrics(
        boolean success,
        OpenAiClientService.AiClassificationResult result
    ) {
        meterRegistry.counter(
            AI_PROCESSING_TOTAL_METRIC,
            SUCCESS_TAG,
            Boolean.toString(success),
            MODEL_TAG,
            openAiProperties.getModel()
        ).increment();

        if (success) {
            meterRegistry.summary(AI_CONFIDENCE_SCORE_METRIC)
                .record(result.confidenceScore().doubleValue());
        }
    }

    private void saveAudit(
        UUID ticketId,
        long latencyMs,
        boolean success,
        String errorMessage,
        int promptTokens,
        int completionTokens,
        int totalTokens
    ) {
        AiResponseAudit audit = AiResponseAudit.builder()
            .ticketId(ticketId)
            .promptTokens(promptTokens)
            .completionTokens(completionTokens)
            .totalTokens(totalTokens)
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
            .promptTokens(result.promptTokens())
            .completionTokens(result.completionTokens())
            .totalTokens(result.totalTokens())
            .latencyMs(latencyMs)
            .success(success)
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
            ).join();
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize ticket.processed event {}", processedEvent.getEventId(), exception);
            throw new KafkaPublishException(
                "Failed to serialize ticket.processed event " + processedEvent.getEventId(), exception);
        } catch (Exception exception) {
            log.error("Failed to publish ticket.processed event {}", processedEvent.getEventId(), exception);
            throw new KafkaPublishException(
                "Failed to publish ticket.processed event " + processedEvent.getEventId(), exception);
        }
    }
}
