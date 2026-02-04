package com.example.smart_banking_support.service;

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
    private final TransactionTemplate transactionTemplate; // 2. Inject TransactionTemplate

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private static final String PROXY_HOST = "proxybsh.bkav.com";
    private static final int PROXY_PORT = 3128;

    public void analyzeTicket(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) return;

        log.info("🤖 AI đang phân tích ticket (Qua Proxy {}): {}", PROXY_HOST, ticket.getTicketCode());

        try {
            String prompt = createPrompt(ticket);

            // Cấu hình Proxy & Timeout
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            factory.setProxy(proxy);
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(10000);

            RestTemplate restTemplate = new RestTemplate(factory);
            String finalUrl = apiUrl + apiKey;

            // Gọi API Gemini (Nằm ngoài Transaction để không giữ DB connection)
            GeminiResponse response = restTemplate.postForObject(finalUrl, GeminiRequest.of(prompt), GeminiResponse.class);

            if (response != null && response.getText() != null) {
                String rawJson = response.getText();
                String cleanJson = rawJson.replace("```json", "").replace("```", "").trim();
                JsonNode rootNode = objectMapper.readTree(cleanJson);

                // 3. Dùng transactionTemplate để BẮT BUỘC chạy trong transaction
                transactionTemplate.execute(status -> {
                    saveInsightAndEscalate(ticketId, rootNode);
                    return null;
                });
            }

        } catch (Exception e) {
            log.error("❌ Lỗi gọi Gemini API: {}", e.getMessage());
        }
    }

    // Bỏ @Transactional ở đây đi (vì đã được bọc bởi TransactionTemplate ở trên rồi)
    public void saveInsightAndEscalate(Long ticketId, JsonNode rootNode) {
        // Tìm lại ticket trong transaction này -> Managed Entity (Sống)
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();

        String sentiment = rootNode.path("sentiment").asText();
        String summary = rootNode.path("summary").asText();
        String tagsJson = rootNode.path("tags").toString();

        TicketAIInsight insight = new TicketAIInsight();
        insight.setTicket(ticket);
        insight.setAiStatus(TicketAIInsight.AIStatus.DONE);
        insight.setAnalyzedAt(LocalDateTime.now());
        insight.setSentiment(sentiment);
        insight.setSummary(summary);
        insight.setSuggestedTags(tagsJson);

        insightRepository.save(insight);

        // Auto-Escalation Logic
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