package com.example.smart_banking_support.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.smart_banking_support.entity.User;
import com.example.smart_banking_support.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Optional;

@Service
public class LoginService {

    @Autowired
    private UserRepository userRepository;

    @Value("${wso2.client-id}")
    private String clientId;

    @Value("${wso2.client-secret}")
    private String clientSecret;

    @Value("${wso2.token-uri}")
    private String tokenUri;

    @Value("${app.backend-callback-url}")
    private String redirectUri;

    // --- PHẦN 1: HÀM TẮT SSL TOÀN CỤC (MẠNH TAY NHẤT) ---
    private void disableSslVerification() {
        try {
            // Tạo TrustManager tin tưởng tất cả
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
            };

            // Cài đặt TrustManager này vào SSL Context mặc định của Java
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Tắt luôn kiểm tra Hostname (bỏ qua lỗi domain không khớp)
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- PHẦN 2: LOGIC TRAO ĐỔI TOKEN ---
    public JsonNode exchangeCodeForToken(String authCode) {
        // GỌI HÀM TẮT SSL NGAY ĐẦU TIÊN
        disableSslVerification();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "authorization_code");
        map.add("code", authCode);
        map.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            // Lúc này chỉ cần new RestTemplate() bình thường
            // Vì disableSslVerification() đã can thiệp vào tầng core của Java
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response = restTemplate.postForEntity(tokenUri, request, String.class);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(response.getBody());
        } catch (Exception e) {
            e.printStackTrace(); // In lỗi ra console
            throw new RuntimeException("Lỗi gọi WSO2: " + e.getMessage());
        }
    }

    // --- PHẦN 3: CÁC HÀM PHỤ TRỢ KHÁC (GIỮ NGUYÊN) -- -
    public String extractUsernameFromIdToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode payload = mapper.readTree(payloadJson);

            if (payload.has("preferred_username")) {
                return payload.get("preferred_username").asText();
            }
            return payload.get("sub").asText();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi parse Token: " + e.getMessage());
        }
    }

    public User validateUserExistence(String username) {
        Optional<User> userOpt = userRepository.findBySsoId(username);
        return userOpt.orElse(null);
    }
}