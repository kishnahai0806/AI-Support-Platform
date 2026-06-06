package com.krish.supportapi.kafka;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.supportapi.domain.entity.Ticket;
import com.krish.supportapi.domain.entity.User;
import com.krish.supportapi.domain.enums.TicketCategory;
import com.krish.supportapi.domain.enums.TicketStatus;
import com.krish.supportapi.domain.enums.UserRole;
import com.krish.supportapi.event.TicketProcessedEvent;
import com.krish.supportapi.repository.TicketRepository;
import com.krish.supportapi.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = TicketProcessedConsumerIT.TestApplication.class
)
@EmbeddedKafka(
    partitions = 1,
    topics = {"ticket.processed", "ticket.processed-dlt"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9093",
        "port=9093"
    }
)
@Testcontainers
class TicketProcessedConsumerIT {

    private static final String TICKET_PROCESSED_TOPIC = "ticket.processed";

    private static final String TICKET_PROCESSED_DLT_TOPIC = "ticket.processed-dlt";

    private static final String MALFORMED_EVENT_PAYLOAD = "not-json";

    private static final int DLT_POLL_TIMEOUT_SECONDS = 10;

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("support_db")
        .withUsername("postgres")
        .withPassword("postgres");

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    private UUID ticketId;
    private Ticket savedTicket;

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
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9093");
        registry.add("openai.api-key", () -> "test-api-key");
    }

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        User user = userRepository.save(User.builder()
            .email("consumer-test-" + UUID.randomUUID() + "@example.com")
            .passwordHash("hashed_password")
            .fullName("Test User")
            .role(UserRole.CUSTOMER)
            .isActive(true)
            .build());

        savedTicket = ticketRepository.save(Ticket.builder()
            .ticketNumber("TKT-TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
            .title("Test Ticket")
            .description("Test ticket description")
            .status(TicketStatus.AI_PROCESSING)
            .customer(user)
            .build());

        ticketId = savedTicket.getId();
    }

    @Test
    void processedEvent_successfulAi_updatesTicketToInProgress() throws Exception {
        TicketProcessedEvent event = TicketProcessedEvent.builder()
            .eventId(UUID.randomUUID())
            .ticketId(ticketId)
            .suggestedCategory(TicketCategory.TECHNICAL)
            .confidenceScore(new BigDecimal("0.9500"))
            .aiEscalated(false)
            .success(true)
            .modelUsed("gpt-4o-mini")
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .latencyMs(1200L)
            .errorMessage(null)
            .processedAt(LocalDateTime.now())
            .build();

        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(TICKET_PROCESSED_TOPIC, ticketId.toString(), json).get();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Ticket updated = ticketRepository.findById(ticketId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(updated.getAiProcessedAt()).isNotNull();
            assertThat(updated.getAiConfidenceScore())
                .isEqualByComparingTo(new BigDecimal("0.9500"));
        });
    }

    @Test
    void processedEvent_escalated_updatesTicketToEscalated() throws Exception {
        TicketProcessedEvent event = TicketProcessedEvent.builder()
            .eventId(UUID.randomUUID())
            .ticketId(ticketId)
            .suggestedCategory(TicketCategory.TECHNICAL)
            .confidenceScore(new BigDecimal("0.8000"))
            .aiEscalated(true)
            .success(true)
            .modelUsed("gpt-4o-mini")
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .latencyMs(1200L)
            .errorMessage(null)
            .processedAt(LocalDateTime.now())
            .build();

        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(TICKET_PROCESSED_TOPIC, ticketId.toString(), json).get();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Ticket updated = ticketRepository.findById(ticketId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TicketStatus.ESCALATED);
            assertThat(updated.getAiProcessedAt()).isNotNull();
            assertThat(updated.isAiEscalated()).isTrue();
        });
    }

    @Test
    void processedEvent_alreadyProcessed_skipsUpdate() throws Exception {
        savedTicket.setAiProcessedAt(LocalDateTime.now());
        ticketRepository.save(savedTicket);

        TicketProcessedEvent event = TicketProcessedEvent.builder()
            .eventId(UUID.randomUUID())
            .ticketId(ticketId)
            .suggestedCategory(TicketCategory.TECHNICAL)
            .confidenceScore(new BigDecimal("0.9500"))
            .aiEscalated(false)
            .success(true)
            .modelUsed("gpt-4o-mini")
            .promptTokens(100)
            .completionTokens(50)
            .totalTokens(150)
            .latencyMs(1200L)
            .errorMessage(null)
            .processedAt(LocalDateTime.now())
            .build();

        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(TICKET_PROCESSED_TOPIC, ticketId.toString(), json).get();

        await()
            .pollDelay(3, TimeUnit.SECONDS)
            .atMost(4, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                Ticket updated = ticketRepository.findById(ticketId).orElseThrow();
                assertThat(updated.getStatus()).isEqualTo(TicketStatus.AI_PROCESSING);
            });
    }

    @Test
    void processedEvent_failedAi_updatesTicketToOpen() throws Exception {
        TicketProcessedEvent event = TicketProcessedEvent.builder()
            .eventId(UUID.randomUUID())
            .ticketId(ticketId)
            .suggestedCategory(TicketCategory.GENERAL)
            .confidenceScore(new BigDecimal("0.5000"))
            .aiEscalated(false)
            .success(false)
            .modelUsed("gpt-4o-mini")
            .promptTokens(0)
            .completionTokens(0)
            .totalTokens(0)
            .latencyMs(500L)
            .errorMessage("AI classification failed")
            .processedAt(LocalDateTime.now())
            .build();

        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(TICKET_PROCESSED_TOPIC, ticketId.toString(), json).get();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Ticket updated = ticketRepository.findById(ticketId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(TicketStatus.OPEN);
            assertThat(updated.getAiProcessedAt()).isNotNull();
        });
    }

    @Test
    void malformedProcessedEvent_routesToDlt() throws Exception {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
            "ticket-processed-dlt-test-" + UUID.randomUUID(),
            "false",
            embeddedKafkaBroker
        );
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> dltConsumer =
                new DefaultKafkaConsumerFactory<>(
                    consumerProps,
                    new StringDeserializer(),
                    new StringDeserializer()
                ).createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, TICKET_PROCESSED_DLT_TOPIC);

            kafkaTemplate.send(TICKET_PROCESSED_TOPIC, "malformed", MALFORMED_EVENT_PAYLOAD).get();

            ConsumerRecord<String, String> dltRecord = KafkaTestUtils.getSingleRecord(
                dltConsumer,
                TICKET_PROCESSED_DLT_TOPIC,
                Duration.ofSeconds(DLT_POLL_TIMEOUT_SECONDS)
            );
            assertThat(dltRecord.value()).isEqualTo(MALFORMED_EVENT_PAYLOAD);
        }
    }

    @SpringBootApplication(scanBasePackages = "com.krish.supportapi")
    @EntityScan("com.krish.supportapi.domain.entity")
    @EnableJpaRepositories("com.krish.supportapi.repository")
    static class TestApplication {
    }
}
