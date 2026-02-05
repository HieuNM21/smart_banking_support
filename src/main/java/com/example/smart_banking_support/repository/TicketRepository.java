package com.example.smart_banking_support.repository;

import com.example.smart_banking_support.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByTicketCode(String ticketCode);

    // Tìm tất cả ticket chưa có AI Insight
    // Yêu cầu: Trong Entity Ticket phải có dòng @OneToOne(mappedBy="ticket") private TicketAIInsight aiInsight;
    @Query(value = "SELECT * FROM tickets t WHERE t.id NOT IN (SELECT ticket_id FROM ticket_ai_insights)", nativeQuery = true)
    List<Ticket> findTicketsMissingAnalysis();
}