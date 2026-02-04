package com.example.smart_banking_support.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    // API này ai cũng vào được (Khách vãng lai)
    @GetMapping("/public/hello")
    public String publicHello() {
        return "Xin chào! Đây là API công khai. Bạn không cần đăng nhập.";
    }

    // API này cần Token (Khách hàng / Nhân viên)
    @GetMapping("/client/profile")
    public String privateProfile() {
        return "Xin chào VIP! Bạn đã đăng nhập thành công.";
    }
}