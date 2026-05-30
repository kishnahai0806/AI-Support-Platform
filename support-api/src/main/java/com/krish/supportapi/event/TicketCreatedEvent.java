package com.krish.supportapi.event;

import com.krish.supportapi.domain.enums.TicketCategory;
import com.krish.supportapi.domain.enums.TicketPriority;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketCreatedEvent {

    private UUID eventId;

    private UUID ticketId;

    private String ticketNumber;

    private String title;

    private String description;

    private TicketCategory category;

    private TicketPriority priority;

    private UUID customerId;

    private String customerEmail;

    private LocalDateTime createdAt;
}
