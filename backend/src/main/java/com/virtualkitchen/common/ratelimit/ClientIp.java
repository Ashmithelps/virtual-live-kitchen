package com.virtualkitchen.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/** The address a request is attributed to for rate limiting. */
public final class ClientIp {

    public static final String CLIENT_IP_HEADER = "X-Client-IP";
    public static final String PROXY_SECRET_HEADER = "X-Proxy-Secret";
    private static final Pattern IP = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private ClientIp() {
    }

    /**
     * {@code X-Client-IP} when the request proves it comes from our own web server (the Next.js route handlers and
     * server components run on Vercel, so their TCP address is Vercel's, shared by every user); otherwise the peer
     * address, which Tomcat has already taken from Caddy's X-Forwarded-For (server.forward-headers-strategy).
     */
    public static String of(HttpServletRequest request, String trustedProxySecret) {
        if (trustedProxySecret != null && !trustedProxySecret.isBlank()) {
            String secret = request.getHeader(PROXY_SECRET_HEADER);
            String claimed = request.getHeader(CLIENT_IP_HEADER);
            if (secret != null && claimed != null && IP.matcher(claimed.strip()).matches()
                    && MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), trustedProxySecret.getBytes(StandardCharsets.UTF_8))) {
                return claimed.strip();
            }
        }
        return request.getRemoteAddr();
    }
}
