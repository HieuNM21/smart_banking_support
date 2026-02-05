package com.example.smart_banking_support.repository;

import com.example.smart_banking_support.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhoneNumberAndDeletedAtIsNull(String phoneNumber);
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    Optional<User> findBySsoId(String ssoId);

    // --- LOGIC TÌM AGENT ĐỂ GIAO VIỆC ---
    // Tìm Agent đang Online, sắp xếp theo khối lượng việc (ít -> nhiều), sau đó đến thời gian nhận việc (cũ -> mới)
    // Limit 1 sẽ được xử lý ở Service hoặc dùng Pageable
    @Query("SELECT u FROM User u WHERE u.role.name = 'INTERNAL_AGENT' AND u.isOnline = true ORDER BY u.currentLoad ASC, u.lastAssignedAt ASC")
    List<User> findAvailableAgents();
}