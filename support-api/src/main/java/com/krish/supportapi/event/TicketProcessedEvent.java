package com.krish.supportapi.event;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.krish.supportapi.domain.enums.TicketCategory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@AllArgsConstructor
@Builder
@JsonDeserialize(builder = TicketProcessedEvent.TicketProcessedEventBuilder.class)
public class TicketProcessedEvent {

    UUID eventId;

    UUID ticketId;

    TicketCategory suggestedCategory;

    BigDecimal confidenceScore;

    boolean aiEscalated;

    String modelUsed;

    int promptTokens;

    int completionTokens;

    int totalTokens;

    long latencyMs;

    boolean success;

    String errorMessage;

    LocalDateTime processedAt;

    @JsonPOJOBuilder(withPrefix = "")
    public static class TicketProcessedEventBuilder {
    }
}
