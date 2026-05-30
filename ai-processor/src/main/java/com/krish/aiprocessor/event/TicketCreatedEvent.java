package com.krish.aiprocessor.event;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.krish.aiprocessor.domain.enums.TicketCategory;
import com.krish.aiprocessor.domain.enums.TicketPriority;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = TicketCreatedEvent.TicketCreatedEventBuilder.class)
public class TicketCreatedEvent {

    UUID eventId;

    UUID ticketId;

    String ticketNumber;

    String title;

    String description;

    TicketCategory category;

    TicketPriority priority;

    UUID customerId;

    String customerEmail;

    LocalDateTime createdAt;

    @JsonPOJOBuilder(withPrefix = "")
    public static class TicketCreatedEventBuilder {
    }
}
