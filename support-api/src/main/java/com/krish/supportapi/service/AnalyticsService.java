package com.krish.supportapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krish.supportapi.domain.dto.response.AnalyticsOverviewResponse;
import com.krish.supportapi.domain.enums.TicketCategory;
import com.krish.supportapi.domain.enums.TicketPriority;
import com.krish.supportapi.domain.enums.TicketStatus;
import com.krish.supportapi.repository.AiResponseAuditRepository;
import com.krish.supportapi.repository.TicketRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private static final String ANALYTICS_CACHE_KEY = "analytics:overview";

    private static final long CACHE_TTL_SECONDS = 300L;

    private final TicketRepository ticketRepository;

    private final AiResponseAuditRepository aiResponseAuditRepository;

    private final ObjectMapper objectMapper;

    private final StringRedisTemplate stringRedisTemplate;

    public AnalyticsService(
        TicketRepository ticketRepository,
        AiResponseAuditRepository aiResponseAuditRepository,
        ObjectMapper objectMapper,
        StringRedisTemplate stringRedisTemplate
    ) {
        this.ticketRepository = ticketRepository;
        this.aiResponseAuditRepository = aiResponseAuditRepository;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public AnalyticsOverviewResponse getOverview() {
        String cachedValue = stringRedisTemplate.opsForValue().get(ANALYTICS_CACHE_KEY);

        if (cachedValue != null) {
            try {
                return objectMapper.readValue(cachedValue, AnalyticsOverviewResponse.class);
            } catch (JsonProcessingException exception) {
                log.warn("Failed to deserialize analytics overview cache", exception);
            }
        }

        AnalyticsOverviewResponse response = buildOverview();

        try {
            String serializedJson = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(
                ANALYTICS_CACHE_KEY,
                serializedJson,
                CACHE_TTL_SECONDS,
                TimeUnit.SECONDS
            );
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize analytics overview cache", exception);
        }

        return response;
    }

    private AnalyticsOverviewResponse buildOverview() {
        int totalTickets = (int) ticketRepository.count();
        int openTickets = (int) ticketRepository.countTotalByStatus(TicketStatus.OPEN);
        int inProgressTickets = (int) ticketRepository.countTotalByStatus(TicketStatus.IN_PROGRESS);
        int resolvedToday = (int) ticketRepository.countResolvedToday(
            LocalDateTime.now().toLocalDate().atStartOfDay()
        );
        double avgResolutionTimeHours = calculateAverageResolutionTimeHours(
            ticketRepository.findCreatedAndResolvedTimestamps()
        );
        Map<TicketStatus, Long> ticketsByStatus = buildTicketsByStatus();
        Map<TicketCategory, Long> ticketsByCategory = buildTicketsByCategory();
        Map<TicketPriority, Long> ticketsByPriority = buildTicketsByPriority();
        Long totalAiTokensUsedValue = aiResponseAuditRepository.sumTotalTokens();
        long totalAiTokensUsed = totalAiTokensUsedValue != null ? totalAiTokensUsedValue : 0L;
        double aiResolutionRate = (double) ticketRepository.countAiResolvedTickets()
            / Math.max(1, totalTickets);

        return AnalyticsOverviewResponse.builder()
            .totalTickets(totalTickets)
            .openTickets(openTickets)
            .inProgressTickets(inProgressTickets)
            .resolvedToday(resolvedToday)
            .avgResolutionTimeHours(avgResolutionTimeHours)
            .aiResolutionRate(aiResolutionRate)
            .ticketsByStatus(ticketsByStatus)
            .ticketsByCategory(ticketsByCategory)
            .ticketsByPriority(ticketsByPriority)
            .totalAiTokensUsed(totalAiTokensUsed)
            .build();
    }

    private double calculateAverageResolutionTimeHours(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return 0.0;
        }

        double totalHours = 0.0;

        for (Object[] row : rows) {
            LocalDateTime createdAt = (LocalDateTime) row[0];
            LocalDateTime resolvedAt = (LocalDateTime) row[1];
            totalHours += ChronoUnit.MINUTES.between(createdAt, resolvedAt) / 60.0;
        }

        return totalHours / rows.size();
    }

    private Map<TicketStatus, Long> buildTicketsByStatus() {
        Map<TicketStatus, Long> ticketsByStatus = new EnumMap<>(TicketStatus.class);

        for (TicketStatus status : TicketStatus.values()) {
            ticketsByStatus.put(status, ticketRepository.countTotalByStatus(status));
        }

        return ticketsByStatus;
    }

    private Map<TicketCategory, Long> buildTicketsByCategory() {
        Map<TicketCategory, Long> ticketsByCategory = new EnumMap<>(TicketCategory.class);

        for (TicketCategory category : TicketCategory.values()) {
            // TODO: Replace placeholder with repository count method for ticket category.
            ticketsByCategory.put(category, 0L);
        }

        return ticketsByCategory;
    }

    private Map<TicketPriority, Long> buildTicketsByPriority() {
        Map<TicketPriority, Long> ticketsByPriority = new EnumMap<>(TicketPriority.class);

        for (TicketPriority priority : TicketPriority.values()) {
            // TODO: Replace placeholder with repository count method for ticket priority.
            ticketsByPriority.put(priority, 0L);
        }

        return ticketsByPriority;
    }
}
