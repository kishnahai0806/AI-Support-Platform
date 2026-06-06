package com.krish.supportapi.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.supportapi.domain.entity.Ticket;
import com.krish.supportapi.domain.enums.TicketCategory;
import com.krish.supportapi.domain.enums.TicketStatus;
import com.krish.supportapi.event.TicketProcessedEvent;
import com.krish.supportapi.exception.KafkaEventDeserializationException;
import com.krish.supportapi.repository.TicketRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(sampleTicket.getAiProcessedAt()).isNotNull();
        assertThat(sampleTicket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(sampleTicket.getAiConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.9500"));
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

        assertThat(sampleTicket.getStatus()).isEqualTo(TicketStatus.ESCALATED);
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
    void consume_deserializationFailure_throwsForErrorHandler() throws Exception {
        Mockito.when(objectMapper.readValue(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(TicketProcessedEvent.class)
        )).thenThrow(new JsonProcessingException("Invalid JSON") {
        });

        assertThatThrownBy(() -> consumer.consume("invalid json"))
            .isInstanceOf(KafkaEventDeserializationException.class);

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

        assertThat(sampleTicket.getCategory()).isEqualTo(TicketCategory.TECHNICAL);
    }

    @Test
    void consume_failedAiEvent_revertsToOpen() throws Exception {
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

        assertThat(sampleTicket.getStatus()).isEqualTo(TicketStatus.OPEN);
        Mockito.verify(ticketRepository, Mockito.times(1)).save(sampleTicket);
    }

    @Test
    void consume_categoryAlreadySet_doesNotOverrideWithAiSuggestion() throws Exception {
        Ticket ticketWithCategory = Ticket.builder()
            .id(sampleTicket.getId())
            .status(TicketStatus.AI_PROCESSING)
            .aiProcessedAt(null)
            .category(TicketCategory.BILLING)
            .build();

        Mockito.when(objectMapper.readValue(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq(TicketProcessedEvent.class)
        )).thenReturn(sampleEvent);
        Mockito.when(ticketRepository.findById(sampleTicket.getId()))
            .thenReturn(Optional.of(ticketWithCategory));
        Mockito.when(ticketRepository.save(ticketWithCategory)).thenReturn(ticketWithCategory);

        consumer.consume("{}");

        assertThat(ticketWithCategory.getCategory()).isEqualTo(TicketCategory.BILLING);
        Mockito.verify(ticketRepository, Mockito.times(1)).save(ticketWithCategory);
    }
}
