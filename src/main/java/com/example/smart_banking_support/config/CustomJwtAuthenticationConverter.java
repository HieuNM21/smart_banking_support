package com.example.smart_banking_support.config;

import com.example.smart_banking_support.entity.User;
import com.example.smart_banking_support.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // 1. Lấy ssoId (username) từ Token
        String ssoId = jwt.getSubject();

        List<GrantedAuthority> authorities = new ArrayList<>();

        // 2. Tra cứu Role từ Database Local
        Optional<User> userOptional = userRepository.findBySsoId(ssoId);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getRole() != null) {
                // Giả sử Role trong DB tên là "INTERNAL_AGENT", Spring cần "ROLE_INTERNAL_AGENT"
                String roleName = "ROLE_" + user.getRole().getName().toUpperCase();
                authorities.add(new SimpleGrantedAuthority(roleName));
            }
        } else {
            // Trường hợp User có Token hợp lệ nhưng chưa có trong DB
            // Có thể gán quyền mặc định hoặc để trống (vô danh)
            authorities.add(new SimpleGrantedAuthority("ROLE_ANONYMOUS"));
        }

        // 3. Trả về Token đã được gắn quyền từ DB
        return new JwtAuthenticationToken(jwt, authorities, ssoId);
    }
}