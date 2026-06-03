package com.krish.supportapi.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final HttpServletRequest request;

    public GlobalExceptionHandler(HttpServletRequest request) {
        this.request = request;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValidException(
        MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .collect(Collectors.joining(", "));

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Failed", message);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyExistsException(
        EmailAlreadyExistsException exception
    ) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", "Registration failed");
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiError> handleInvalidTokenException(InvalidTokenException exception) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", exception.getMessage());
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ApiError> handleTicketNotFoundException(TicketNotFoundException exception) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", exception.getMessage());
    }

    @ExceptionHandler(AgentNotFoundException.class)
    public ResponseEntity<ApiError> handleAgentNotFoundException(AgentNotFoundException exception) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", exception.getMessage());
    }

    @ExceptionHandler(TicketClosedException.class)
    public ResponseEntity<ApiError> handleTicketClosedException(TicketClosedException exception) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(IllegalArgumentException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", exception.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleRuntimeException(RuntimeException exception) {
        log.error("Unhandled runtime exception", exception);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error", "An unexpected error occurred");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentialsException(
        BadCredentialsException exception
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password");
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiError> handleUsernameNotFoundException(
        UsernameNotFoundException exception
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(AccessDeniedException exception) {
        return buildResponse(
            HttpStatus.FORBIDDEN,
            "Forbidden",
            "You do not have permission to access this resource"
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(Exception exception) {
        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            "An unexpected error occurred"
        );
    }

    private ResponseEntity<ApiError> buildResponse(
        HttpStatus status,
        String error,
        String message
    ) {
        ApiError apiError = ApiError.builder()
            .timestamp(LocalDateTime.now())
            .status(status.value())
            .error(error)
            .message(message)
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.status(status).body(apiError);
    }
}
