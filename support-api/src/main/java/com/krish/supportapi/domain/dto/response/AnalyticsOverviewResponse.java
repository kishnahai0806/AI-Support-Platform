package com.krish.supportapi.domain.dto.response;

import com.krish.supportapi.domain.enums.TicketCategory;
import com.krish.supportapi.domain.enums.TicketPriority;
import com.krish.supportapi.domain.enums.TicketStatus;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsOverviewResponse {

    private int totalTickets;

    private int openTickets;

    private int inProgressTickets;

    private int resolvedToday;

    private double avgResolutionTimeHours;

    private double aiResolutionRate;

    private Map<TicketStatus, Long> ticketsByStatus;

    private Map<TicketCategory, Long> ticketsByCategory;

    private Map<TicketPriority, Long> ticketsByPriority;

    private long totalAiTokensUsed;
}
