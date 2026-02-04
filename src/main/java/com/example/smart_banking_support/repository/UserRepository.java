package com.example.smart_banking_support.repository;

import com.example.smart_banking_support.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Tìm user đang hoạt động (chưa xóa) theo số điện thoại
    Optional<User> findByPhoneNumberAndDeletedAtIsNull(String phoneNumber);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    // Tìm theo SSO ID (cho luồng Login)
    Optional<User> findBySsoId(String ssoId);
}