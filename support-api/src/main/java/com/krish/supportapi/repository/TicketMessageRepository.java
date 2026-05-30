package com.krish.supportapi.repository;

import com.krish.supportapi.domain.entity.TicketMessage;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {

    Page<TicketMessage> findByTicketId(UUID ticketId, Pageable pageable);

    long countByTicketId(UUID ticketId);
}
