package com.virtualkitchen.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * STOMP over SockJS at {@code /ws}, with Spring's in-memory broker for {@code /topic/**}.
 * The in-memory broker only reaches clients connected to this instance; running several backend
 * instances needs a broker relay (e.g. RabbitMQ) instead.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor authInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(StompAuthInterceptor authInterceptor, @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::strip).toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS()
                // Stateless API: no JSESSIONID cookie for SockJS sticky sessions.
                .setSessionCookieNeeded(false);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                // Server and client heartbeats every 10 s, so dead tablets/networks are noticed quickly.
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(brokerHeartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Bean
    public ThreadPoolTaskScheduler brokerHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
