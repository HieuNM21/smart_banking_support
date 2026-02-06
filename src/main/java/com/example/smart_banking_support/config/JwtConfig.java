package com.example.smart_banking_support.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.cert.X509Certificate;

@Configuration
public class JwtConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Bean
    public JwtDecoder jwtDecoder() {
        // 1. Cấu hình Bypass SSL (Do dùng HTTPS tự ký)
        RestTemplate restTemplate = new RestTemplate(new TrustAllClientHttpRequestFactory());

        // 2. Build JwtDecoder
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .restOperations(restTemplate) // Áp dụng bypass SSL
                .jwtProcessorCustomizer(processor -> {
                    // 3. FIX LỖI: JOSE header typ (type) at+jwt not allowed
                    // Cấu hình cho phép chấp nhận cả 'at+jwt', 'JWT' và 'null'
                    processor.setJWSTypeVerifier(
                            new DefaultJOSEObjectTypeVerifier<>(
                                    new JOSEObjectType("at+jwt"), // Chuẩn mới của WSO2 (RFC 9068)
                                    new JOSEObjectType("JWT"),    // Chuẩn cũ
                                    null                          // Không có header typ
                            )
                    );
                })
                .build();
    }

    /**
     * Inner Class: Factory để tạo kết nối HTTP bỏ qua kiểm tra SSL
     */
    private static class TrustAllClientHttpRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            if (connection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) connection).setHostnameVerifier((hostname, session) -> true);
                ((HttpsURLConnection) connection).setSSLSocketFactory(trustAllSslSocketFactory());
            }
            super.prepareConnection(connection, httpMethod);
        }

        private SSLSocketFactory trustAllSslSocketFactory() {
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            public X509Certificate[] getAcceptedIssuers() { return null; }
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                        }
                };
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                return sc.getSocketFactory();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create trust-all SSL factory", e);
            }
        }
    }
}