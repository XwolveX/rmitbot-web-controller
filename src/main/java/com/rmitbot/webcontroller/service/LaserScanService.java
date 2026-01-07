package com.rmitbot.webcontroller.service;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to handle laser scan data from RPLidar
 * Subscribes to /scan topic and broadcasts to web clients
 */
@Service
public class LaserScanService {

    private static final Logger logger = LoggerFactory.getLogger(LaserScanService.class);

    private final ROSBridgeService rosBridgeService;
    private final Map<String, WebSocketSession> webSocketSessions = new ConcurrentHashMap<>();

    private boolean isSubscribed = false;
    private JsonObject latestScanData = null;

    public LaserScanService(ROSBridgeService rosBridgeService) {
        this.rosBridgeService = rosBridgeService;
    }

    /**
     * Initialize laser scan subscription after ROSBridge is ready
     */
    @PostConstruct
    public void init() {
        // Wait for ROSBridge to be ready, then subscribe
        new Thread(() -> {
            int attempts = 0;
            while (!rosBridgeService.isConnected() && attempts < 20) {
                try {
                    Thread.sleep(1000);
                    attempts++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            if (rosBridgeService.isConnected()) {
                subscribeToLaserScan();
            } else {
                logger.warn("Could not subscribe to laser scan - ROSBridge not connected");
            }
        }).start();
    }

    /**
     * Subscribe to /scan topic
     */
    public void subscribeToLaserScan() {
        if (isSubscribed) {
            logger.info("Already subscribed to /scan");
            return;
        }

        try {
            rosBridgeService.subscribeToTopic(
                    "/scan",
                    "sensor_msgs/msg/LaserScan",
                    this::handleLaserScanMessage
            );
            isSubscribed = true;
            logger.info("Successfully subscribed to /scan topic");
        } catch (Exception e) {
            logger.error("Failed to subscribe to /scan topic", e);
        }
    }

    /**
     * Handle incoming laser scan messages
     */
    private void handleLaserScanMessage(JsonObject message) {
        try {
            // Store latest scan data
            latestScanData = message;

            // Extract scan data for processing
            JsonObject msg = message.getAsJsonObject("msg");

            // Log periodically (every 100 messages)
            if (Math.random() < 0.01) {
                int numRanges = msg.getAsJsonArray("ranges").size();
                logger.debug("Received laser scan with {} points", numRanges);
            }

            // Broadcast to all connected web clients
            broadcastToWebClients(message);

        } catch (Exception e) {
            logger.error("Error processing laser scan message", e);
        }
    }

    /**
     * Register a WebSocket session to receive laser scan updates
     */
    public void registerWebSocketSession(String sessionId, WebSocketSession session) {
        webSocketSessions.put(sessionId, session);
        logger.info("Registered WebSocket session for laser scan: {}", sessionId);

        // Send latest scan data immediately if available
        if (latestScanData != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(latestScanData.toString()));
            } catch (IOException e) {
                logger.error("Error sending initial scan data", e);
            }
        }
    }

    /**
     * Unregister a WebSocket session
     */
    public void unregisterWebSocketSession(String sessionId) {
        webSocketSessions.remove(sessionId);
        logger.info("Unregistered WebSocket session: {}", sessionId);
    }

    /**
     * Broadcast laser scan data to all connected web clients
     */
    private void broadcastToWebClients(JsonObject scanData) {
        List<String> deadSessions = new ArrayList<>();

        webSocketSessions.forEach((sessionId, session) -> {
            if (session.isOpen()) {
                try {
                    // Create simplified message for web clients
                    JsonObject webMessage = new JsonObject();
                    webMessage.addProperty("type", "laser_scan");
                    webMessage.add("data", scanData.getAsJsonObject("msg"));

                    session.sendMessage(new TextMessage(webMessage.toString()));
                } catch (IOException e) {
                    logger.warn("Failed to send to session {}: {}", sessionId, e.getMessage());
                    deadSessions.add(sessionId);
                }
            } else {
                deadSessions.add(sessionId);
            }
        });

        // Clean up dead sessions
        deadSessions.forEach(webSocketSessions::remove);
    }

    /**
     * Get latest laser scan data
     */
    public JsonObject getLatestScanData() {
        return latestScanData;
    }

    /**
     * Check if subscribed to laser scan
     */
    public boolean isSubscribed() {
        return isSubscribed;
    }

    /**
     * Get number of active web clients
     */
    public int getActiveClientCount() {
        return webSocketSessions.size();
    }
}