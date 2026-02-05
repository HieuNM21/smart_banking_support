package com.example.smart_banking_support.controller;

import com.example.smart_banking_support.dto.TicketReplyDTO;
import com.example.smart_banking_support.dto.TicketRequestDTO;
import com.example.smart_banking_support.dto.TicketResponseDTO;
import com.example.smart_banking_support.entity.Ticket;
import com.example.smart_banking_support.entity.TicketActivity;
import com.example.smart_banking_support.entity.TicketComment;
import com.example.smart_banking_support.enums.TicketChannel;
import com.example.smart_banking_support.repository.TicketActivityRepository;
import com.example.smart_banking_support.repository.TicketCommentRepository;
import com.example.smart_banking_support.repository.TicketRepository;
import com.example.smart_banking_support.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketRepository ticketRepository;
    private final TicketActivityRepository activityRepository;
    private final TicketCommentRepository commentRepository;

    // ========================================================================
    // 1. CLIENT / PUBLIC API
    // ========================================================================

    @PostMapping("/public/tickets")
    public ResponseEntity<TicketResponseDTO> createPublicTicket(@Valid @RequestBody TicketRequestDTO request) {
        Ticket ticket = ticketService.createTicket(request, null, TicketChannel.WEB_FORM);
        return ResponseEntity.ok(TicketResponseDTO.fromEntity(ticket));
    }

    @PostMapping("/client/tickets")
    public ResponseEntity<TicketResponseDTO> createClientTicket(
            @Valid @RequestBody TicketRequestDTO request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String ssoId = jwt.getSubject();
        Ticket ticket = ticketService.createTicket(request, ssoId, TicketChannel.MOBILE_APP);
        return ResponseEntity.ok(TicketResponseDTO.fromEntity(ticket));
    }

    @GetMapping("/public/tickets/{ticketCode}")
    public ResponseEntity<TicketResponseDTO> getPublicTicket(@PathVariable String ticketCode) {
        TicketResponseDTO response = ticketService.getTicketByCode(ticketCode);
        return ResponseEntity.ok(response);
    }

    // API lấy toàn bộ ticket (cho Dashboard cũ/F5)
    @GetMapping("/public/tickets")
    public ResponseEntity<List<TicketResponseDTO>> getAllTickets() {
        List<Ticket> tickets = ticketRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<TicketResponseDTO> response = tickets.stream()
                .map(TicketResponseDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    // ========================================================================
    // 2. AGENT API (NGHIỆP VỤ MỚI)
    // ========================================================================

    // Lấy chi tiết ticket (để hiển thị ở cột phải Dashboard Split View)
    @GetMapping("/agent/tickets/{id}")
    public ResponseEntity<TicketResponseDTO> getTicketDetailForAgent(@PathVariable Long id) {
        TicketResponseDTO response = ticketService.getTicketDetail(id);
        return ResponseEntity.ok(response);
    }

    // API Reply Ticket
    @PostMapping("/agent/tickets/{id}/reply")
    public ResponseEntity<?> replyTicket(
            @PathVariable Long id,
            @RequestBody @Valid TicketReplyDTO request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // Lấy User ID từ Token (Giả định Agent đã login)
        // Nếu bạn đang test không có token, có thể tạm hardcode ssoId trong service để test
        String agentSsoId = jwt.getSubject();
        System.out.println("DEBUG: Agent replying with SSO ID: " + agentSsoId);

        ticketService.replyToTicket(id, agentSsoId, request);
        return ResponseEntity.ok("Đã gửi phản hồi thành công.");
    }

    // API Lấy lịch sử hội thoại (Chat history)
    @GetMapping("/agent/tickets/{id}/comments")
    public ResponseEntity<List<TicketComment>> getTicketComments(@PathVariable Long id) {
        List<TicketComment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(id);
        return ResponseEntity.ok(comments);
    }

    // API Lấy lịch sử hoạt động (Audit Log)
    @GetMapping("/agent/tickets/{id}/activities")
    public ResponseEntity<List<TicketActivity>> getTicketActivities(@PathVariable Long id) {
        List<TicketActivity> activities = activityRepository.findByTicketIdOrderByCreatedAtDesc(id);
        return ResponseEntity.ok(activities);
    }

    // API Reprocess (Admin)
    @PostMapping("/admin/tickets/reprocess")
    public ResponseEntity<?> reprocessStuckTickets() {
        int count = ticketService.reprocessStuckTickets();
        return ResponseEntity.ok("Đã đẩy lại " + count + " ticket vào hàng đợi xử lý.");
    }
}