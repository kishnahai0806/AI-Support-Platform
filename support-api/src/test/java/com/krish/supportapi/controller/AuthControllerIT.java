package com.krish.supportapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.supportapi.domain.dto.request.LoginRequest;
import com.krish.supportapi.domain.dto.request.RegisterRequest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = AuthControllerIT.TestApplication.class
)
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIT {

    @Container
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
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void register_validRequest_returns201() throws Exception {
        String email = uniqueEmail();
        RegisterRequest request = registerRequest(email, "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void register_duplicateEmail_returns400() throws Exception {
        String email = uniqueEmail();
        RegisterRequest request = registerRequest(email, "password123");

        registerUser(request);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest request = registerRequest("notanemail", "password123");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest request = registerRequest(uniqueEmail(), "short");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    void login_validCredentials_returns200() throws Exception {
        String email = uniqueEmail();
        String password = "password123";
        registerUser(registerRequest(email, password));
        Thread.sleep(1100);

        LoginRequest request = loginRequest(email, password);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        String email = uniqueEmail();
        registerUser(registerRequest(email, "password123"));

        LoginRequest request = loginRequest(email, "wrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void login_nonExistentEmail_returns401() throws Exception {
        LoginRequest request = loginRequest(uniqueEmail(), "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_validToken_returns200() throws Exception {
        String email = uniqueEmail();
        String refreshToken = registerAndExtractRefreshToken(registerRequest(email, "password123"));
        Thread.sleep(1100);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void refresh_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of())))
            .andExpect(status().isBadRequest());
    }

    private void registerUser(RegisterRequest request) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    private String registerAndExtractRefreshToken(RegisterRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("refreshToken").asText();
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
