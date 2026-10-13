package com.virtualkitchen.kitchen.device;

import com.virtualkitchen.auth.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates station phones by {@code X-Device-Token}. Deliberately scoped to the device
 * routes only, so a device token is never accepted as a user anywhere else (/api/me etc.).
 */
public class StationDeviceAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Device-Token";
    private static final List<String> DEVICE_PATHS = List.of("/api/station-device/", "/api/clips/");

    private final StationDeviceService devices;

    public StationDeviceAuthenticationFilter(StationDeviceService devices) {
        this.devices = devices;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return DEVICE_PATHS.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token != null && !token.isBlank()) {
            devices.authenticate(token.strip()).ifPresentOrElse(device -> {
                var authentication = new UsernamePasswordAuthenticationToken(device, null,
                        List.of(new SimpleGrantedAuthority(StationDevice.AUTHORITY)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }, () -> request.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE,
                    "Device token is invalid (the station may have been paired with another phone)"));
        }
        chain.doFilter(request, response);
    }
}
