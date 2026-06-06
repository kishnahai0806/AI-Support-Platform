package com.krish.aiprocessor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.aiprocessor.config.OpenAiProperties;
import com.krish.aiprocessor.domain.entity.AiResponseAudit;
import com.krish.aiprocessor.domain.enums.TicketCategory;
import com.krish.aiprocessor.domain.enums.TicketPriority;
import com.krish.aiprocessor.event.TicketCreatedEvent;
import com.krish.aiprocessor.event.TicketProcessedEvent;
import com.krish.aiprocessor.repository.AiResponseAuditRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AiProcessingServiceTest {

    private static final String TICKET_PROCESSED_TOPIC = "ticket.processed";

    @Mock
    private OpenAiClientService openAiClientService;

    @Mock
    private AiResponseAuditRepository aiResponseAuditRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OpenAiProperties openAiProperties;

    private AiProcessingService aiProcessingService;

    private SimpleMeterRegistry meterRegistry;

    private TicketCreatedEvent sampleEvent;

    private OpenAiClientService.AiClassificationResult successResult;

    private OpenAiClientService.AiClassificationResult defaultResult;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        aiProcessingService = new AiProcessingService(
            openAiClientService,
            aiResponseAuditRepository,
            kafkaTemplate,
            objectMapper,
            openAiProperties,
            TICKET_PROCESSED_TOPIC,
            meterRegistry
        );

        UUID ticketId = UUID.randomUUID();

        sampleEvent = TicketCreatedEvent.builder()
            .eventId(UUID.randomUUID())
            .ticketId(ticketId)
            .ticketNumber("TKT-000001")
            .title("Application error")
            .description("The dashboard fails to load")
            .category(TicketCategory.TECHNICAL)
            .priority(TicketPriority.MEDIUM)
            .customerId(UUID.randomUUID())
            .customerEmail("customer@example.com")
            .createdAt(LocalDateTime.now())
            .build();

        successResult = new OpenAiClientService.AiClassificationResult(
            TicketCategory.TECHNICAL,
            new BigDecimal("0.9500"),
            false,
            "Clear technical issue",
            100, 50, 150
        );

        defaultResult = new OpenAiClientService.AiClassificationResult(
            TicketCategory.GENERAL,
            new BigDecimal("0.5000"),
            false,
            "Classification failed",
            0, 0, 0
        );

        Mockito.when(openAiProperties.getModel()).thenReturn("gpt-4o-mini");
    }

    @Test
    void processTicket_successfulClassification_savesAuditAndPublishes() throws Exception {
        Mockito.when(openAiClientService.classifyTicket(
            sampleEvent.getTitle(),
            sampleEvent.getDescription(),
            sampleEvent.getCategory()
        )).thenReturn(successResult);
        Mockito.when(objectMapper.writeValueAsString(ArgumentMatchers.any(TicketProcessedEvent.class)))
            .thenReturn("{}");
        Mockito.when(kafkaTemplate.send(
            ArgumentMatchers.eq(TICKET_PROCESSED_TOPIC),
            ArgumentMatchers.eq(sampleEvent.getTicketId().toString()),
            ArgumentMatchers.anyString()
        )).thenReturn(CompletableFuture.completedFuture(null));

        aiProcessingService.processTicket(sampleEvent);

        ArgumentCaptor<AiResponseAudit> auditCaptor = ArgumentCaptor.forClass(AiResponseAudit.class);
        Mockito.verify(aiResponseAuditRepository, Mockito.times(1)).save(auditCaptor.capture());
        Mockito.verify(kafkaTemplate, Mockito.times(1)).send(
            ArgumentMatchers.eq(TICKET_PROCESSED_TOPIC),
            ArgumentMatchers.eq(sampleEvent.getTicketId().toString()),
            ArgumentMatchers.anyString()
        );
        assertThat(auditCaptor.getValue().isSuccess()).isTrue();
        assertThat(auditCaptor.getValue().getPromptTokens()).isEqualTo(100);
        assertThat(auditCaptor.getValue().getCompletionTokens()).isEqualTo(50);
        assertThat(auditCaptor.getValue().getTotalTokens()).isEqualTo(150);
    }

    @Test
    void processTicket_defaultFallbackResult_savesFailedAudit() throws Exception {
        Mockito.when(openAiClientService.classifyTicket(
            sampleEvent.getTitle(),
            sampleEvent.getDescription(),
            sampleEvent.getCategory()
        )).thenReturn(defaultResult);
        Mockito.when(objectMapper.writeValueAsString(ArgumentMatchers.any(TicketProcessedEvent.class)))
            .thenReturn("{}");
        Mockito.when(kafkaTemplate.send(
            ArgumentMatchers.eq(TICKET_PROCESSED_TOPIC),
            ArgumentMatchers.eq(sampleEvent.getTicketId().toString()),
            ArgumentMatchers.anyString()
        )).thenReturn(CompletableFuture.completedFuture(null));

        aiProcessingService.processTicket(sampleEvent);

        ArgumentCaptor<AiResponseAudit> auditCaptor = ArgumentCaptor.forClass(AiResponseAudit.class);
        Mockito.verify(aiResponseAuditRepository, Mockito.times(1)).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().isSuccess()).isFalse();
    }

    @Test
    void processTicket_dbSaveFails_throwsException() {
        Mockito.when(openAiClientService.classifyTicket(
            sampleEvent.getTitle(),
            sampleEvent.getDescription(),
            sampleEvent.getCategory()
        )).thenReturn(successResult);
        Mockito.when(aiResponseAuditRepository.save(ArgumentMatchers.any(AiResponseAudit.class)))
            .thenThrow(new IllegalStateException("DB connection lost"));

        assertThatThrownBy(() -> aiProcessingService.processTicket(sampleEvent))
            .isInstanceOf(RuntimeException.class);

        Mockito.verify(aiResponseAuditRepository, Mockito.times(1))
            .save(ArgumentMatchers.any(AiResponseAudit.class));
        Mockito.verify(kafkaTemplate, Mockito.never()).send(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString()
        );
    }

    @Test
    void processTicket_kafkaPublishFails_auditStillSavedAndRethrows() throws Exception {
        Mockito.when(openAiClientService.classifyTicket(
            sampleEvent.getTitle(),
            sampleEvent.getDescription(),
            sampleEvent.getCategory()
        )).thenReturn(successResult);
        Mockito.when(objectMapper.writeValueAsString(ArgumentMatchers.any(TicketProcessedEvent.class)))
            .thenThrow(new JsonProcessingException("Serialization failed") {
            });

        assertThatThrownBy(() -> aiProcessingService.processTicket(sampleEvent))
            .isInstanceOf(KafkaPublishException.class);

        Mockito.verify(aiResponseAuditRepository, Mockito.times(1))
            .save(ArgumentMatchers.any(AiResponseAudit.class));
    }

    @Test
    void processTicket_escalatedResult_publishesEscalatedEvent() throws Exception {
        OpenAiClientService.AiClassificationResult escalatedResult =
            new OpenAiClientService.AiClassificationResult(
                TicketCategory.BILLING,
                new BigDecimal("0.9500"),
                true,
                "Billing issue needs escalation",
                120, 60, 180
            );

        Mockito.when(openAiClientService.classifyTicket(
            sampleEvent.getTitle(),
            sampleEvent.getDescription(),
            sampleEvent.getCategory()
        )).thenReturn(escalatedResult);
        Mockito.when(objectMapper.writeValueAsString(ArgumentMatchers.any(TicketProcessedEvent.class)))
            .thenReturn("{}");
        Mockito.when(kafkaTemplate.send(
            ArgumentMatchers.eq(TICKET_PROCESSED_TOPIC),
            ArgumentMatchers.eq(sampleEvent.getTicketId().toString()),
            ArgumentMatchers.anyString()
        )).thenReturn(CompletableFuture.completedFuture(null));

        aiProcessingService.processTicket(sampleEvent);

        ArgumentCaptor<TicketProcessedEvent> eventCaptor =
            ArgumentCaptor.forClass(TicketProcessedEvent.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(objectMapper, Mockito.times(1)).writeValueAsString(eventCaptor.capture());
        Mockito.verify(kafkaTemplate, Mockito.times(1)).send(
            ArgumentMatchers.eq(TICKET_PROCESSED_TOPIC),
            ArgumentMatchers.eq(sampleEvent.getTicketId().toString()),
            payloadCaptor.capture()
        );
        assertThat(eventCaptor.getValue().isAiEscalated()).isTrue();
        assertThat(eventCaptor.getValue().getSuggestedCategory()).isEqualTo(TicketCategory.BILLING);
        assertThat(eventCaptor.getValue().getPromptTokens()).isEqualTo(120);
        assertThat(payloadCaptor.getValue()).isEqualTo("{}");
    }
}
