package com.rmitbot.webcontroller.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rmitbot.webcontroller.model.RobotCommand;
import com.rmitbot.webcontroller.service.ROSBridgeService;
import com.rmitbot.webcontroller.service.LaserScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for robot control and laser scan streaming
 * UPDATED: Added laser scan support
 */
@Component
public class RobotWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(RobotWebSocketHandler.class);
    private final Gson gson = new Gson();

    private final ROSBridgeService rosBridgeService;
    private final LaserScanService laserScanService;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private double currentSpeedMultiplier = 1.0;

    public RobotWebSocketHandler(ROSBridgeService rosBridgeService, LaserScanService laserScanService) {
        this.rosBridgeService = rosBridgeService;
        this.laserScanService = laserScanService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        logger.info("WebSocket connection established: {}", session.getId());

        // Send welcome message
        JsonObject welcome = new JsonObject();
        welcome.addProperty("type", "connected");
        welcome.addProperty("message", "Connected to robot controller (ROSBridge)");
        welcome.addProperty("speedMultiplier", currentSpeedMultiplier);
        welcome.addProperty("rosBridgeConnected", rosBridgeService.isConnected());
        session.sendMessage(new TextMessage(gson.toJson(welcome)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        logger.debug("Received message: {}", payload);

        try {
            JsonObject json = gson.fromJson(payload, JsonObject.class);
            String type = json.get("type").getAsString();

            switch (type) {
                case "command":
                    handleCommandMessage(session, json);
                    break;
                case "speed":
                    handleSpeedMessage(session, json);
                    break;
                case "stop":
                    handleStopMessage(session);
                    break;
                case "ping":
                    handlePingMessage(session);
                    break;
                case "status":
                    handleStatusMessage(session);
                    break;
                case "subscribe_laser_scan":
                    handleLaserScanSubscription(session);
                    break;
                case "unsubscribe_laser_scan":
                    handleLaserScanUnsubscription(session);
                    break;
                default:
                    logger.warn("Unknown message type: {}", type);
            }

        } catch (Exception e) {
            logger.error("Error processing message", e);
            sendError(session, "Error processing command: " + e.getMessage());
        }
    }

    /**
     * Handle movement command
     */
    private void handleCommandMessage(WebSocketSession session, JsonObject json) throws IOException {
        String action = json.get("action").getAsString();

        // Create and send command via ROSBridge
        RobotCommand command = RobotCommand.fromAction(action, currentSpeedMultiplier);
        rosBridgeService.sendCommand(command);

        // Send acknowledgment
        JsonObject response = new JsonObject();
        response.addProperty("type", "command_ack");
        response.addProperty("action", action);
        response.addProperty("linearX", command.getLinearX());
        response.addProperty("linearY", command.getLinearY());
        response.addProperty("angularZ", command.getAngularZ());
        response.addProperty("timestamp", System.currentTimeMillis());
        session.sendMessage(new TextMessage(gson.toJson(response)));
    }

    /**
     * Handle speed change
     */
    private void handleSpeedMessage(WebSocketSession session, JsonObject json) throws IOException {
        String action = json.get("action").getAsString();
        double speedStep = 0.2;
        double maxSpeed = 3.0;
        double minSpeed = 0.2;

        if ("increase".equals(action)) {
            currentSpeedMultiplier = Math.min(currentSpeedMultiplier + speedStep, maxSpeed);
        } else if ("decrease".equals(action)) {
            currentSpeedMultiplier = Math.max(currentSpeedMultiplier - speedStep, minSpeed);
        } else if (json.has("value")) {
            currentSpeedMultiplier = json.get("value").getAsDouble();
            currentSpeedMultiplier = Math.max(minSpeed, Math.min(maxSpeed, currentSpeedMultiplier));
        }

        // Broadcast speed change to all sessions
        JsonObject response = new JsonObject();
        response.addProperty("type", "speed_update");
        response.addProperty("speedMultiplier", currentSpeedMultiplier);
        broadcastMessage(gson.toJson(response));
    }

    /**
     * Handle stop command
     */
    private void handleStopMessage(WebSocketSession session) throws IOException {
        rosBridgeService.sendStopCommand();

        JsonObject response = new JsonObject();
        response.addProperty("type", "stopped");
        response.addProperty("timestamp", System.currentTimeMillis());
        session.sendMessage(new TextMessage(gson.toJson(response)));
    }

    /**
     * Handle ping (keepalive)
     */
    private void handlePingMessage(WebSocketSession session) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("type", "pong");
        response.addProperty("timestamp", System.currentTimeMillis());
        session.sendMessage(new TextMessage(gson.toJson(response)));
    }

    /**
     * Handle status request
     */
    private void handleStatusMessage(WebSocketSession session) throws IOException {
        Map<String, Object> status = rosBridgeService.getConnectionStatus();
        
        JsonObject response = new JsonObject();
        response.addProperty("type", "status");
        response.add("rosbridge", gson.toJsonTree(status));
        response.addProperty("speedMultiplier", currentSpeedMultiplier);
        response.addProperty("timestamp", System.currentTimeMillis());
        
        session.sendMessage(new TextMessage(gson.toJson(response)));
    }

    /**
     * Handle laser scan subscription
     */
    private void handleLaserScanSubscription(WebSocketSession session) throws IOException {
        laserScanService.registerWebSocketSession(session.getId(), session);
        
        JsonObject response = new JsonObject();
        response.addProperty("type", "laser_scan_subscribed");
        response.addProperty("message", "Subscribed to laser scan updates");
        session.sendMessage(new TextMessage(gson.toJson(response)));
        
        logger.info("Session {} subscribed to laser scan", session.getId());
    }

    /**
     * Handle laser scan unsubscription
     */
    private void handleLaserScanUnsubscription(WebSocketSession session) throws IOException {
        laserScanService.unregisterWebSocketSession(session.getId());
        
        JsonObject response = new JsonObject();
        response.addProperty("type", "laser_scan_unsubscribed");
        response.addProperty("message", "Unsubscribed from laser scan updates");
        session.sendMessage(new TextMessage(gson.toJson(response)));
        
        logger.info("Session {} unsubscribed from laser scan", session.getId());
    }

    /**
     * Send error message
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            JsonObject error = new JsonObject();
            error.addProperty("type", "error");
            error.addProperty("message", errorMessage);
            error.addProperty("timestamp", System.currentTimeMillis());
            session.sendMessage(new TextMessage(gson.toJson(error)));
        } catch (IOException e) {
            logger.error("Error sending error message", e);
        }
    }

    /**
     * Broadcast message to all connected sessions
     */
    private void broadcastMessage(String message) {
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            } catch (IOException e) {
                logger.error("Error broadcasting message to session: {}", session.getId(), e);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // Unregister from laser scan
        laserScanService.unregisterWebSocketSession(session.getId());
        
        sessions.remove(session.getId());
        logger.info("WebSocket connection closed: {} - Status: {}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for session: {}", session.getId(), exception);
        laserScanService.unregisterWebSocketSession(session.getId());
        sessions.remove(session.getId());
    }
}
