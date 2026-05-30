package com.krish.supportapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.supportapi.domain.dto.request.CreateTicketRequest;
import com.krish.supportapi.domain.dto.request.LoginRequest;
import com.krish.supportapi.domain.dto.request.RegisterRequest;
import com.krish.supportapi.domain.dto.request.UpdateTicketStatusRequest;
import com.krish.supportapi.domain.enums.TicketPriority;
import com.krish.supportapi.domain.enums.TicketStatus;
import com.krish.supportapi.domain.enums.UserRole;
import com.krish.supportapi.repository.UserRepository;
import java.util.UUID;
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
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = TicketControllerIT.TestApplication.class
)
@AutoConfigureMockMvc
@Testcontainers
class TicketControllerIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("support_db")
        .withUsername("postgres")
        .withPassword("postgres");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    private String accessToken;

    private String agentToken;

    private String adminToken;

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

        String customerEmail = uniqueEmail();
        String password = "password123";
        registerUser(registerRequest(customerEmail, password));
        Thread.sleep(1100);
        accessToken = loginAndExtractAccessToken(loginRequest(customerEmail, password));

        String agentEmail = uniqueEmail();
        registerUser(registerRequest(agentEmail, password));
        updateUserRole(agentEmail, UserRole.AGENT);
        agentToken = loginAndExtractAccessToken(loginRequest(agentEmail, password));

        String adminEmail = uniqueEmail();
        registerUser(registerRequest(adminEmail, password));
        updateUserRole(adminEmail, UserRole.ADMIN);
        adminToken = loginAndExtractAccessToken(loginRequest(adminEmail, password));
    }

    @Test
    void createTicket_validRequest_returns201() throws Exception {
        CreateTicketRequest request = createTicketRequest("Need help with billing");

        performAuthenticatedRequest(post("/api/v1/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ticketNumber").value(startsWith("TKT-")))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.title").value(request.getTitle()));
    }

    @Test
    void createTicket_unauthenticated_returns401() throws Exception {
        CreateTicketRequest request = createTicketRequest("Need help with billing");

        mockMvc.perform(post("/api/v1/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createTicket_invalidRequest_returns400() throws Exception {
        CreateTicketRequest request = CreateTicketRequest.builder()
            .title("")
            .description("Description is present")
            .priority(TicketPriority.MEDIUM)
            .category(null)
            .build();

        performAuthenticatedRequest(post("/api/v1/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void getTickets_asCustomer_returns200() throws Exception {
        createTicket(createTicketRequest("Need help with account access"));

        performAuthenticatedRequest(get("/api/v1/tickets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void getTicket_byId_returns200() throws Exception {
        String ticketId = createTicketAndExtractId(createTicketRequest("Need help with shipping"));

        performAuthenticatedRequest(get("/api/v1/tickets/{id}", ticketId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(ticketId));
    }

    @Test
    void getTicket_notFound_returns404() throws Exception {
        performAuthenticatedRequest(get("/api/v1/tickets/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_asAgent_returns200() throws Exception {
        String ticketId = createTicketAndExtractId(createTicketRequest("Need technical support"));
        UpdateTicketStatusRequest request = UpdateTicketStatusRequest.builder()
            .status(TicketStatus.IN_PROGRESS)
            .build();

        performRequestWithToken(agentToken, patch("/api/v1/tickets/{id}/status", ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void updateStatus_asCustomer_returns403() throws Exception {
        String ticketId = createTicketAndExtractId(createTicketRequest("Need help with billing"));
        UpdateTicketStatusRequest request = UpdateTicketStatusRequest.builder()
            .status(TicketStatus.IN_PROGRESS)
            .build();

        performAuthenticatedRequest(patch("/api/v1/tickets/{id}/status", ticketId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteTicket_asAdmin_returns204() throws Exception {
        String ticketId = createTicketAndExtractId(createTicketRequest("Need general support"));

        performRequestWithToken(adminToken, delete("/api/v1/tickets/{id}", ticketId))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteTicket_asCustomer_returns403() throws Exception {
        String ticketId = createTicketAndExtractId(createTicketRequest("Need general support"));

        performAuthenticatedRequest(delete("/api/v1/tickets/{id}", ticketId))
            .andExpect(status().isForbidden());
    }

    @Test
    void getAdminAnalytics_asCustomer_returns403() throws Exception {
        performAuthenticatedRequest(get("/api/v1/admin/analytics/overview"))
            .andExpect(status().isForbidden());
    }

    private ResultActions performAuthenticatedRequest(
        MockHttpServletRequestBuilder requestBuilder
    ) throws Exception {
        return mockMvc.perform(requestBuilder.header("Authorization", "Bearer " + accessToken));
    }

    private ResultActions performRequestWithToken(
        String token,
        MockHttpServletRequestBuilder requestBuilder
    ) throws Exception {
        return mockMvc.perform(requestBuilder.header("Authorization", "Bearer " + token));
    }

    private void registerUser(RegisterRequest request) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    private void updateUserRole(String email, UserRole role) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setRole(role);
            userRepository.save(user);
        });
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

    private MvcResult createTicket(CreateTicketRequest request) throws Exception {
        return performAuthenticatedRequest(post("/api/v1/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private String createTicketAndExtractId(CreateTicketRequest request) throws Exception {
        MvcResult result = createTicket(request);
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

    private CreateTicketRequest createTicketRequest(String title) {
        return CreateTicketRequest.builder()
            .title(title)
            .description("Integration test ticket description")
            .priority(TicketPriority.MEDIUM)
            .category(null)
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
