package com.krish.supportapi.domain.dto.response;

import com.krish.supportapi.domain.enums.TicketCategory;
import com.krish.supportapi.domain.enums.TicketPriority;
import com.krish.supportapi.domain.enums.TicketStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketDetailResponse {

    private UUID id;

    private String ticketNumber;

    private String title;

    private String description;

    private TicketStatus status;

    private TicketPriority priority;

    private TicketCategory category;

    private UUID customerId;

    private UUID assignedAgentId;

    private BigDecimal aiConfidenceScore;

    private boolean aiEscalated;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Page<MessageResponse> messages;
}
