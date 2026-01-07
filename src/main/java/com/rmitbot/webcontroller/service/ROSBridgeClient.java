package com.rmitbot.webcontroller.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * WebSocket client for connecting to ROS Bridge
 * Handles low-level WebSocket communication with ROSBridge server
 */
public class ROSBridgeClient extends WebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(ROSBridgeClient.class);
    private final Gson gson = new Gson();
    
    // Callbacks for different message types
    private final Map<String, Consumer<JsonObject>> topicCallbacks = new ConcurrentHashMap<>();
    private Consumer<Boolean> connectionCallback;
    
    public ROSBridgeClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        logger.info("Connected to ROSBridge at {}", getURI());
        if (connectionCallback != null) {
            connectionCallback.accept(true);
        }
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            
            // Check if this is a topic message
            if (json.has("topic")) {
                String topic = json.get("topic").getAsString();
                Consumer<JsonObject> callback = topicCallbacks.get(topic);
                if (callback != null) {
                    callback.accept(json);
                }
            }
            
            // Log other message types
            if (json.has("op")) {
                String op = json.get("op").getAsString();
                logger.trace("Received message with op: {}", op);
            }
            
        } catch (Exception e) {
            logger.error("Error processing ROSBridge message: {}", message, e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.warn("ROSBridge connection closed: {} - {}", code, reason);
        if (connectionCallback != null) {
            connectionCallback.accept(false);
        }
    }

    @Override
    public void onError(Exception ex) {
        logger.error("ROSBridge WebSocket error", ex);
    }

    /**
     * Publish a message to a ROS topic
     * 
     * @param topic The ROS topic name (e.g., "/cmd_vel")
     * @param msgType The message type (e.g., "geometry_msgs/msg/TwistStamped")
     * @param message The message content as a Map
     */
    public void publish(String topic, String msgType, Map<String, Object> message) {
        JsonObject publishMsg = new JsonObject();
        publishMsg.addProperty("op", "publish");
        publishMsg.addProperty("topic", topic);
        publishMsg.addProperty("type", msgType);
        publishMsg.add("msg", gson.toJsonTree(message));
        
        String jsonString = gson.toJson(publishMsg);
        logger.debug("Publishing to {}: {}", topic, jsonString);
        send(jsonString);
    }

    /**
     * Subscribe to a ROS topic
     * 
     * @param topic The ROS topic name
     * @param msgType The message type
     * @param callback Callback function to handle received messages
     */
    public void subscribe(String topic, String msgType, Consumer<JsonObject> callback) {
        // Store callback
        topicCallbacks.put(topic, callback);
        
        // Send subscribe message
        JsonObject subscribeMsg = new JsonObject();
        subscribeMsg.addProperty("op", "subscribe");
        subscribeMsg.addProperty("topic", topic);
        subscribeMsg.addProperty("type", msgType);
        
        String jsonString = gson.toJson(subscribeMsg);
        logger.info("Subscribing to {}: {}", topic, jsonString);
        send(jsonString);
    }

    /**
     * Unsubscribe from a ROS topic
     * 
     * @param topic The ROS topic name
     */
    public void unsubscribe(String topic) {
        topicCallbacks.remove(topic);
        
        JsonObject unsubscribeMsg = new JsonObject();
        unsubscribeMsg.addProperty("op", "unsubscribe");
        unsubscribeMsg.addProperty("topic", topic);
        
        send(gson.toJson(unsubscribeMsg));
        logger.info("Unsubscribed from {}", topic);
    }

    /**
     * Advertise a topic (prepare to publish)
     * 
     * @param topic The ROS topic name
     * @param msgType The message type
     */
    public void advertise(String topic, String msgType) {
        JsonObject advertiseMsg = new JsonObject();
        advertiseMsg.addProperty("op", "advertise");
        advertiseMsg.addProperty("topic", topic);
        advertiseMsg.addProperty("type", msgType);
        
        send(gson.toJson(advertiseMsg));
        logger.info("Advertised topic: {}", topic);
    }

    /**
     * Unadvertise a topic
     * 
     * @param topic The ROS topic name
     */
    public void unadvertise(String topic) {
        JsonObject unadvertiseMsg = new JsonObject();
        unadvertiseMsg.addProperty("op", "unadvertise");
        unadvertiseMsg.addProperty("topic", topic);
        
        send(gson.toJson(unadvertiseMsg));
        logger.info("Unadvertised topic: {}", topic);
    }

    /**
     * Set callback for connection status changes
     * 
     * @param callback Callback function (true = connected, false = disconnected)
     */
    public void setConnectionCallback(Consumer<Boolean> callback) {
        this.connectionCallback = callback;
    }

    /**
     * Check if connected to ROSBridge
     */
    public boolean isConnected() {
        return isOpen();
    }
}
