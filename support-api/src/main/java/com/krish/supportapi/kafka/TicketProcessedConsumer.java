package com.krish.supportapi.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.supportapi.domain.entity.Ticket;
import com.krish.supportapi.domain.enums.TicketStatus;
import com.krish.supportapi.event.TicketProcessedEvent;
import com.krish.supportapi.repository.TicketRepository;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TicketProcessedConsumer {

    private final TicketRepository ticketRepository;

    private final ObjectMapper objectMapper;

    public TicketProcessedConsumer(
        TicketRepository ticketRepository,
        ObjectMapper objectMapper
    ) {
        this.ticketRepository = ticketRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "ticket.processed",
        groupId = "support-api-group"
    )
    public void consume(String message) {
        TicketProcessedEvent event;

        try {
            event = objectMapper.readValue(message, TicketProcessedEvent.class);
        } catch (JsonProcessingException exception) {
            log.error("Failed to deserialize ticket processed event", exception);
            return;
        }

        try {
            Optional<Ticket> optionalTicket = ticketRepository.findById(event.getTicketId());

            if (optionalTicket.isEmpty()) {
                log.warn("Ticket not found for processed event {}", event.getTicketId());
                return;
            }

            Ticket ticket = optionalTicket.get();

            if (ticket.getAiProcessedAt() != null) {
                log.info("Ticket already processed, skipping");
                return;
            }

            ticket.setAiProcessedAt(event.getProcessedAt());
            ticket.setAiConfidenceScore(event.getConfidenceScore());
            ticket.setAiSuggestedCategory(event.getSuggestedCategory());
            ticket.setAiEscalated(event.isAiEscalated());

            if (event.isAiEscalated()) {
                ticket.setStatus(TicketStatus.ESCALATED);
            } else if (event.isSuccess()) {
                ticket.setStatus(TicketStatus.IN_PROGRESS);
            }

            if (event.getSuggestedCategory() != null && ticket.getCategory() == null) {
                ticket.setCategory(event.getSuggestedCategory());
            }

            ticketRepository.save(ticket);
            log.info("Ticket {} processed successfully by AI", ticket.getId());
        } catch (Exception exception) {
            log.error("Failed to process ticket processed event {}", event.getEventId(), exception);
        }
    }
}
