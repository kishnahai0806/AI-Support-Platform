package com.krish.supportapi.event;

import com.krish.supportapi.domain.enums.TicketCategory;
import java.math.BigDecimal;
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
public class TicketProcessedEvent {

    private UUID eventId;

    private UUID ticketId;

    private TicketCategory suggestedCategory;

    private BigDecimal confidenceScore;

    private boolean aiEscalated;

    private String modelUsed;

    private int promptTokens;

    private int completionTokens;

    private int totalTokens;

    private long latencyMs;

    private boolean success;

    private String errorMessage;

    private LocalDateTime processedAt;
}
