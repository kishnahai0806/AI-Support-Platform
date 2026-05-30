package com.krish.supportapi.controller;

import com.krish.supportapi.domain.dto.request.AssignTicketRequest;
import com.krish.supportapi.domain.dto.request.CreateTicketRequest;
import com.krish.supportapi.domain.dto.request.UpdateTicketPriorityRequest;
import com.krish.supportapi.domain.dto.request.UpdateTicketStatusRequest;
import com.krish.supportapi.domain.dto.response.TicketDetailResponse;
import com.krish.supportapi.domain.dto.response.TicketResponse;
import com.krish.supportapi.domain.entity.User;
import com.krish.supportapi.domain.enums.TicketCategory;
import com.krish.supportapi.domain.enums.TicketPriority;
import com.krish.supportapi.domain.enums.TicketStatus;
import com.krish.supportapi.service.TicketService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
        @Valid @RequestBody CreateTicketRequest request,
        Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        TicketResponse response = ticketService.createTicket(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<TicketResponse>> getTickets(
        @RequestParam(required = false) TicketStatus status,
        @RequestParam(required = false) TicketPriority priority,
        @RequestParam(required = false) TicketCategory category,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir,
        Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(sortDir.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy)
        );
        Page<TicketResponse> response = ticketService.getTickets(
            currentUser.getId(),
            currentUser.getRole(),
            status,
            priority,
            category,
            pageable
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketDetailResponse> getTicket(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        TicketDetailResponse response = ticketService.getTicket(
            id,
            currentUser.getId(),
            currentUser.getRole()
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketResponse> updateStatus(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateTicketStatusRequest request,
        Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        TicketResponse response = ticketService.updateStatus(id, request, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<TicketResponse> assignTicket(
        @PathVariable UUID id,
        @Valid @RequestBody AssignTicketRequest request,
        Authentication authentication
    ) {
        TicketResponse response = ticketService.assignTicket(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/priority")
    public ResponseEntity<TicketResponse> updatePriority(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateTicketPriorityRequest request,
        Authentication authentication
    ) {
        TicketResponse response = ticketService.updatePriority(id, request.getPriority());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }

    private User getCurrentUser(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
