package com.example.smart_banking_support.repository;

import com.example.smart_banking_support.entity.TicketActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketActivityRepository extends JpaRepository<TicketActivity, Long> {

    // Lấy toàn bộ lịch sử của 1 ticket
    // Sắp xếp: Mới nhất lên đầu (DESC) để hiển thị Timeline ngược
    // Hoặc sửa thành Asc nếu muốn hiển thị xuôi dòng thời gian
    List<TicketActivity> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}