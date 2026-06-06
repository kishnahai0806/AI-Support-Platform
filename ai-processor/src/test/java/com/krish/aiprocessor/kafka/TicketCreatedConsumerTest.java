package com.krish.aiprocessor.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.aiprocessor.domain.enums.TicketCategory;
import com.krish.aiprocessor.domain.enums.TicketPriority;
import com.krish.aiprocessor.event.TicketCreatedEvent;
import com.krish.aiprocessor.exception.KafkaEventDeserializationException;
import com.krish.aiprocessor.service.AiProcessingService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class TicketCreatedConsumerTest {

    @Mock
    private AiProcessingService aiProcessingService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TicketCreatedConsumer consumer;

    private TicketCreatedEvent sampleEvent;

    private String validJson;

    @BeforeEach
    void setUp() {
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

        validJson = "{\"ticketId\":\"" + ticketId + "\"}";
    }

    @Test
    void consume_validMessage_callsProcessTicket() throws Exception {
        Mockito.when(objectMapper.readValue(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(TicketCreatedEvent.class)
        )).thenReturn(sampleEvent);

        consumer.consume(validJson);

        Mockito.verify(aiProcessingService, Mockito.times(1)).processTicket(sampleEvent);
    }

    @Test
    void consume_invalidJson_throwsForErrorHandler() throws Exception {
        Mockito.when(objectMapper.readValue(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(TicketCreatedEvent.class)
        )).thenThrow(new JsonProcessingException("Invalid JSON") {
        });

        assertThatThrownBy(() -> consumer.consume("invalid"))
            .isInstanceOf(KafkaEventDeserializationException.class);

        Mockito.verify(aiProcessingService, Mockito.never())
            .processTicket(ArgumentMatchers.any(TicketCreatedEvent.class));
    }

    @Test
    void consume_processingFails_rethrowsException() throws Exception {
        Mockito.when(objectMapper.readValue(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(TicketCreatedEvent.class)
        )).thenReturn(sampleEvent);
        Mockito.doThrow(new IllegalStateException("Processing failed"))
            .when(aiProcessingService)
            .processTicket(sampleEvent);

        Assertions.assertThrows(IllegalStateException.class, () -> consumer.consume(validJson));

        Mockito.verify(aiProcessingService, Mockito.times(1)).processTicket(sampleEvent);
    }

    @Test
    void consume_successfulProcessing_logsAndCompletes() throws Exception {
        Mockito.when(objectMapper.readValue(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(TicketCreatedEvent.class)
        )).thenReturn(sampleEvent);

        Assertions.assertDoesNotThrow(() -> consumer.consume(validJson));

        Mockito.verify(aiProcessingService, Mockito.times(1)).processTicket(sampleEvent);
    }
}
