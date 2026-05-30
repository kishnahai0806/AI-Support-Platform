package com.krish.aiprocessor.repository;

import com.krish.aiprocessor.domain.entity.AiResponseAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiResponseAuditRepository extends JpaRepository<AiResponseAudit, UUID> {
}
