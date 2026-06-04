package com.krish.supportapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.supportapi.config.CacheConstants;
import com.krish.supportapi.domain.dto.request.CreateTicketRequest;
import com.krish.supportapi.domain.dto.request.UpdateTicketStatusRequest;
import com.krish.supportapi.domain.dto.response.TicketDetailResponse;
import com.krish.supportapi.domain.dto.response.TicketResponse;
import com.krish.supportapi.domain.entity.Ticket;
import com.krish.supportapi.domain.entity.User;
import com.krish.supportapi.domain.enums.TicketPriority;
import com.krish.supportapi.domain.enums.TicketStatus;
import com.krish.supportapi.domain.enums.UserRole;
import com.krish.supportapi.event.TicketCreatedEvent;
import com.krish.supportapi.repository.TicketRepository;
import com.krish.supportapi.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private TicketService ticketService;

    private User sampleCustomer;

    private User sampleAgent;

    private Ticket sampleTicket;

    private CreateTicketRequest createRequest;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
            ticketRepository,
            userRepository,
            kafkaTemplate,
            objectMapper,
            stringRedisTemplate,
            meterRegistry,
            "ticket.created"
        );

        sampleCustomer = User.builder()
            .id(UUID.randomUUID())
            .email("customer@example.com")
            .fullName("Sample Customer")
            .role(UserRole.CUSTOMER)
            .isActive(true)
            .build();

        sampleAgent = User.builder()
            .id(UUID.randomUUID())
            .email("agent@example.com")
            .fullName("Sample Agent")
            .role(UserRole.AGENT)
            .isActive(true)
            .build();

        sampleTicket = Ticket.builder()
            .id(UUID.randomUUID())
            .ticketNumber("TKT-000001")
            .title("Test ticket")
            .description("Test ticket description")
            .status(TicketStatus.OPEN)
            .priority(TicketPriority.MEDIUM)
            .customer(sampleCustomer)
            .build();

        createRequest = CreateTicketRequest.builder()
            .title("Test ticket")
            .description("Test ticket description")
            .priority(TicketPriority.MEDIUM)
            .category(null)
            .build();
    }

    @Test
    void createTicket_success_returnsTicketResponse() throws JsonProcessingException {
        Mockito.when(userRepository.findById(sampleCustomer.getId())).thenReturn(Optional.of(sampleCustomer));
        Mockito.when(ticketRepository.nextTicketNumber()).thenReturn(1L);
        Mockito.when(ticketRepository.save(ArgumentMatchers.any(Ticket.class))).thenReturn(sampleTicket);
        Mockito.when(objectMapper.writeValueAsString(ArgumentMatchers.any(TicketCreatedEvent.class)))
            .thenReturn("{}");
        Mockito.when(kafkaTemplate.send(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString()
        )).thenReturn(CompletableFuture.completedFuture(null));

        TicketResponse response = ticketService.createTicket(createRequest, sampleCustomer.getId());

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo(sampleTicket.getTitle());
        Mockito.verify(ticketRepository, Mockito.times(1)).save(ArgumentMatchers.any(Ticket.class));
        Mockito.verify(kafkaTemplate, Mockito.times(1)).send(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString()
        );
    }

    @Test
    void createTicket_customerNotFound_throwsException() {
        Mockito.when(userRepository.findById(sampleCustomer.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.createTicket(createRequest, sampleCustomer.getId()))
            .isInstanceOf(RuntimeException.class);

        Mockito.verify(ticketRepository, Mockito.never()).save(ArgumentMatchers.any(Ticket.class));
    }

    @Test
    void getTickets_asCustomer_returnsOnlyOwnTickets() {
        Pageable pageable = PageRequest.of(0, 20);
        Mockito.when(ticketRepository.findAll(
            ArgumentMatchers.any(Specification.class),
            ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(sampleTicket)));

        Page<TicketResponse> response = ticketService.getTickets(
            sampleCustomer.getId(),
            UserRole.CUSTOMER,
            null,
            null,
            null,
            pageable
        );

        assertThat(response).isNotNull();
        Mockito.verify(ticketRepository, Mockito.times(1))
            .findAll(ArgumentMatchers.any(Specification.class), ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void getTickets_asAdmin_returnsAllTickets() {
        Pageable pageable = PageRequest.of(0, 20);
        Mockito.when(ticketRepository.findAll(
            ArgumentMatchers.any(Specification.class),
            ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(sampleTicket)));

        Page<TicketResponse> response = ticketService.getTickets(
            sampleAgent.getId(),
            UserRole.ADMIN,
            null,
            null,
            null,
            pageable
        );

        assertThat(response).isNotNull();
        Mockito.verify(ticketRepository, Mockito.times(1))
            .findAll(ArgumentMatchers.any(Specification.class), ArgumentMatchers.any(Pageable.class));
    }

    @Test
    void getTicket_asCustomer_ownTicket_returnsDetail() {
        Mockito.when(ticketRepository.findById(sampleTicket.getId())).thenReturn(Optional.of(sampleTicket));

        TicketDetailResponse response = ticketService.getTicket(
            sampleTicket.getId(),
            sampleCustomer.getId(),
            UserRole.CUSTOMER
        );

        assertThat(response).isNotNull();
    }

    @Test
    void getTicket_asCustomer_otherTicket_throwsException() {
        Mockito.when(ticketRepository.findById(sampleTicket.getId())).thenReturn(Optional.of(sampleTicket));

        assertThatThrownBy(() -> ticketService.getTicket(sampleTicket.getId(), UUID.randomUUID(), UserRole.CUSTOMER))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void updateStatus_toResolved_setsResolvedAt() {
        UpdateTicketStatusRequest request = UpdateTicketStatusRequest.builder()
            .status(TicketStatus.RESOLVED)
            .build();
        Mockito.when(ticketRepository.findById(sampleTicket.getId())).thenReturn(Optional.of(sampleTicket));
        Mockito.when(ticketRepository.save(sampleTicket)).thenReturn(sampleTicket);

        TicketResponse response = ticketService.updateStatus(sampleTicket.getId(), request, sampleAgent.getId());

        assertThat(response).isNotNull();
        assertThat(sampleTicket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(sampleTicket.getResolvedAt()).isNotNull();
        Mockito.verify(ticketRepository, Mockito.times(1)).save(sampleTicket);
        Mockito.verify(stringRedisTemplate, Mockito.times(1)).delete(CacheConstants.ANALYTICS_OVERVIEW_KEY);
    }

    @Test
    void deleteTicket_success_deletesAndEvictsCache() {
        Mockito.when(ticketRepository.findById(sampleTicket.getId())).thenReturn(Optional.of(sampleTicket));

        ticketService.deleteTicket(sampleTicket.getId());

        Mockito.verify(ticketRepository, Mockito.times(1)).delete(sampleTicket);
        Mockito.verify(stringRedisTemplate, Mockito.times(1)).delete(CacheConstants.ANALYTICS_OVERVIEW_KEY);
    }
}
