package com.example.smart_banking_support.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class TicketReplyDTO {
    @NotBlank(message = "Nội dung phản hồi không được để trống")
    private String content;

    private String status; // Trạng thái mới (VD: RESOLVED, IN_PROGRESS)

    private boolean isInternal = false; // Có phải ghi chú nội bộ không? (Khách sẽ không thấy)
}