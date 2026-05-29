package com.krish.supportapi.domain.dto.response;

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
public class MessageResponse {

    private UUID id;

    private UUID ticketId;

    private UUID senderId;

    private String content;

    private boolean isAiGenerated;

    private String aiModelUsed;

    private LocalDateTime createdAt;
}
