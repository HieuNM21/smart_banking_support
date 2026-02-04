package com.example.smart_banking_support.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class TicketRequestDTO {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String subject;

    @NotBlank(message = "Nội dung không được để trống")
    private String description;

    // Dành cho khách vãng lai (Optional)
    private String guestName;
    private String guestEmail;
    private String guestPhone;
}