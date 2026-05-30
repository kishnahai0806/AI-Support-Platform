package com.krish.supportapi.service;

import com.krish.supportapi.domain.dto.request.CreateMessageRequest;
import com.krish.supportapi.domain.dto.response.MessageResponse;
import com.krish.supportapi.domain.entity.Ticket;
import com.krish.supportapi.domain.entity.TicketMessage;
import com.krish.supportapi.domain.entity.User;
import com.krish.supportapi.domain.enums.TicketStatus;
import com.krish.supportapi.domain.enums.UserRole;
import com.krish.supportapi.exception.TicketNotFoundException;
import com.krish.supportapi.repository.TicketMessageRepository;
import com.krish.supportapi.repository.TicketRepository;
import com.krish.supportapi.repository.UserRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MessageService {

    private static final String ANALYTICS_CACHE_KEY = "analytics:overview";

    private final TicketRepository ticketRepository;

    private final TicketMessageRepository ticketMessageRepository;

    private final UserRepository userRepository;

    private final StringRedisTemplate stringRedisTemplate;

    public MessageService(
        TicketRepository ticketRepository,
        TicketMessageRepository ticketMessageRepository,
        UserRepository userRepository,
        StringRedisTemplate stringRedisTemplate
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketMessageRepository = ticketMessageRepository;
        this.userRepository = userRepository;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public MessageResponse sendMessage(UUID ticketId, CreateMessageRequest request, UUID senderId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));
        User sender = userRepository.findById(senderId)
            .orElseThrow(() -> new TicketNotFoundException("Sender not found"));

        if (sender.getRole() == UserRole.CUSTOMER
                && !ticket.getCustomer().getId().equals(senderId)) {
            throw new RuntimeException("Access denied: cannot send message to this ticket");
        }

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new RuntimeException("Cannot send message on a closed ticket");
        }

        if (sender.getRole() == UserRole.CUSTOMER && ticket.getStatus() == TicketStatus.RESOLVED) {
            ticket.setStatus(TicketStatus.OPEN);
            ticket.setResolvedAt(null);
            stringRedisTemplate.delete(ANALYTICS_CACHE_KEY);
        }

        TicketMessage message = TicketMessage.builder()
            .ticket(ticket)
            .sender(sender)
            .content(request.getContent())
            .isAiGenerated(false)
            .aiModelUsed(null)
            .aiTokensUsed(null)
            .build();

        TicketMessage savedMessage = ticketMessageRepository.save(message);
        return mapToResponse(savedMessage);
    }

    public Page<MessageResponse> getMessages(
        UUID ticketId,
        UUID requesterId,
        UserRole role,
        Pageable pageable
    ) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));

        if (role == UserRole.CUSTOMER && !ticket.getCustomer().getId().equals(requesterId)) {
            throw new RuntimeException("Access denied");
        }

        return ticketMessageRepository.findByTicketId(ticketId, pageable)
            .map(this::mapToResponse);
    }

    private MessageResponse mapToResponse(TicketMessage message) {
        return MessageResponse.builder()
            .id(message.getId())
            .ticketId(message.getTicket().getId())
            .senderId(message.getSender().getId())
            .content(message.getContent())
            .isAiGenerated(message.isAiGenerated())
            .aiModelUsed(message.getAiModelUsed())
            .createdAt(message.getCreatedAt())
            .build();
    }
}
