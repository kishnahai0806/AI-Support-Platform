package com.krish.supportapi.controller;

import com.krish.supportapi.domain.dto.request.LoginRequest;
import com.krish.supportapi.domain.dto.request.RegisterRequest;
import com.krish.supportapi.domain.dto.response.AuthResponse;
import com.krish.supportapi.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        HttpServletRequest request,
        @RequestBody Map<String, String> body
    ) {
        String refreshToken = body.get("refreshToken");
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        String accessToken = null;

        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            accessToken = authorizationHeader.substring(BEARER_PREFIX.length());
        }

        authService.logout(refreshToken, accessToken);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
