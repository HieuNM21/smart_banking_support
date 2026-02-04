package com.example.smart_banking_support.consumer;

import com.example.smart_banking_support.config.RabbitConfig;
import com.example.smart_banking_support.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketConsumer {

    private final AIService aiService;
    // Không cần TicketRepository ở đây nữa

    @RabbitListener(queues = RabbitConfig.TICKET_QUEUE)
    public void consumeTicketCreatedEvent(Long ticketId) {
        log.info("📩 Nhận được tin nhắn từ RabbitMQ: Ticket ID = {}", ticketId);

        // Chuyển thẳng ID vào Service để xử lý trọn gói
        aiService.analyzeTicket(ticketId);
    }
}