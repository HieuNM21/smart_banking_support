package com.example.smart_banking_support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketNotificationDTO {
    private Long ticketId;
    private String ticketCode;
    private String priority;   // HIGH, MEDIUM, LOW
    private String sentiment;  // NEGATIVE, POSITIVE
    private String summary;    // Tóm tắt ngắn
    private String type;       // "UPDATE_TABLE" hoặc "SHOW_ALERT"
    private String tags;
}