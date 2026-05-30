package com.krish.supportapi.controller;

import com.krish.supportapi.domain.dto.response.AnalyticsOverviewResponse;
import com.krish.supportapi.domain.dto.response.UserResponse;
import com.krish.supportapi.service.AnalyticsService;
import com.krish.supportapi.service.TicketService;
import com.krish.supportapi.service.UserService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final TicketService ticketService;

    private final AnalyticsService analyticsService;

    private final UserService userService;

    public AdminController(
        TicketService ticketService,
        AnalyticsService analyticsService,
        UserService userService
    ) {
        this.ticketService = ticketService;
        this.analyticsService = analyticsService;
        this.userService = userService;
    }

    @GetMapping("/analytics/overview")
    public ResponseEntity<AnalyticsOverviewResponse> getAnalyticsOverview(
        Authentication authentication
    ) {
        AnalyticsOverviewResponse response = analyticsService.getOverview();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserResponse>> getUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<UserResponse> response = userService.getUsers(pageable);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/tickets/{id}")
    public ResponseEntity<Void> deleteTicket(
        @PathVariable UUID id,
        Authentication authentication
    ) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }
}
