package com.krish.supportapi.domain.dto.request;

import com.krish.supportapi.domain.enums.TicketPriority;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTicketPriorityRequest {

    @NotNull
    private TicketPriority priority;
}
