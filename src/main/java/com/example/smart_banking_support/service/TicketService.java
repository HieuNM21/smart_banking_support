package com.example.smart_banking_support.service;

import com.example.smart_banking_support.config.RabbitConfig;
import com.example.smart_banking_support.dto.TicketReplyDTO;
import com.example.smart_banking_support.dto.TicketRequestDTO;
import com.example.smart_banking_support.dto.TicketResponseDTO;
import com.example.smart_banking_support.entity.Ticket;
import com.example.smart_banking_support.entity.TicketActivity;
import com.example.smart_banking_support.entity.User;
import com.example.smart_banking_support.enums.TicketChannel;
import com.example.smart_banking_support.enums.TicketPriority;
import com.example.smart_banking_support.enums.TicketStatus;
import com.example.smart_banking_support.repository.TicketActivityRepository;
import com.example.smart_banking_support.repository.TicketRepository;
import com.example.smart_banking_support.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final TicketActivityRepository activityRepository;

    // Helper ghi log
    private void logActivity(Ticket ticket, User actor, String action, String details) {
        TicketActivity activity = new TicketActivity();
        activity.setTicket(ticket);
        activity.setActor(actor);
        activity.setAction(action);
        activity.setDetails(details);
        activityRepository.save(activity);
    }
    // Không cần Inject TicketAIInsightRepository nữa vì Hibernate tự lo
    // private final TicketAIInsightRepository insightRepository;

    @Transactional
    public Ticket createTicket(TicketRequestDTO request, String ssoId, TicketChannel channel) {
        Ticket ticket = new Ticket();
        ticket.setSubject(request.getSubject());
        ticket.setDescription(request.getDescription());
        ticket.setChannel(channel);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(TicketPriority.MEDIUM);

        // --- LOGIC 1: ĐỊNH DANH USER ---
        if (ssoId != null) {
            User user = userRepository.findBySsoId(ssoId)
                    .orElseThrow(() -> new RuntimeException("User not found with SSO ID: " + ssoId));
            ticket.setCustomer(user);
        } else {
            if (request.getGuestPhone() != null) {
                Optional<User> existingUser = userRepository.findByPhoneNumberAndDeletedAtIsNull(request.getGuestPhone());
                if (existingUser.isPresent()) {
                    ticket.setCustomer(existingUser.get());
                    log.info("Mapped guest phone {} to existing user ID {}", request.getGuestPhone(), existingUser.get().getId());
                } else {
                    ticket.setGuestName(request.getGuestName());
                    ticket.setGuestEmail(request.getGuestEmail());
                    ticket.setGuestPhone(request.getGuestPhone());
                }
            }
        }

        // --- LOGIC 2: SLA ---
        ticket.setSlaDueAt(LocalDateTime.now().plusHours(24));

        Ticket savedTicket = ticketRepository.save(ticket);
        logActivity(savedTicket, null, "CREATE", "Hệ thống tiếp nhận yêu cầu mới");

        // --- LOGIC 3: RABBITMQ ---
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(
                        RabbitConfig.TICKET_EXCHANGE,
                        RabbitConfig.TICKET_ROUTING_KEY,
                        savedTicket.getId()
                );
                log.info("✅ Transaction Committed. Sent ticket ID {} to RabbitMQ", savedTicket.getId());
            }
        });

        return savedTicket;
    }

    // Hàm lấy chi tiết Ticket
    @Transactional(readOnly = true) // Thêm cái này để đảm bảo Hibernate Session còn mở để load AI Insight
    public TicketResponseDTO getTicketDetail(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        // CHỈNH SỬA: Chỉ truyền 1 tham số ticket.
        // DTO sẽ tự gọi ticket.getAiInsight() để lấy dữ liệu.
        return TicketResponseDTO.fromEntity(ticket);
    }

    // Hàm tìm bằng Code (Để tra cứu public)
    @Transactional(readOnly = true)
    public TicketResponseDTO getTicketByCode(String ticketCode) {
        Ticket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new RuntimeException("Ticket code invalid"));

        // CHỈNH SỬA: Tương tự, chỉ truyền 1 tham số
        return TicketResponseDTO.fromEntity(ticket);
    }

    // Hàm quét và xử lý lại các ticket bị kẹt
    @Transactional
    public int reprocessStuckTickets() {
        List<Ticket> stuckTickets = ticketRepository.findTicketsMissingAnalysis();
        int count = 0;

        for (Ticket ticket : stuckTickets) {
            // Chỉ xử lý ticket KHÔNG ở trạng thái DONE/CANCELLED (tùy logic của bạn)
            if (ticket.getStatus() != TicketStatus.DONE && ticket.getStatus() != TicketStatus.CANCELLED
                    && ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {

                // Đẩy lại ID vào RabbitMQ để Consumer gắp ra xử lý như mới
                rabbitTemplate.convertAndSend(
                        RabbitConfig.TICKET_EXCHANGE,
                        RabbitConfig.TICKET_ROUTING_KEY,
                        ticket.getId()
                );
                count++;
                log.info("♻️ Re-queued stuck ticket ID: {}", ticket.getId());
            }
        }
        return count;
    }

    // --- LOGIC MỚI: AUTO ASSIGNMENT (GỌI SAU KHI AI PHÂN TÍCH XONG) ---
    @Transactional
    public void autoAssignTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        // Chỉ assign nếu chưa có ai nhận
        if (ticket.getAssignedAgent() != null) return;

        // Tìm Agent phù hợp nhất (Online + Ít việc nhất)
        List<User> availableAgents = userRepository.findAvailableAgents();

        if (!availableAgents.isEmpty()) {
            User bestAgent = availableAgents.get(0); // Lấy người đầu tiên (đã sort ở query)

            // Gán việc
            ticket.setAssignedAgent(bestAgent);
            ticket.setStatus(TicketStatus.IN_PROGRESS); // Chuyển trạng thái
            ticketRepository.save(ticket);

            // Cập nhật Load cho Agent
            bestAgent.setCurrentLoad(bestAgent.getCurrentLoad() + 1);
            bestAgent.setLastAssignedAt(LocalDateTime.now());
            userRepository.save(bestAgent);

            logActivity(ticket, null, "AUTO_ASSIGN", "Hệ thống tự động phân công cho Agent: " + bestAgent.getFullName());
            log.info("🤖 Auto-assigned Ticket {} to Agent {}", ticket.getTicketCode(), bestAgent.getEmail());
        } else {
            log.warn("⚠️ Không tìm thấy Agent nào Online để giao Ticket {}", ticket.getTicketCode());
            logActivity(ticket, null, "QUEUE_PENDING", "Chưa có nhân viên trực tuyến. Ticket vào hàng đợi.");
        }
    }

    @Transactional
    public void replyToTicket(Long ticketId, String agentSsoId, TicketReplyDTO request) {
        // 1. Tìm Ticket
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        // 2. Tìm Agent đang thao tác (Từ Token SSO)
        // Lưu ý: Nếu chưa có SSO thật, bạn có thể hardcode tìm theo Email hoặc ID để test
        User agent = userRepository.findBySsoId(agentSsoId)
                .orElseThrow(() -> new RuntimeException("Agent not found. SSO ID '" + agentSsoId + "' chưa được đồng bộ vào hệ thống."));

        // 3. Tạo Comment (Hội thoại)
        com.example.smart_banking_support.entity.TicketComment comment = new com.example.smart_banking_support.entity.TicketComment();
        comment.setTicket(ticket);
        comment.setUser(agent); // Người trả lời là Agent
        comment.setContent(request.getContent());
        comment.setInternal(request.isInternal());
        // commentRepository cần được Inject ở đầu class
        // (Nếu chưa inject, hãy thêm: private final TicketCommentRepository commentRepository;)
        // commentRepository.save(comment); -> Bạn cần thêm Repository này vào service nhé

        // Tạm thời nếu chưa inject commentRepository, ta có thể lưu thông qua List (nếu mapping OneToMany)
        // hoặc tốt nhất bạn hãy thêm private final TicketCommentRepository commentRepository; vào đầu file.

        // 4. Cập nhật trạng thái Ticket (nếu Agent có chọn)
        if (request.getStatus() != null && !request.getStatus().isEmpty()) {
            try {
                TicketStatus newStatus = TicketStatus.valueOf(request.getStatus());
                if (ticket.getStatus() != newStatus) {
                    String oldStatus = ticket.getStatus().name();
                    ticket.setStatus(newStatus);
                    ticketRepository.save(ticket); // Lưu thay đổi status

                    // Ghi log thay đổi trạng thái
                    logActivity(ticket, agent, "UPDATE_STATUS",
                            "Đổi trạng thái từ " + oldStatus + " sang " + newStatus);
                }
            } catch (IllegalArgumentException e) {
                log.warn("Trạng thái không hợp lệ: {}", request.getStatus());
            }
        }

        // 5. Ghi log hành động trả lời
        String actionType = request.isInternal() ? "INTERNAL_NOTE" : "REPLY_CUSTOMER";
        logActivity(ticket, agent, actionType, "Đã trả lời: " + request.getContent());

        // TODO: Bắn WebSocket/Email thông báo cho khách hàng ở đây
    }
}