package com.example.smart_banking_support.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sso_id", unique = true)
    private String ssoId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(nullable = false)
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "full_name")
    private String fullName;

    // --- CÁC TRƯỜNG MỚI CHO LOGIC PHÂN CÔNG (V4) ---

    @Column(name = "is_online")
    private boolean isOnline = false; // Trạng thái làm việc

    @Column(name = "current_load")
    private int currentLoad = 0; // Số ticket đang xử lý

    @Column(name = "last_assigned_at")
    private LocalDateTime lastAssignedAt; // Thời điểm nhận việc gần nhất (để chia đều Round-Robin)

    // ------------------------------------------------

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}