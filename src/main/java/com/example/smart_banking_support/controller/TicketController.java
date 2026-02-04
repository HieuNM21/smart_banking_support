package com.example.smart_banking_support.controller;

import com.example.smart_banking_support.dto.TicketRequestDTO;
import com.example.smart_banking_support.dto.TicketResponseDTO;
import com.example.smart_banking_support.entity.Ticket;
import com.example.smart_banking_support.enums.TicketChannel;
import com.example.smart_banking_support.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    // 1. API cho Khách vãng lai (Không cần Token)
    // POST /api/public/tickets
    @PostMapping("/public/tickets")
    public ResponseEntity<?> createPublicTicket(@Valid @RequestBody TicketRequestDTO request) {
        Ticket ticket = ticketService.createTicket(request, null, TicketChannel.WEB_FORM);
        return ResponseEntity.ok("Ticket created successfully. Code: " + ticket.getTicketCode());
    }

    // 2. API cho Khách hàng đã đăng nhập (Cần Token)
    // POST /api/client/tickets
    @PostMapping("/client/tickets")
    public ResponseEntity<?> createClientTicket(
            @Valid @RequestBody TicketRequestDTO request,
            @AuthenticationPrincipal Jwt jwt // Lấy thông tin user từ Token
    ) {
        // Lấy SSO ID (sub) từ Token JWT
        String ssoId = jwt.getSubject();

        Ticket ticket = ticketService.createTicket(request, ssoId, TicketChannel.MOBILE_APP);
        return ResponseEntity.ok("Ticket created successfully. Code: " + ticket.getTicketCode());
    }

    // 3. API Tra cứu ticket công khai (Dành cho khách vãng lai kiểm tra tiến độ)
    // GET /api/public/tickets/{ticketCode}
    @GetMapping("/public/tickets/{ticketCode}")
    public ResponseEntity<?> getPublicTicket(@PathVariable String ticketCode) {
        TicketResponseDTO response = ticketService.getTicketByCode(ticketCode);
        return ResponseEntity.ok(response);
    }

    // 4. API Xem chi tiết (Dành cho nội bộ/App)
    // GET /api/tickets/{id}
    @GetMapping("/tickets/{id}")
    public ResponseEntity<?> getTicketById(@PathVariable Long id) {
        // TODO: Sau này cần check quyền xem user có sở hữu ticket này không
        TicketResponseDTO response = ticketService.getTicketDetail(id);
        return ResponseEntity.ok(response);
    }
}