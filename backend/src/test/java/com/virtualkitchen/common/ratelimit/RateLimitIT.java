package com.virtualkitchen.common.ratelimit;

import com.virtualkitchen.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.trusted-proxy-secret=test-proxy-secret-0123456789abcdef",
        "RATE_LIMIT_AUTH_PER_MINUTE=2",
        "RATE_LIMIT_PUBLIC_READ_PER_MINUTE=3",
})
class RateLimitIT extends IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private static RequestPostProcessor from(String ip) {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    private static MockHttpServletRequestBuilder viaWebServer(MockHttpServletRequestBuilder b, String clientIp) {
        return b.with(from("76.76.21.21")).header(ClientIp.PROXY_SECRET_HEADER, "test-proxy-secret-0123456789abcdef")
                .header(ClientIp.CLIENT_IP_HEADER, clientIp);
    }

    @Test
    void publicReadsAreLimitedPerClientWithTheStandardErrorShape() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/restaurants").param("city", "Bengaluru").with(from("203.0.113.10"))).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/restaurants").param("city", "Bengaluru").with(from("203.0.113.10")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.path").value("/api/restaurants"))
                .andExpect(jsonPath("$.timestamp").exists());
        // A different client is unaffected; signed-in and unmatched routes aren't limited here.
        mockMvc.perform(get("/api/restaurants").param("city", "Bengaluru").with(from("203.0.113.11"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/health").with(from("203.0.113.10"))).andExpect(status().isOk());
    }

    @Test
    void theWebServerSpeaksForEachVisitorOnlyWithTheSecret() throws Exception {
        String body = "{\"phone\":\"+919999900001\",\"password\":\"wrong-password\"}";
        // Two visitors behind the same Vercel address each get their own budget.
        for (String visitor : new String[]{"198.51.100.1", "198.51.100.2"}) {
            for (int i = 0; i < 2; i++) {
                mockMvc.perform(viaWebServer(post("/api/auth/login"), visitor).contentType("application/json").content(body))
                        .andExpect(status().isUnauthorized());
            }
            mockMvc.perform(viaWebServer(post("/api/auth/login"), visitor).contentType("application/json").content(body))
                    .andExpect(status().isTooManyRequests());
        }
        // A forged X-Client-IP without the secret counts against the sender's own address.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/register").with(from("192.0.2.50")).header(ClientIp.CLIENT_IP_HEADER, "198.51.100." + (10 + i))
                    .contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/auth/register").with(from("192.0.2.50")).header(ClientIp.CLIENT_IP_HEADER, "198.51.100.99")
                .contentType("application/json").content("{}")).andExpect(status().isTooManyRequests());
    }

    @Test
    void recordingConsentTextIsPublic() throws Exception {
        mockMvc.perform(get("/api/public/legal/recording-consent").with(from("203.0.113.40")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.text").isNotEmpty())
                .andExpect(jsonPath("$.textHash").isNotEmpty());
    }
}
