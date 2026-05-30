package com.krish.aiprocessor.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.aiprocessor.event.TicketCreatedEvent;
import com.krish.aiprocessor.service.AiProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TicketCreatedConsumer {

    private final AiProcessingService aiProcessingService;

    private final ObjectMapper objectMapper;

    public TicketCreatedConsumer(
        AiProcessingService aiProcessingService,
        ObjectMapper objectMapper
    ) {
        this.aiProcessingService = aiProcessingService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = "${spring.kafka.topics.ticket-created}",
        groupId = "ai-processor-group"
    )
    public void consume(String message) {
        TicketCreatedEvent event;

        try {
            event = objectMapper.readValue(message, TicketCreatedEvent.class);
        } catch (JsonProcessingException exception) {
            log.error("Failed to deserialize ticket.created event", exception);
            return;
        }

        try {
            log.info("Received ticket.created event for ticket {}", event.getTicketId());
            aiProcessingService.processTicket(event);
        } catch (Exception exception) {
            log.error("Failed to process ticket.created event for ticket {}", event.getTicketId(), exception);
            throw new IllegalStateException("Failed to process ticket.created event", exception);
        }
    }
}
