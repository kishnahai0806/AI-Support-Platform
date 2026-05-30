package com.krish.supportapi.repository;

import com.krish.supportapi.domain.entity.AiResponseAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AiResponseAuditRepository extends JpaRepository<AiResponseAudit, UUID> {

    List<AiResponseAudit> findByTicketId(UUID ticketId);

    @Query("SELECT SUM(a.totalTokens) FROM AiResponseAudit a")
    Long sumTotalTokens();

    @Query("SELECT a FROM AiResponseAudit a WHERE a.success = false")
    List<AiResponseAudit> findFailedAudits();
}
