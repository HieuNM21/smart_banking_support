package com.example.smart_banking_support.service;

import com.example.smart_banking_support.dto.TicketNotificationDTO;
import com.example.smart_banking_support.dto.gemini.GeminiRequest;
import com.example.smart_banking_support.dto.gemini.GeminiResponse;
import com.example.smart_banking_support.entity.Ticket;
import com.example.smart_banking_support.entity.TicketAIInsight;
import com.example.smart_banking_support.enums.TicketPriority;
import com.example.smart_banking_support.repository.TicketAIInsightRepository;
import com.example.smart_banking_support.repository.TicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate; // 1. Import cái này
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIService {

    private final TicketAIInsightRepository insightRepository;
    private final TicketRepository ticketRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final TicketService ticketService;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private static final String PROXY_HOST = "proxybsh.bkav.com";
    private static final int PROXY_PORT = 3128;

    public void analyzeTicket(Long ticketId) {
        // XÓA DÒNG NÀY: Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        // XÓA DÒNG NÀY: if (ticket == null) return; -> Im lặng là chết

        // LOGIC MỚI: Nếu không tìm thấy ticket, ném lỗi để log in ra dòng ERROR (Giúp debug dễ hơn)
        // Vì ta đã dùng TransactionSynchronization ở TicketService, nên tỉ lệ null cực thấp.
        // Tuy nhiên, ta vẫn cần query lại trong transaction bên dưới.

        log.info("🤖 AI đang chuẩn bị phân tích Ticket ID: {}", ticketId);

        try {
            // Lấy thông tin ticket (Chỉ để tạo Prompt, chưa cần transaction write)
            Ticket ticketForPrompt = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new RuntimeException("Ticket ID " + ticketId + " không tồn tại (Lỗi đồng bộ DB)"));

            log.info("🤖 AI đang gọi Gemini qua Proxy {}: {}", PROXY_HOST, ticketForPrompt.getTicketCode());

            String prompt = createPrompt(ticketForPrompt);

            // Cấu hình Proxy & Timeout
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            factory.setProxy(proxy);
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(10000);

            RestTemplate restTemplate = new RestTemplate(factory);
            String finalUrl = apiUrl + apiKey;

            // Gọi API Gemini
            GeminiResponse response = restTemplate.postForObject(finalUrl, GeminiRequest.of(prompt), GeminiResponse.class);

            if (response != null && response.getText() != null) {
                String rawJson = response.getText();
                String cleanJson = rawJson.replace("```json", "").replace("```", "").trim();
                JsonNode rootNode = objectMapper.readTree(cleanJson);

                // Dùng transactionTemplate để lưu DB và bắn Socket
                transactionTemplate.execute(status -> {
                    saveInsightAndEscalate(ticketId, rootNode);
                    ticketService.autoAssignTicket(ticketId);
                    log.info("✅ Finished AI & Assignment flow.");
                    return null;
                });
            }

        } catch (Exception e) {
            log.error("❌ Lỗi xử lý AI cho Ticket {}: {}", ticketId, e.getMessage());
            // Có thể ném exception tiếp để RabbitMQ retry nếu muốn
        }
    }

    // Bỏ @Transactional ở đây đi (vì đã được bọc bởi TransactionTemplate ở trên rồi)
    public void saveInsightAndEscalate(Long ticketId, JsonNode rootNode) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        String sentiment = rootNode.path("sentiment").asText();
        String summary = rootNode.path("summary").asText();
        String tagsJson = rootNode.path("tags").toString();

        // 1. Lưu AI Insight
        TicketAIInsight insight = new TicketAIInsight();
        insight.setTicket(ticket);
        insight.setAiStatus(TicketAIInsight.AIStatus.DONE);
        insight.setAnalyzedAt(LocalDateTime.now());
        insight.setSentiment(sentiment);
        insight.setSummary(summary);
        insight.setSuggestedTags(tagsJson);
        insightRepository.save(insight);

        // 2. Logic Auto-Escalation (Cập nhật Priority)
        boolean isUrgent = false;
        if ("NEGATIVE".equalsIgnoreCase(sentiment)) {
            ticket.setPriority(TicketPriority.HIGH);
            isUrgent = true;
        }
        String upperTags = tagsJson.toUpperCase();
        if (upperTags.contains("FRAUD") || upperTags.contains("SCAM") || upperTags.contains("LOST_CARD")) {
            ticket.setPriority(TicketPriority.CRITICAL);
            isUrgent = true;
        }

        if (isUrgent) {
            ticketRepository.save(ticket);
            log.warn("🔥 Ticket {} đã được đẩy lên mức độ ưu tiên: {}", ticket.getTicketCode(), ticket.getPriority());
        }

        // ==================================================================
        // 3. LOGIC WEBSOCKET (ĐÃ SỬA LẠI)
        // ==================================================================

        // Tạo DTO thông báo
        TicketNotificationDTO notification = TicketNotificationDTO.builder()
                .ticketId(ticket.getId())
                .ticketCode(ticket.getTicketCode())
                .priority(ticket.getPriority().name()) // Lấy Priority mới nhất
                .sentiment(sentiment)
                .summary(summary)
                .tags(tagsJson)
                .type("UPDATE_TABLE")
                .build();

        // A. LUÔN LUÔN bắn tin update table (Bất kể Low hay High)
        // Để dòng ticket mới hiện ra ngay lập tức trên Dashboard
        messagingTemplate.convertAndSend("/topic/admin/updates", notification);
        log.info("📡 Đã bắn socket UPDATE_TABLE cho ticket: {}", ticket.getTicketCode());

        // B. CHỈ bắn tin Alert (Popup) nếu Khẩn cấp
        if (isUrgent) {
            notification.setType("SHOW_ALERT"); // Đổi loại message
            messagingTemplate.convertAndSend("/topic/admin/alerts", notification);
            log.info("🚨 Đã bắn socket SHOW_ALERT cho ticket: {}", ticket.getTicketCode());
        }

        log.info("✅ AI Gemini phân tích xong: Sentiment={}, Tags={}", sentiment, tagsJson);
    }

    private String createPrompt(Ticket ticket) {
        return "Bạn là trợ lý AI cho hệ thống CSKH ngân hàng. Hãy phân tích yêu cầu sau:\n" +
                "Tiêu đề: " + ticket.getSubject() + "\n" +
                "Nội dung: " + ticket.getDescription() + "\n\n" +
                "Yêu cầu output: Trả về CHỈ MỘT chuỗi JSON duy nhất (không markdown) theo định dạng sau:\n" +
                "{\n" +
                "  \"sentiment\": \"POSITIVE\" hoặc \"NEGATIVE\" hoặc \"NEUTRAL\",\n" +
                "  \"summary\": \"Tóm tắt ngắn gọn nội dung trong 1 câu tiếng Việt\",\n" +
                "  \"tags\": [\"tag1\", \"tag2\"] (Gợi ý 3 tags tiếng Anh liên quan ví dụ: CARD_ISSUE, FRAUD, TRANSACTION_ERROR)\n" +
                "}";
    }
}