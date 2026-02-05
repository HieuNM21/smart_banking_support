package com.example.smart_banking_support.entity;

import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_activities")
@Data
public class TicketActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    // Người thực hiện hành động (Có thể null nếu là SYSTEM/AI)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false)
    private String action; // VD: ASSIGN, UPDATE_STATUS, COMMENT, AI_ANALYSIS

    @Column(columnDefinition = "TEXT")
    private String details; // Nội dung chi tiết (VD: Đổi trạng thái từ OPEN -> IN_PROGRESS)

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}