package com.example.smart_banking_support.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.smart_banking_support.entity.User;
import com.example.smart_banking_support.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private LoginService ssoService;

    @Value("${wso2.auth-uri}")
    private String authUri;

    @Value("${wso2.client-id}")
    private String clientId;

    @Value("${app.backend-callback-url}")
    private String redirectUri;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // API 1: Bắt đầu đăng nhập -> Redirect sang WSO2
    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        // Scope 'openid' bắt buộc để lấy ID Token
        String wso2Url = String.format("%s?response_type=code&client_id=%s&redirect_uri=%s&scope=openid profile",
                authUri, clientId, redirectUri);
        response.sendRedirect(wso2Url);
    }

    // API 2: Nhận Callback từ WSO2
    @GetMapping("/callback")
    public void callback(@RequestParam("code") String code, HttpServletResponse response) throws IOException {

        // B1: Đổi code lấy token
        JsonNode tokenResponse = ssoService.exchangeCodeForToken(code);
        String idToken = tokenResponse.get("id_token").asText();

        // B2: Lấy username từ token
        String username = ssoService.extractUsernameFromIdToken(idToken);

        // B3: Kiểm tra trong DB (QUAN TRỌNG: Không tạo mới)
        User user = ssoService.validateUserExistence(username);

        if (user == null) {
            // Trường hợp: User có trên WSO2 nhưng không có trong DB SBSC
            response.sendRedirect(frontendUrl + "/error?message=UserNotFoundInSystem");
            return;
        }

        // B4: Tạo Cookie HttpOnly để định danh Session (An toàn hơn lưu LocalStorage ở FE)
        // Trong thực tế bạn có thể dùng JWT của riêng hệ thống SBSC hoặc SessionId
        Cookie authCookie = new Cookie("SBSC_SESSION", username); // Demo lưu username, thực tế nên lưu Session ID mã hóa
        authCookie.setHttpOnly(true); // FE không đọc được cookie này (chống XSS)
        authCookie.setPath("/");
        authCookie.setMaxAge(3600); // 1 giờ
        response.addCookie(authCookie);

        // B5: Phân luồng Role & Redirect về FE
        String roleName = "";
        // 2. Kiểm tra null để tránh lỗi NullPointerException nếu user chưa được gán role
        if (user.getRole() != null) {
            // SỬA LỖI TẠI ĐÂY: Gọi .getName() để lấy chuỗi String ra
            roleName = user.getRole().getName();
        }

        // 3. Truyền chuỗi String vừa lấy được vào hàm
        String targetPage = determineRedirectUrl(roleName);
        response.sendRedirect(frontendUrl + targetPage);
    }

    // Hàm phụ trợ phân luồng
    private String determineRedirectUrl(String roleName) {
        // Luôn kiểm tra null hoặc rỗng trước
        if (roleName == null || roleName.isEmpty()) {
            return "/home";
        }

        // Dùng equalsIgnoreCase để không lo viết hoa/thường (Admin vs ADMIN)
        if ("ADMIN".equalsIgnoreCase(roleName)) {
            return "/admin/dashboard";
        } else if ("INTERNAL_AGENT".equalsIgnoreCase(roleName)) {
            return "/agent/workspace"; // hoặc URL tương ứng của bạn
        } else if ("CUSTOMER".equalsIgnoreCase(roleName)) {
            return "/portal/home";
        }

        return "/home";
    }
}
