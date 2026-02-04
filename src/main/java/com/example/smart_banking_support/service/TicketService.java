package com.example.smart_banking_support.service;

import com.example.smart_banking_support.config.RabbitConfig;
import com.example.smart_banking_support.dto.TicketRequestDTO;
import com.example.smart_banking_support.dto.TicketResponseDTO;
import com.example.smart_banking_support.entity.Ticket;
import com.example.smart_banking_support.entity.TicketAIInsight;
import com.example.smart_banking_support.entity.User;
import com.example.smart_banking_support.enums.TicketChannel;
import com.example.smart_banking_support.enums.TicketPriority;
import com.example.smart_banking_support.enums.TicketStatus;
import com.example.smart_banking_support.repository.TicketAIInsightRepository;
import com.example.smart_banking_support.repository.TicketRepository;
import com.example.smart_banking_support.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;

    private final TicketAIInsightRepository insightRepository;

    @Transactional
    public Ticket createTicket(TicketRequestDTO request, String ssoId, TicketChannel channel) {
        Ticket ticket = new Ticket();
        ticket.setSubject(request.getSubject());
        ticket.setDescription(request.getDescription());
        ticket.setChannel(channel);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(TicketPriority.MEDIUM); // Mặc định, AI sẽ update sau

        // --- LOGIC 1: ĐỊNH DANH USER (Identity Resolution) ---
        if (ssoId != null) {
            // Case A: Khách hàng đã đăng nhập (Mobile App)
            User user = userRepository.findBySsoId(ssoId)
                    .orElseThrow(() -> new RuntimeException("User not found with SSO ID: " + ssoId));
            ticket.setCustomer(user);
        } else {
            // Case B: Khách vãng lai (Web Form) -> Tìm thử xem có phải khách cũ không?
            if (request.getGuestPhone() != null) {
                Optional<User> existingUser = userRepository.findByPhoneNumberAndDeletedAtIsNull(request.getGuestPhone());
                if (existingUser.isPresent()) {
                    // WOW! Tìm thấy khách cũ -> Map luôn vào hồ sơ
                    ticket.setCustomer(existingUser.get());
                    log.info("Mapped guest phone {} to existing user ID {}", request.getGuestPhone(), existingUser.get().getId());
                } else {
                    // Không tìm thấy -> Lưu thông tin guest
                    ticket.setGuestName(request.getGuestName());
                    ticket.setGuestEmail(request.getGuestEmail());
                    ticket.setGuestPhone(request.getGuestPhone());
                }
            }
        }

        // --- LOGIC 2: SLA (Tính hạn chót xử lý) ---
        // Ví dụ: Medium priority được xử lý trong 24h
        ticket.setSlaDueAt(LocalDateTime.now().plusHours(24));

        // Lưu vào DB
        Ticket savedTicket = ticketRepository.save(ticket);

        // --- LOGIC 3: ASYNC PROCESSING (RabbitMQ) ---
        // Gửi ID của ticket vào Queue để AI xử lý sau
        // Chúng ta gửi ID để Consumer tự query lại DB lấy data mới nhất
        rabbitTemplate.convertAndSend(
                RabbitConfig.TICKET_EXCHANGE,
                RabbitConfig.TICKET_ROUTING_KEY,
                savedTicket.getId()
        );
        log.info("Sent ticket ID {} to RabbitMQ", savedTicket.getId());

        return savedTicket;
    }

    // Hàm lấy chi tiết Ticket
    public TicketResponseDTO getTicketDetail(Long ticketId) {
        // 1. Tìm Ticket
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        // 2. Tìm kết quả AI (Nếu có)
        TicketAIInsight insight = insightRepository.findById(ticketId).orElse(null);

        // 3. Gộp lại thành DTO
        return TicketResponseDTO.fromEntity(ticket, insight);
    }

    // Hàm tìm bằng Code (Để tra cứu public)
    public TicketResponseDTO getTicketByCode(String ticketCode) {
        // Bạn cần thêm method findByTicketCode vào TicketRepository trước nhé
        Ticket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new RuntimeException("Ticket code invalid"));

        TicketAIInsight insight = insightRepository.findById(ticket.getId()).orElse(null);
        return TicketResponseDTO.fromEntity(ticket, insight);
    }
}