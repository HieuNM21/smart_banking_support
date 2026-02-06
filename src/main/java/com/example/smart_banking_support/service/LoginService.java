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

    @Value("${wso2.token-uri}") // https://wso2is.com/oauth2/token
    private String tokenUri;

    @Value("${app.backend-callback-url}") // http://localhost:8080/api/auth/callback
    private String redirectUri;

    private final RestTemplate restTemplate = new RestTemplate();

    // 1. Hàm đổi Authorization Code lấy Access Token & ID Token
    public JsonNode exchangeCodeForToken(String authCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret); // Gửi Client ID/Secret

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "authorization_code");
        map.add("code", authCode);
        map.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUri, request, String.class);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi gọi WSO2 lấy token: " + e.getMessage());
        }
    }

    // 2. Hàm lấy Username từ ID Token (JWT)
    public String extractUsernameFromIdToken(String idToken) {
        try {
            // JWT có 3 phần: Header.Payload.Signature. Ta chỉ cần decode phần Payload (giữa)
            String[] parts = idToken.split("\\.");
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));

            ObjectMapper mapper = new ObjectMapper();
            JsonNode payload = mapper.readTree(payloadJson);

            // Lấy field 'sub' (subject) hoặc 'preferred_username' tùy cấu hình WSO2
            return payload.get("sub").asText();
        } catch (Exception e) {
            throw new RuntimeException("Token không hợp lệ");
        }
    }

    // 3. Hàm Logic Gatekeeper: Kiểm tra user có trong DB không
    public User validateUserExistence(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        // Nếu không có -> Trả về null (hoặc throw exception tùy cách xử lý)
        return userOpt.orElse(null);
    }
}
