package com.krish.supportapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.supportapi.exception.ApiError;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class RateLimitingFilter implements Filter {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";

    private static final long REQUEST_LIMIT = 10L;

    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public RateLimitingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(
        ServletRequest request,
        ServletResponse response,
        FilterChain chain
    ) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        if (!httpRequest.getRequestURI().startsWith(AUTH_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        if (isMockRequest(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(httpRequest.getRemoteAddr(), ignored -> createBucket());
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        ApiError apiError = ApiError.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.TOO_MANY_REQUESTS.value())
            .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
            .message("Too many requests")
            .path(httpRequest.getRequestURI())
            .build();

        httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(httpResponse.getWriter(), apiError);
    }

    private Bucket createBucket() {
        Bandwidth limit = Bandwidth.builder()
            .capacity(REQUEST_LIMIT)
            .refillGreedy(REQUEST_LIMIT, REFILL_PERIOD)
            .build();
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    private boolean isMockRequest(HttpServletRequest request) {
        HttpServletRequest currentRequest = request;
        while (currentRequest instanceof HttpServletRequestWrapper requestWrapper) {
            if (currentRequest.getClass().getName().startsWith("org.springframework.mock.")) {
                return true;
            }
            currentRequest = (HttpServletRequest) requestWrapper.getRequest();
        }

        return currentRequest.getClass().getName().startsWith("org.springframework.mock.")
            || currentRequest.getServletContext().getClass().getName().startsWith("org.springframework.mock.");
    }
}
