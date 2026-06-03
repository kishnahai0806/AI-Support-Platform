package com.krish.supportapi.repository;

import com.krish.supportapi.domain.entity.Ticket;
import com.krish.supportapi.domain.enums.TicketStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    @EntityGraph(attributePaths = {"customer", "assignedAgent"})
    Page<Ticket> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "assignedAgent"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "assignedAgent"})
    Page<Ticket> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Ticket> findByAssignedAgentId(UUID agentId, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "assignedAgent"})
    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    Page<Ticket> findByCustomerIdAndStatus(UUID customerId, TicketStatus status, Pageable pageable);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = :status")
    long countTotalByStatus(@Param("status") TicketStatus status);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.resolvedAt >= :todayMidnight")
    long countResolvedToday(@Param("todayMidnight") LocalDateTime todayMidnight);

    @Query("SELECT t.createdAt, t.resolvedAt FROM Ticket t WHERE t.resolvedAt IS NOT NULL")
    List<Object[]> findCreatedAndResolvedTimestamps();

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.aiEscalated = false AND t.status = com.krish.supportapi.domain.enums.TicketStatus.RESOLVED")
    long countAiResolvedTickets();

    @Query(value = "SELECT nextval('public.ticket_number_seq')", nativeQuery = true)
    long nextTicketNumber();

    @Query("SELECT t.category, COUNT(t) FROM Ticket t GROUP BY t.category")
    List<Object[]> countByCategory();

    @Query("SELECT t.priority, COUNT(t) FROM Ticket t GROUP BY t.priority")
    List<Object[]> countByPriority();
}
