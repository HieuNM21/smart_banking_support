package com.example.smart_banking_support.dto;

import com.example.smart_banking_support.entity.Ticket;
import com.example.smart_banking_support.entity.TicketAIInsight;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponseDTO {
    // Thông tin cơ bản
    private Long id;
    private String ticketCode;
    private String subject;
    private String description;
    private String status;
    private String priority;
    private String channel;
    private LocalDateTime createdAt;
    private LocalDateTime slaDueAt;

    // Thông tin AI (Sẽ null nếu AI chưa chạy xong)
    private AIResultDTO aiAnalysis;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AIResultDTO {
        private String sentiment; // Tích cực/Tiêu cực
        private String summary;   // Tóm tắt
        private String tags;      // Tags gợi ý (Dạng chuỗi JSON, ví dụ: "[\"SCAM\"]")
        private String status;    // DONE/PENDING
    }

    /**
     * Hàm tiện ích Map từ Entity -> DTO
     * Chỉ cần truyền Ticket, nó sẽ tự móc nối sang bảng AI Insight (nếu có quan hệ JPA)
     */
    public static TicketResponseDTO fromEntity(Ticket ticket) {
        TicketResponseDTO dto = new TicketResponseDTO();
        dto.setId(ticket.getId());
        dto.setTicketCode(ticket.getTicketCode());
        dto.setSubject(ticket.getSubject());
        dto.setDescription(ticket.getDescription());

        // Handle Null Safety cho Enum
        dto.setStatus(ticket.getStatus() != null ? ticket.getStatus().name() : "OPEN");
        dto.setPriority(ticket.getPriority() != null ? ticket.getPriority().name() : "LOW");
        dto.setChannel(ticket.getChannel() != null ? ticket.getChannel().name() : "WEB_FORM");

        dto.setCreatedAt(ticket.getCreatedAt());
        dto.setSlaDueAt(ticket.getSlaDueAt());

        // Lấy thông tin AI
        // LƯU Ý: Đảm bảo trong Entity Ticket bạn đã có field:
        // @OneToOne(mappedBy = "ticket") private TicketAIInsight aiInsight;
        TicketAIInsight insight = ticket.getAiInsight();

        if (insight != null) {
            AIResultDTO ai = new AIResultDTO();
            ai.setStatus(insight.getAiStatus() != null ? insight.getAiStatus().name() : "PENDING");
            ai.setSentiment(insight.getSentiment());
            ai.setSummary(insight.getSummary());
            ai.setTags(insight.getSuggestedTags());
            dto.setAiAnalysis(ai);
        } else {
            // Nếu chưa có Insight, trả về null để Frontend hiện trạng thái "Đang phân tích..."
            dto.setAiAnalysis(null);
        }

        return dto;
    }
}