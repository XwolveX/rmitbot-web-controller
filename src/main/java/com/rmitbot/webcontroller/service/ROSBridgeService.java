package com.rmitbot.webcontroller.service;

import com.rmitbot.webcontroller.model.RobotCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for sending robot commands via ROSBridge WebSocket
 * Replaces SSH-based ROS2CommandService with real-time WebSocket communication
 *
 * Performance: ~50ms latency (vs 3-4s with SSH)
 */
@Service
public class ROSBridgeService {

    private static final Logger logger = LoggerFactory.getLogger(ROSBridgeService.class);

    @Value("${rosbridge.enabled:true}")
    private boolean rosBridgeEnabled;

    @Value("${rosbridge.uri:ws://192.168.0.3:9090}")
    private String rosBridgeUri;

    @Value("${ros2.topic.cmd_vel:/cmd_vel}")
    private String cmdVelTopic;

    @Value("${rosbridge.reconnect.attempts:5}")
    private int reconnectAttempts;

    @Value("${rosbridge.reconnect.delay:3000}")
    private int reconnectDelay;

    private ROSBridgeClient client;
    private boolean connected = false;
    private int reconnectCount = 0;
    private ScheduledExecutorService reconnectScheduler;

    /**
     * Initialize ROSBridge connection on startup
     */
    @PostConstruct
    public void init() {
        if (!rosBridgeEnabled) {
            logger.info("ROSBridge is disabled in configuration");
            return;
        }

        reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
        connect();
    }

    /**
     * Connect to ROSBridge server
     */
    private void connect() {
        try {
            logger.info("Connecting to ROSBridge at {}", rosBridgeUri);

            URI serverUri = new URI(rosBridgeUri);
            client = new ROSBridgeClient(serverUri);

            // Setup connection callback
            client.setConnectionCallback(isConnected -> {
                connected = isConnected;
                if (isConnected) {
                    reconnectCount = 0;
                    onConnected();
                } else {
                    onDisconnected();
                }
            });

            // Connect
            boolean success = client.connectBlocking(5, TimeUnit.SECONDS);

            if (success) {
                logger.info("Successfully connected to ROSBridge");
            } else {
                logger.error("Failed to connect to ROSBridge");
                scheduleReconnect();
            }

        } catch (Exception e) {
            logger.error("Error connecting to ROSBridge", e);
            scheduleReconnect();
        }
    }

    /**
     * Called when connection is established
     */
    private void onConnected() {
        logger.info("ROSBridge connection established");

        // Advertise cmd_vel topic
        client.advertise(cmdVelTopic, "geometry_msgs/msg/TwistStamped");

        // Optional: Subscribe to odometry for monitoring
        // client.subscribe("/odom", "nav_msgs/msg/Odometry", this::handleOdometry);
    }

    /**
     * Called when connection is lost
     */
    private void onDisconnected() {
        logger.warn("ROSBridge connection lost");
        scheduleReconnect();
    }

    /**
     * Schedule reconnection attempt
     */
    private void scheduleReconnect() {
        if (reconnectCount >= reconnectAttempts) {
            logger.error("Max reconnection attempts reached ({})", reconnectAttempts);
            return;
        }

        reconnectCount++;
        logger.info("Scheduling reconnection attempt {}/{} in {}ms",
                reconnectCount, reconnectAttempts, reconnectDelay);

        reconnectScheduler.schedule(() -> {
            if (!connected) {
                connect();
            }
        }, reconnectDelay, TimeUnit.MILLISECONDS);
    }

    /**
     * Send velocity command to robot
     * This is the main method called by WebSocket/REST controllers
     *
     * @param command The robot command containing linear and angular velocities
     */
    public void sendCommand(RobotCommand command) {
        if (!rosBridgeEnabled) {
            logger.debug("ROSBridge disabled - Command would be: {}", command);
            return;
        }

        if (!connected || client == null) {
            logger.warn("ROSBridge not connected - Cannot send command");
            return;
        }

        try {
            // Build TwistStamped message
            Map<String, Object> message = buildTwistStampedMessage(command);

            // Publish to ROS topic via WebSocket
            client.publish(cmdVelTopic, "geometry_msgs/msg/TwistStamped", message);

            logger.debug("Sent command via ROSBridge: linear=[{}, {}], angular=[{}]",
                    command.getLinearX(), command.getLinearY(), command.getAngularZ());

        } catch (Exception e) {
            logger.error("Error sending command via ROSBridge", e);
        }
    }

    /**
     * Build TwistStamped message from RobotCommand
     * Format matches ROS2 geometry_msgs/msg/TwistStamped
     */
    private Map<String, Object> buildTwistStampedMessage(RobotCommand command) {
        Map<String, Object> message = new HashMap<>();

        // Header
        Map<String, Object> header = new HashMap<>();
        Map<String, Object> stamp = new HashMap<>();
        stamp.put("sec", 0);
        stamp.put("nanosec", 0);
        header.put("stamp", stamp);
        header.put("frame_id", "base_link");
        message.put("header", header);

        // Twist
        Map<String, Object> twist = new HashMap<>();

        // Linear velocity
        Map<String, Object> linear = new HashMap<>();
        linear.put("x", command.getLinearX());
        linear.put("y", command.getLinearY());
        linear.put("z", 0.0);

        // Angular velocity
        Map<String, Object> angular = new HashMap<>();
        angular.put("x", 0.0);
        angular.put("y", 0.0);
        angular.put("z", command.getAngularZ());

        twist.put("linear", linear);
        twist.put("angular", angular);
        message.put("twist", twist);

        return message;
    }

    /**
     * Send stop command
     */
    public void sendStopCommand() {
        RobotCommand stopCommand = RobotCommand.fromAction("stop", 1.0);
        sendCommand(stopCommand);
    }

    /**
     * Check if ROSBridge is connected
     */
    public boolean isConnected() {
        return connected && client != null && client.isConnected();
    }

    /**
     * Subscribe to a ROS topic
     *
     * @param topic The topic name (e.g., "/scan")
     * @param messageType The message type (e.g., "sensor_msgs/msg/LaserScan")
     * @param callback Callback to handle received messages
     */
    public void subscribeToTopic(String topic, String messageType, java.util.function.Consumer<com.google.gson.JsonObject> callback) {
        if (!connected || client == null) {
            logger.warn("Cannot subscribe to {} - ROSBridge not connected", topic);
            return;
        }

        try {
            client.subscribe(topic, messageType, callback);
            logger.info("Subscribed to topic: {} (type: {})", topic, messageType);
        } catch (Exception e) {
            logger.error("Error subscribing to topic: {}", topic, e);
        }
    }

    /**
     * Unsubscribe from a ROS topic
     */
    public void unsubscribeFromTopic(String topic) {
        if (client != null) {
            client.unsubscribe(topic);
            logger.info("Unsubscribed from topic: {}", topic);
        }
    }

    /**
     * Get connection status info
     */
    public Map<String, Object> getConnectionStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", rosBridgeEnabled);
        status.put("connected", isConnected());
        status.put("uri", rosBridgeUri);
        status.put("reconnectAttempts", reconnectCount);
        return status;
    }

    /**
     * Close ROSBridge connection on shutdown
     */
    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down ROSBridge connection");

        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
        }

        if (client != null) {
            try {
                // Unadvertise topic
                client.unadvertise(cmdVelTopic);

                // Close connection
                client.closeBlocking();
                logger.info("ROSBridge connection closed");
            } catch (Exception e) {
                logger.error("Error closing ROSBridge connection", e);
            }
        }
    }
}