package com.krish.supportapi.controller;

import com.krish.supportapi.domain.dto.request.CreateMessageRequest;
import com.krish.supportapi.domain.dto.response.MessageResponse;
import com.krish.supportapi.domain.entity.User;
import com.krish.supportapi.service.MessageService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
        @PathVariable UUID ticketId,
        @Valid @RequestBody CreateMessageRequest request,
        Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        MessageResponse response = messageService.sendMessage(ticketId, request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<MessageResponse>> getMessages(
        @PathVariable UUID ticketId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        Authentication authentication
    ) {
        User currentUser = getCurrentUser(authentication);
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.ASC, "createdAt")
        );
        Page<MessageResponse> response = messageService.getMessages(
            ticketId,
            currentUser.getId(),
            currentUser.getRole(),
            pageable
        );
        return ResponseEntity.ok(response);
    }

    private User getCurrentUser(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
