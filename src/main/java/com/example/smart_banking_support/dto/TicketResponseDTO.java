package com.example.smart_banking_support.dto;

import com.example.smart_banking_support.entity.Ticket;
import com.example.smart_banking_support.entity.TicketAIInsight;
import lombok.Data;

import java.time.LocalDateTime;

@Data
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

    // Thông tin AI (Có thể null nếu chưa chạy xong)
    private AIResultDTO aiAnalysis;

    @Data
    public static class AIResultDTO {
        private String sentiment; // Tích cực/Tiêu cực
        private String summary;   // Tóm tắt
        private String tags;      // Tags gợi ý
        private String status;    // DONE/PENDING
    }

    // Hàm tiện ích để map từ Entity sang DTO
    public static TicketResponseDTO fromEntity(Ticket ticket, TicketAIInsight insight) {
        TicketResponseDTO dto = new TicketResponseDTO();
        dto.setId(ticket.getId());
        dto.setTicketCode(ticket.getTicketCode());
        dto.setSubject(ticket.getSubject());
        dto.setDescription(ticket.getDescription());
        dto.setStatus(ticket.getStatus().name());
        dto.setPriority(ticket.getPriority().name());
        dto.setChannel(ticket.getChannel().name());
        dto.setCreatedAt(ticket.getCreatedAt());
        dto.setSlaDueAt(ticket.getSlaDueAt());

        if (insight != null) {
            AIResultDTO ai = new AIResultDTO();
            ai.setStatus(insight.getAiStatus().name());
            ai.setSentiment(insight.getSentiment());
            ai.setSummary(insight.getSummary());
            ai.setTags(insight.getSuggestedTags());
            dto.setAiAnalysis(ai);
        }
        return dto;
    }
}