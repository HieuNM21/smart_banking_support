package com.example.smart_banking_support.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors().and().csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .antMatchers("/api/public/**", "/actuator/**").permitAll()
                .antMatchers("/ws/**").permitAll()
                .antMatchers("/api/admin/**").authenticated()
                .antMatchers("/api/agent/**").authenticated()
                .anyRequest().authenticated()
                .and()
                .oauth2ResourceServer().jwt();

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Cho phép Frontend (localhost:5173) truy cập
        // Bạn có thể thêm cả IP máy công ty nếu cần test chéo: "http://10.2.22.54:5173"
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173", "http://10.2.22.54:5173"));

        // Cho phép tất cả các method: GET, POST, PUT, DELETE, OPTIONS
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Cho phép mọi Header (Authorization, Content-Type...)
        configuration.setAllowedHeaders(Collections.singletonList("*"));

        // Cho phép gửi credentials (nếu sau này cần cookie)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
