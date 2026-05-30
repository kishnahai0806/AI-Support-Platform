package com.krish.supportapi.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.supportapi.domain.entity.Ticket;
import com.krish.supportapi.domain.enums.TicketCategory;
import com.krish.supportapi.domain.enums.TicketStatus;
import com.krish.supportapi.event.TicketProcessedEvent;
import com.krish.supportapi.repository.TicketRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
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

@ExtendWith(MockitoExtension.class)
class TicketProcessedConsumerTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TicketProcessedConsumer consumer;

    private Ticket sampleTicket;

    private TicketProcessedEvent sampleEvent;

    @BeforeEach
    void setUp() {
        sampleTicket = Ticket.builder()
            .id(UUID.randomUUID())
            .status(TicketStatus.AI_PROCESSING)
            .aiProcessedAt(null)
            .category(null)
            .build();

        sampleEvent = TicketProcessedEvent.builder()
            .eventId(UUID.randomUUID())
            .ticketId(sampleTicket.getId())
            .suggestedCategory(TicketCategory.TECHNICAL)
            .confidenceScore(new BigDecimal("0.9500"))
            .aiEscalated(false)
            .modelUsed("gpt-4o-mini")
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .latencyMs(1200L)
            .success(true)
            .errorMessage(null)
            .processedAt(LocalDateTime.now())
            .build();
    }

    @Test
    void consume_successfulEvent_updatesTicket() throws Exception {
        Mockito.when(objectMapper.readValue(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(TicketProcessedEvent.class)
        )).thenReturn(sampleEvent);
        Mockito.when(ticketRepository.findById(sampleTicket.getId())).thenReturn(Optional.of(sampleTicket));
        Mockito.when(ticketRepository.save(sampleTicket)).thenReturn(sampleTicket);

        consumer.consume("{}");

        Mockito.verify(ticketRepository, Mockito.times(1)).save(sampleTicket);
        Assertions.assertNotNull(sampleTicket.getAiProcessedAt());
        Assertions.assertEquals(TicketStatus.IN_PROGRESS, sampleTicket.getStatus());
        Assertions.assertEquals(new BigDecimal("0.9500"), sampleTicket.getAiConfidenceScore());
    }

    @Test
    void consume_escalatedEvent_setsEscalatedStatus() throws Exception {
        TicketProcessedEvent escalatedEvent = TicketProcessedEvent.builder()
            .eventId(UUID.randomUUID())
            .ticketId(sampleTicket.getId())
            .suggestedCategory(TicketCategory.TECHNICAL)
            .confidenceScore(new BigDecimal("0.9500"))
            .aiEscalated(true)
            .modelUsed("gpt-4o-mini")
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .latencyMs(1200L)
            .success(true)
            .errorMessage(null)
            .processedAt(LocalDateTime.now())
            .build();

        Mockito.when(objectMapper.readValue(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(TicketProcessedEvent.class)
        )).thenReturn(escalatedEvent);
        Mockito.when(ticketRepository.findById(sampleTicket.getId())).thenReturn(Optional.of(sampleTicket));
        Mockito.when(ticketRepository.save(sampleTicket)).thenReturn(sampleTicket);

        consumer.consume("{}");

        Assertions.assertEquals(TicketStatus.ESCALATED, sampleTicket.getStatus());
        Mockito.verify(ticketRepository, Mockito.times(1)).save(sampleTicket);
    }

    @Test
    void consume_ticketNotFound_skipsProcessing() throws Exception {
        Mockito.when(objectMapper.readValue(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(TicketProcessedEvent.class)
        )).thenReturn(sampleEvent);
        Mockito.when(ticketRepository.findById(sampleTicket.getId())).thenReturn(Optional.empty());

        consumer.consume("{}");

        Mockito.verify(ticketRepository, Mockito.never()).save(ArgumentMatchers.any(Ticket.class));
    }

    @Test
    void consume_alreadyProcessed_skipsProcessing() throws Exception {
        Ticket alreadyProcessedTicket = Ticket.builder()
            .id(sampleTicket.getId())
            .status(TicketStatus.IN_PROGRESS)
            .aiProcessedAt(LocalDateTime.now())
            .category(TicketCategory.TECHNICAL)
            .build();

        Mockito.when(objectMapper.readValue(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(TicketProcessedEvent.class)
        )).thenReturn(sampleEvent);
        Mockito.when(ticketRepository.findById(sampleTicket.getId()))
            .thenReturn(Optional.of(alreadyProcessedTicket));

        consumer.consume("{}");

        Mockito.verify(ticketRepository, Mockito.never()).save(ArgumentMatchers.any(Ticket.class));
    }

    @Test
    void consume_deserializationFailure_skipsProcessing() throws Exception {
        Mockito.when(objectMapper.readValue(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(TicketProcessedEvent.class)
        )).thenThrow(new JsonProcessingException("Invalid JSON") {
        });

        consumer.consume("invalid json");

        Mockito.verify(ticketRepository, Mockito.never()).findById(ArgumentMatchers.any(UUID.class));
        Mockito.verify(ticketRepository, Mockito.never()).save(ArgumentMatchers.any(Ticket.class));
    }

    @Test
    void consume_successfulEvent_setsCategoryIfNull() throws Exception {
        Mockito.when(objectMapper.readValue(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(TicketProcessedEvent.class)
        )).thenReturn(sampleEvent);
        Mockito.when(ticketRepository.findById(sampleTicket.getId())).thenReturn(Optional.of(sampleTicket));
        Mockito.when(ticketRepository.save(sampleTicket)).thenReturn(sampleTicket);

        consumer.consume("{}");

        Assertions.assertEquals(TicketCategory.TECHNICAL, sampleTicket.getCategory());
    }

    @Test
    void consume_failedAiEvent_keepsCurrentStatus() throws Exception {
        TicketProcessedEvent failedEvent = TicketProcessedEvent.builder()
            .eventId(UUID.randomUUID())
            .ticketId(sampleTicket.getId())
            .suggestedCategory(TicketCategory.TECHNICAL)
            .confidenceScore(new BigDecimal("0.9500"))
            .aiEscalated(false)
            .modelUsed("gpt-4o-mini")
            .promptTokens(100)
            .completionTokens(0)
            .totalTokens(100)
            .latencyMs(1200L)
            .success(false)
            .errorMessage("AI processing failed")
            .processedAt(LocalDateTime.now())
            .build();

        Mockito.when(objectMapper.readValue(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(TicketProcessedEvent.class)
        )).thenReturn(failedEvent);
        Mockito.when(ticketRepository.findById(sampleTicket.getId())).thenReturn(Optional.of(sampleTicket));
        Mockito.when(ticketRepository.save(sampleTicket)).thenReturn(sampleTicket);

        consumer.consume("{}");

        Assertions.assertEquals(TicketStatus.AI_PROCESSING, sampleTicket.getStatus());
        Mockito.verify(ticketRepository, Mockito.times(1)).save(sampleTicket);
    }
}
