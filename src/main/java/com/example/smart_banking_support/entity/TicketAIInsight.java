package com.example.smart_banking_support.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_ai_insights")
@Data
public class TicketAIInsight {

    @Id
    @Column(name = "ticket_id")
    private Long ticketId; // Key chính cũng là Foreign Key trỏ tới Ticket

    @OneToOne
    @MapsId
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status")
    private AIStatus aiStatus; // Cần tạo Enum này: PENDING, DONE, FAILED

    @Column(name = "sentiment")
    private String sentiment; // POSITIVE, NEGATIVE

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "suggested_tags", columnDefinition = "JSON")
    private String suggestedTags; // Lưu JSON dạng String đơn giản cho Demo

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt;

    public enum AIStatus {
        PENDING, DONE, FAILED
    }
}