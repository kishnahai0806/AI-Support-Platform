package com.krish.supportapi.service;

import com.krish.supportapi.domain.dto.request.CreateMessageRequest;
import com.krish.supportapi.domain.dto.response.MessageResponse;
import com.krish.supportapi.domain.entity.Ticket;
import com.krish.supportapi.domain.entity.TicketMessage;
import com.krish.supportapi.domain.entity.User;
import com.krish.supportapi.domain.enums.TicketStatus;
import com.krish.supportapi.domain.enums.UserRole;
import com.krish.supportapi.repository.TicketMessageRepository;
import com.krish.supportapi.repository.TicketRepository;
import com.krish.supportapi.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketMessageRepository ticketMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private MessageService messageService;

    private User sampleCustomer;

    private Ticket sampleTicket;

    private TicketMessage sampleMessage;

    private CreateMessageRequest createRequest;

    @BeforeEach
    void setUp() {
        sampleCustomer = User.builder()
            .id(UUID.randomUUID())
            .email("customer@example.com")
            .fullName("Sample Customer")
            .role(UserRole.CUSTOMER)
            .isActive(true)
            .build();

        sampleTicket = Ticket.builder()
            .id(UUID.randomUUID())
            .status(TicketStatus.OPEN)
            .customer(sampleCustomer)
            .build();

        sampleMessage = TicketMessage.builder()
            .id(UUID.randomUUID())
            .ticket(sampleTicket)
            .sender(sampleCustomer)
            .content("Test message")
            .isAiGenerated(false)
            .build();

        createRequest = CreateMessageRequest.builder()
            .content("Test message")
            .build();
    }

    @Test
    void sendMessage_success_returnsMessageResponse() {
        Mockito.when(ticketRepository.findById(sampleTicket.getId())).thenReturn(Optional.of(sampleTicket));
        Mockito.when(userRepository.findById(sampleCustomer.getId())).thenReturn(Optional.of(sampleCustomer));
        Mockito.when(ticketMessageRepository.save(ArgumentMatchers.any(TicketMessage.class)))
            .thenReturn(sampleMessage);

        MessageResponse response = messageService.sendMessage(
            sampleTicket.getId(),
            createRequest,
            sampleCustomer.getId()
        );

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("Test message");
        Mockito.verify(ticketMessageRepository, Mockito.times(1))
            .save(ArgumentMatchers.any(TicketMessage.class));
    }

    @Test
    void sendMessage_customerSendingToOtherTicket_throwsException() {
        User otherCustomer = User.builder()
            .id(UUID.randomUUID())
            .email("other@example.com")
            .fullName("Other Customer")
            .role(UserRole.CUSTOMER)
            .isActive(true)
            .build();

        Mockito.when(ticketRepository.findById(sampleTicket.getId())).thenReturn(Optional.of(sampleTicket));
        Mockito.when(userRepository.findById(otherCustomer.getId())).thenReturn(Optional.of(otherCustomer));

        assertThatThrownBy(() -> messageService.sendMessage(sampleTicket.getId(), createRequest, otherCustomer.getId()))
            .isInstanceOf(RuntimeException.class);

        Mockito.verify(ticketMessageRepository, Mockito.never())
            .save(ArgumentMatchers.any(TicketMessage.class));
    }

    @Test
    void sendMessage_closedTicket_throwsException() {
        Ticket closedTicket = Ticket.builder()
            .id(sampleTicket.getId())
            .status(TicketStatus.CLOSED)
            .customer(sampleCustomer)
            .build();

        Mockito.when(ticketRepository.findById(closedTicket.getId())).thenReturn(Optional.of(closedTicket));
        Mockito.when(userRepository.findById(sampleCustomer.getId())).thenReturn(Optional.of(sampleCustomer));

        assertThatThrownBy(() -> messageService.sendMessage(closedTicket.getId(), createRequest, sampleCustomer.getId()))
            .isInstanceOf(RuntimeException.class);

        Mockito.verify(ticketMessageRepository, Mockito.never())
            .save(ArgumentMatchers.any(TicketMessage.class));
    }

    @Test
    void sendMessage_customerReplyOnResolved_reopensTicket() {
        Ticket resolvedTicket = Ticket.builder()
            .id(sampleTicket.getId())
            .status(TicketStatus.RESOLVED)
            .customer(sampleCustomer)
            .build();

        Mockito.when(ticketRepository.findById(resolvedTicket.getId())).thenReturn(Optional.of(resolvedTicket));
        Mockito.when(userRepository.findById(sampleCustomer.getId())).thenReturn(Optional.of(sampleCustomer));
        Mockito.when(ticketMessageRepository.save(ArgumentMatchers.any(TicketMessage.class)))
            .thenReturn(sampleMessage);

        messageService.sendMessage(resolvedTicket.getId(), createRequest, sampleCustomer.getId());

        assertThat(resolvedTicket.getStatus()).isEqualTo(TicketStatus.OPEN);
        Mockito.verify(ticketMessageRepository, Mockito.times(1))
            .save(ArgumentMatchers.any(TicketMessage.class));
        Mockito.verify(stringRedisTemplate, Mockito.times(1)).delete("analytics:overview");
    }

    @Test
    void getMessages_asCustomer_ownTicket_returnsPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Mockito.when(ticketRepository.findById(sampleTicket.getId())).thenReturn(Optional.of(sampleTicket));
        Mockito.when(ticketMessageRepository.findByTicketId(sampleTicket.getId(), pageable))
            .thenReturn(new PageImpl<>(List.of(sampleMessage)));

        Page<MessageResponse> response = messageService.getMessages(
            sampleTicket.getId(),
            sampleCustomer.getId(),
            UserRole.CUSTOMER,
            pageable
        );

        assertThat(response).isNotNull();
        Mockito.verify(ticketMessageRepository, Mockito.times(1))
            .findByTicketId(sampleTicket.getId(), pageable);
    }

    @Test
    void getMessages_asCustomer_otherTicket_throwsException() {
        Mockito.when(ticketRepository.findById(sampleTicket.getId())).thenReturn(Optional.of(sampleTicket));

        assertThatThrownBy(() -> messageService.getMessages(
                sampleTicket.getId(),
                UUID.randomUUID(),
                UserRole.CUSTOMER,
                PageRequest.of(0, 20)
            ))
            .isInstanceOf(RuntimeException.class);
    }
}
