package com.virtualkitchen.auth.security;

import com.virtualkitchen.auth.token.InvalidTokenException;
import com.virtualkitchen.auth.token.JwtService;
import com.virtualkitchen.auth.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <access token>}. A missing or bad
 * token leaves the request anonymous; protected endpoints then answer 401 via
 * {@link RestAuthenticationEntryPoint}, which reports the reason stored here.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Why authentication failed, for the 401 body (also set by the station device filter). */
    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".error";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            try {
                AuthenticatedUser principal = jwtService.verify(header.substring(BEARER_PREFIX.length()).strip());
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authoritiesOf(principal));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidTokenException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Only ACTIVE users receive their role authority. A PENDING_APPROVAL owner is authenticated
     * (can call /api/me) but fails every hasRole(...) check until an admin approves them.
     */
    private static List<GrantedAuthority> authoritiesOf(AuthenticatedUser principal) {
        return principal.status() == UserStatus.ACTIVE
                ? List.of(new SimpleGrantedAuthority(principal.role().authority()))
                : List.of();
    }
}
