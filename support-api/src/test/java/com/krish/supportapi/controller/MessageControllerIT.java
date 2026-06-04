package com.krish.supportapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.supportapi.domain.dto.request.CreateMessageRequest;
import com.krish.supportapi.domain.dto.request.CreateTicketRequest;
import com.krish.supportapi.domain.dto.request.LoginRequest;
import com.krish.supportapi.domain.dto.request.RegisterRequest;
import com.krish.supportapi.domain.enums.TicketPriority;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = MessageControllerIT.TestApplication.class
)
@AutoConfigureMockMvc
@Testcontainers
class MessageControllerIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("support_db")
        .withUsername("postgres")
        .withPassword("postgres");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    private String accessToken;

    private String ticketId;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "test-secret-key-that-is-at-least-32-bytes-long");
        registry.add("jwt.access-token-expiry-ms", () -> "900000");
        registry.add("jwt.refresh-token-expiry-ms", () -> "604800000");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("openai.api-key", () -> "test-api-key");
    }

    @BeforeEach
    void setUp() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        String email = uniqueEmail();
        String password = "password123";
        registerUser(registerRequest(email, password));
        Thread.sleep(1100);
        accessToken = loginAndExtractAccessToken(loginRequest(email, password));
        ticketId = createTicket(accessToken);
    }

    @Test
    void sendMessage_validRequest_returns201() throws Exception {
        CreateMessageRequest request = CreateMessageRequest.builder()
            .content("Test message content")
            .build();

        performAuth(post("/api/v1/tickets/{ticketId}/messages", ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.content").value("Test message content"))
            .andExpect(jsonPath("$.ticketId").value(ticketId));
    }

    @Test
    void sendMessage_unauthenticated_returns401() throws Exception {
        CreateMessageRequest request = CreateMessageRequest.builder()
            .content("Test message content")
            .build();

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/messages", ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sendMessage_blankContent_returns400() throws Exception {
        CreateMessageRequest request = CreateMessageRequest.builder()
            .content("")
            .build();

        performAuth(post("/api/v1/tickets/{ticketId}/messages", ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void getMessages_validTicket_returns200() throws Exception {
        CreateMessageRequest request = CreateMessageRequest.builder()
            .content("First message")
            .build();

        performAuth(post("/api/v1/tickets/{ticketId}/messages", ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        performAuth(get("/api/v1/tickets/{ticketId}/messages", ticketId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void getMessages_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{ticketId}/messages", ticketId))
            .andExpect(status().isUnauthorized());
    }

    private ResultActions performAuth(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return mockMvc.perform(requestBuilder.header("Authorization", "Bearer " + accessToken));
    }

    private void registerUser(RegisterRequest request) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    private String loginAndExtractAccessToken(LoginRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }

    private String createTicket(String token) throws Exception {
        CreateTicketRequest request = CreateTicketRequest.builder()
            .title("Test ticket for messages")
            .description("Integration test ticket")
            .priority(TicketPriority.MEDIUM)
            .category(null)
            .build();

        MvcResult result = mockMvc.perform(post("/api/v1/tickets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asText();
    }

    private RegisterRequest registerRequest(String email, String password) {
        return RegisterRequest.builder()
            .email(email)
            .password(password)
            .fullName("Integration Test User")
            .build();
    }

    private LoginRequest loginRequest(String email, String password) {
        return LoginRequest.builder()
            .email(email)
            .password(password)
            .build();
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @SpringBootApplication(scanBasePackages = "com.krish.supportapi")
    @EntityScan("com.krish.supportapi.domain.entity")
    @EnableJpaRepositories("com.krish.supportapi.repository")
    static class TestApplication {
    }
}
