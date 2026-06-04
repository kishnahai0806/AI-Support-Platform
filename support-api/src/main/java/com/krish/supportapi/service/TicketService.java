package com.krish.supportapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.supportapi.config.CacheConstants;
import com.krish.supportapi.domain.dto.request.AssignTicketRequest;
import com.krish.supportapi.domain.dto.request.CreateTicketRequest;
import com.krish.supportapi.domain.dto.request.UpdateTicketStatusRequest;
import com.krish.supportapi.domain.dto.response.TicketDetailResponse;
import com.krish.supportapi.domain.dto.response.TicketResponse;
import com.krish.supportapi.domain.entity.Ticket;
import com.krish.supportapi.domain.entity.User;
import com.krish.supportapi.domain.enums.TicketCategory;
import com.krish.supportapi.domain.enums.TicketPriority;
import com.krish.supportapi.domain.enums.TicketStatus;
import com.krish.supportapi.domain.enums.UserRole;
import com.krish.supportapi.event.TicketCreatedEvent;
import com.krish.supportapi.exception.AgentNotFoundException;
import com.krish.supportapi.exception.KafkaEventProcessingException;
import com.krish.supportapi.exception.TicketNotFoundException;
import com.krish.supportapi.exception.UserNotFoundException;
import com.krish.supportapi.repository.TicketRepository;
import com.krish.supportapi.repository.TicketSpecification;
import com.krish.supportapi.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@Slf4j
public class TicketService {

    private static final String TICKETS_CREATED_TOTAL_METRIC = "tickets.created.total";

    private static final String TICKET_CREATION_DURATION_METRIC = "ticket.creation.duration";

    private static final String PRIORITY_TAG = "priority";

    private static final String CATEGORY_TAG = "category";

    private static final String UNSET_CATEGORY_TAG_VALUE = "NONE";

    private final TicketRepository ticketRepository;

    private final UserRepository userRepository;

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final MeterRegistry meterRegistry;

    private final String ticketCreatedTopic;

