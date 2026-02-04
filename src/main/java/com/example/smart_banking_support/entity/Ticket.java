package com.example.smart_banking_support.entity;

import com.example.smart_banking_support.enums.TicketChannel;
import com.example.smart_banking_support.enums.TicketPriority;
import com.example.smart_banking_support.enums.TicketStatus;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Data
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_code", unique = true, nullable = false, updatable = false)
    private String ticketCode;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private TicketStatus status = TicketStatus.OPEN;

    @Enumerated(EnumType.STRING)
    private TicketPriority priority = TicketPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketChannel channel;

    // --- Liên kết User (Có thể null nếu là khách vãng lai) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_agent_id")
    private User assignedAgent;

    // --- Thông tin Khách vãng lai (Guest) ---
    @Column(name = "guest_name")
    private String guestName;

    @Column(name = "guest_email")
    private String guestEmail;

    @Column(name = "guest_phone")
    private String guestPhone;

    // --- SLA Tracking ---
    @Column(name = "sla_due_at")
    private LocalDateTime slaDueAt;

    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    // --- Audit ---
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Tự động sinh mã Ticket Code trước khi lưu
    @PrePersist
    protected void onCreate() {
        if (this.ticketCode == null) {
            // Logic đơn giản: SBSC + Random UUID (Bạn có thể sửa logic phức tạp hơn sau này)
            this.ticketCode = "SBSC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }
}