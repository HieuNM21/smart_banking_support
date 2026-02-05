package com.example.smart_banking_support.repository;

import com.example.smart_banking_support.entity.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {

    // Lấy hội thoại của 1 ticket
    // Sắp xếp: Cũ nhất lên đầu (ASC) -> Giống giao diện chat Messenger/Zalo
    List<TicketComment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}