package com.rmitbot.webcontroller.config;

import com.rmitbot.webcontroller.websocket.RobotWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket configuration for robot control
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RobotWebSocketHandler robotWebSocketHandler;

    public WebSocketConfig(RobotWebSocketHandler robotWebSocketHandler) {
        this.robotWebSocketHandler = robotWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Register WebSocket endpoint
        registry.addHandler(robotWebSocketHandler, "/ws/robot")
                .setAllowedOrigins("*");  // Allow all origins (adjust for production)
    }
}