package com.krish.supportapi.domain.dto.request;

import com.krish.supportapi.domain.enums.TicketCategory;
import com.krish.supportapi.domain.enums.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTicketRequest {

    @NotBlank
    @Size(max = 500)
    private String title;

    @NotBlank
    @Size(max = 10000)
    private String description;

    private TicketPriority priority;

    private TicketCategory category;
}