    public TicketService(
        TicketRepository ticketRepository,
        UserRepository userRepository,
        KafkaTemplate<String, String> kafkaTemplate,
        ObjectMapper objectMapper,
        StringRedisTemplate stringRedisTemplate,
        MeterRegistry meterRegistry,
        @Value("${spring.kafka.topics.ticket-created}") String ticketCreatedTopic
    ) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.meterRegistry = meterRegistry;
        this.ticketCreatedTopic = ticketCreatedTopic;
    }

    public TicketResponse createTicket(CreateTicketRequest request, UUID customerId) {
        Sample sample = Timer.start(meterRegistry);

        try {
            User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new UserNotFoundException("Customer not found"));

            String ticketNumber = "TKT-" + String.format("%06d", ticketRepository.nextTicketNumber());
            TicketPriority priority = request.getPriority() != null
                ? request.getPriority()
                : TicketPriority.MEDIUM;

            Ticket ticket = Ticket.builder()
                .ticketNumber(ticketNumber)
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(priority)
                .category(request.getCategory())
                .status(TicketStatus.AI_PROCESSING)
                .customer(customer)
                .build();

            Ticket savedTicket = ticketRepository.save(ticket);

            TicketCreatedEvent event = TicketCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .ticketId(savedTicket.getId())
                .ticketNumber(savedTicket.getTicketNumber())
                .title(savedTicket.getTitle())
                .description(savedTicket.getDescription())
                .category(savedTicket.getCategory())
                .priority(savedTicket.getPriority())
                .customerId(customer.getId())
                .customerEmail(customer.getEmail())
                .createdAt(savedTicket.getCreatedAt())
                .build();

            String serializedEvent;
            try {
                serializedEvent = objectMapper.writeValueAsString(event);
            } catch (JsonProcessingException exception) {
                throw new KafkaEventProcessingException("Failed to serialize ticket created event", exception);
            }

            kafkaTemplate.send(ticketCreatedTopic, savedTicket.getId().toString(), serializedEvent)
                .whenComplete((sendResult, exception) -> {
                    if (exception != null) {
                        log.error("Failed to publish ticket.created event {}", event.getEventId(), exception);
                    } else {
                        log.debug("Published ticket.created event {} to {}", event.getEventId(), ticketCreatedTopic);
                    }
                });
            meterRegistry.counter(
                TICKETS_CREATED_TOTAL_METRIC,
                PRIORITY_TAG,
                savedTicket.getPriority().name(),
                CATEGORY_TAG,
                savedTicket.getCategory() != null
                    ? savedTicket.getCategory().name()
                    : UNSET_CATEGORY_TAG_VALUE
            ).increment();
            stringRedisTemplate.delete(CacheConstants.ANALYTICS_OVERVIEW_KEY);

            return mapToResponse(savedTicket);
        } finally {
            sample.stop(Timer.builder(TICKET_CREATION_DURATION_METRIC)
                .register(meterRegistry));
        }
    }

    public Page<TicketResponse> getTickets(
        UUID customerId,
        UserRole role,
        TicketStatus status,
        TicketPriority priority,
        TicketCategory category,
        Pageable pageable
    ) {
        Specification<Ticket> spec = TicketSpecification.buildFilter(
            customerId, role, status, priority, category);
        return ticketRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    public TicketDetailResponse getTicket(UUID ticketId, UUID requesterId, UserRole role) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));

        if (role == UserRole.CUSTOMER && !ticket.getCustomer().getId().equals(requesterId)) {
            throw new AccessDeniedException("Access denied to this ticket");
        }

        return mapToDetailResponse(ticket);
    }

    public TicketResponse updateStatus(
        UUID ticketId,
        UpdateTicketStatusRequest request,
        UUID agentId
    ) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));

        ticket.setStatus(request.getStatus());

        if (request.getStatus() == TicketStatus.RESOLVED && ticket.getResolvedAt() == null) {
            ticket.setResolvedAt(LocalDateTime.now());
        }

        if (request.getStatus() == TicketStatus.CLOSED) {
            ticket.setClosedAt(LocalDateTime.now());
        }

        Ticket savedTicket = ticketRepository.save(ticket);
        stringRedisTemplate.delete(CacheConstants.ANALYTICS_OVERVIEW_KEY);

        return mapToResponse(savedTicket);
    }

    public TicketResponse assignTicket(UUID ticketId, AssignTicketRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));
        User agent = userRepository.findById(request.getAgentId())
            .orElseThrow(() -> new AgentNotFoundException("Agent not found"));

        if (agent.getRole() != UserRole.AGENT) {
            throw new IllegalArgumentException("Assigned user is not an agent");
        }

        ticket.setAssignedAgent(agent);

        if (ticket.getFirstResponseAt() == null) {
            ticket.setFirstResponseAt(LocalDateTime.now());
        }

        Ticket savedTicket = ticketRepository.save(ticket);
        stringRedisTemplate.delete(CacheConstants.ANALYTICS_OVERVIEW_KEY);

        return mapToResponse(savedTicket);
    }

    public TicketResponse updatePriority(UUID ticketId, TicketPriority priority) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));

        ticket.setPriority(priority);

        Ticket savedTicket = ticketRepository.save(ticket);
        stringRedisTemplate.delete(CacheConstants.ANALYTICS_OVERVIEW_KEY);

        return mapToResponse(savedTicket);
    }

    public void deleteTicket(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));

        ticketRepository.delete(ticket);
        stringRedisTemplate.delete(CacheConstants.ANALYTICS_OVERVIEW_KEY);
    }

    private TicketResponse mapToResponse(Ticket ticket) {
        return TicketResponse.builder()
            .id(ticket.getId())
            .ticketNumber(ticket.getTicketNumber())
            .title(ticket.getTitle())
            .description(ticket.getDescription())
            .status(ticket.getStatus())
            .priority(ticket.getPriority())
            .category(ticket.getCategory())
            .customerId(ticket.getCustomer().getId())
            .assignedAgentId(ticket.getAssignedAgent() != null
                ? ticket.getAssignedAgent().getId()
                : null)
            .aiConfidenceScore(ticket.getAiConfidenceScore())
            .aiEscalated(ticket.isAiEscalated())
            .createdAt(ticket.getCreatedAt())
            .updatedAt(ticket.getUpdatedAt())
            .build();
    }

    private TicketDetailResponse mapToDetailResponse(Ticket ticket) {
        TicketResponse response = mapToResponse(ticket);

        return TicketDetailResponse.builder()
            .id(response.getId())
            .ticketNumber(response.getTicketNumber())
            .title(response.getTitle())
            .description(response.getDescription())
            .status(response.getStatus())
            .priority(response.getPriority())
            .category(response.getCategory())
            .customerId(response.getCustomerId())
            .assignedAgentId(response.getAssignedAgentId())
            .aiConfidenceScore(response.getAiConfidenceScore())
            .aiEscalated(response.isAiEscalated())
            .createdAt(response.getCreatedAt())
            .updatedAt(response.getUpdatedAt())
            .build();
    }
}
