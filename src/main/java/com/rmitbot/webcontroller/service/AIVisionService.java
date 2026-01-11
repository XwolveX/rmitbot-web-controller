package com.rmitbot.webcontroller.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for AI vision features:
 * - Person detection with auto-stop
 * - AprilTag detection and tracking
 */
@Service
public class AIVisionService {

    private static final Logger logger = LoggerFactory.getLogger(AIVisionService.class);
    private final Gson gson = new Gson();

    @Value("${raspi.ip}")
    private String raspiIp;

    @Value("${raspi.user}")
    private String raspiUser;

    @Value("${vision.person-detection.enabled:false}")
    private boolean personDetectionEnabled;

    @Value("${vision.person-detection.distance:1.0}")
    private double detectionDistance;

    @Value("${vision.apriltag.enabled:false}")
    private boolean aprilTagEnabled;

    @Value("${vision.apriltag.family:tag36h11}")
    private String tagFamily;

    @Value("${vision.apriltag.size:0.165}")
    private double tagSize;

    private final ROSBridgeService rosBridgeService;

    // Person detection state
    private int personsDetected = 0;
    private long lastPersonDetectionTime = 0;
    private boolean personDetectionActive = false;

    // AprilTag detection state
    private Map<Integer, Map<String, Object>> detectedTags = new ConcurrentHashMap<>();
    private boolean aprilTagDetectionActive = false;

    // Executors
    private ScheduledExecutorService statusCheckExecutor;

    public AIVisionService(ROSBridgeService rosBridgeService) {
        this.rosBridgeService = rosBridgeService;
    }

    @PostConstruct
    public void init() {
        logger.info("Initializing AI Vision Service");

        // Subscribe to person detection events
        if (personDetectionEnabled) {
            subscribeToPersonDetection();
        }

        // Subscribe to AprilTag detection events
        if (aprilTagEnabled) {
            subscribeToAprilTagDetection();
        }

        // Start status monitoring
        statusCheckExecutor = Executors.newSingleThreadScheduledExecutor();
        statusCheckExecutor.scheduleAtFixedRate(
            this::checkServiceStatus,
            10, 30, TimeUnit.SECONDS
        );

        logger.info("AI Vision Service initialized");
        logger.info("Person Detection: {}", personDetectionEnabled ? "ENABLED" : "DISABLED");
        logger.info("AprilTag Detection: {}", aprilTagEnabled ? "ENABLED" : "DISABLED");
    }

    /**
     * Subscribe to person detection messages from ROS
     */
    private void subscribeToPersonDetection() {
        try {
            rosBridgeService.subscribeToTopic(
                "/person_detection/info",
                "std_msgs/msg/String",
                this::handlePersonDetectionMessage
            );

            personDetectionActive = true;
            logger.info("Subscribed to person detection topic");

        } catch (Exception e) {
            logger.error("Failed to subscribe to person detection", e);
            personDetectionActive = false;
        }
    }

    /**
     * Subscribe to AprilTag detection messages from ROS
     */
    private void subscribeToAprilTagDetection() {
        try {
            rosBridgeService.subscribeToTopic(
                "/apriltag/detections",
                "std_msgs/msg/String",
                this::handleAprilTagMessage
            );

            aprilTagDetectionActive = true;
            logger.info("Subscribed to AprilTag detection topic");

        } catch (Exception e) {
            logger.error("Failed to subscribe to AprilTag detection", e);
            aprilTagDetectionActive = false;
        }
    }

    /**
     * Handle person detection messages
     */
    private void handlePersonDetectionMessage(JsonObject message) {
        try {
            JsonObject msg = message.getAsJsonObject("msg");
            String data = msg.get("data").getAsString();
            JsonObject detection = gson.fromJson(data, JsonObject.class);

            int numPersons = detection.get("num_persons").getAsInt();
            boolean emergencyStop = detection.has("emergency_stop") 
                && detection.get("emergency_stop").getAsBoolean();

            personsDetected = numPersons;
            lastPersonDetectionTime = System.currentTimeMillis();

            if (emergencyStop) {
                logger.warn("⚠️ PERSON DETECTED WITHIN {}m - EMERGENCY STOP TRIGGERED!", detectionDistance);
            }

            if (numPersons > 0) {
                logger.debug("Detected {} person(s)", numPersons);
            }

        } catch (Exception e) {
            logger.error("Error processing person detection message", e);
        }
    }

    /**
     * Handle AprilTag detection messages
     */
    private void handleAprilTagMessage(JsonObject message) {
        try {
            JsonObject msg = message.getAsJsonObject("msg");
            String data = msg.get("data").getAsString();
            JsonObject detection = gson.fromJson(data, JsonObject.class);

            int numTags = detection.get("num_tags").getAsInt();
            JsonObject tags = detection.getAsJsonObject("tags");

            // Clear old detections
            detectedTags.clear();

            // Store new detections
            if (numTags > 0) {
                logger.info("Detected {} AprilTag(s)", numTags);

                for (String tagIdStr : tags.keySet()) {
                    JsonObject tagInfo = tags.getAsJsonObject(tagIdStr);
                    int tagId = tagInfo.get("id").getAsInt();

                    Map<String, Object> tagData = new HashMap<>();
                    tagData.put("id", tagId);
                    tagData.put("center", tagInfo.getAsJsonArray("center"));
                    tagData.put("corners", tagInfo.getAsJsonArray("corners"));
                    tagData.put("hamming", tagInfo.get("hamming").getAsInt());
                    tagData.put("decision_margin", tagInfo.get("decision_margin").getAsDouble());
                    tagData.put("timestamp", tagInfo.get("timestamp").getAsDouble());

                    detectedTags.put(tagId, tagData);
                    logger.debug("Tag ID {}: hamming={}, margin={}",
                        tagId,
                        tagInfo.get("hamming").getAsInt(),
                        tagInfo.get("decision_margin").getAsDouble()
                    );
                }
            }

        } catch (Exception e) {
            logger.error("Error processing AprilTag message", e);
        }
    }

    /**
     * Check if vision services are running
     */
    private void checkServiceStatus() {
        if (personDetectionEnabled) {
            checkPersonDetectionService();
        }

        if (aprilTagEnabled) {
            checkAprilTagService();
        }
    }

    private void checkPersonDetectionService() {
        try {
            String command = String.format(
                "ssh -o StrictHostKeyChecking=no -o ConnectTimeout=2 %s@%s " +
                "\"ros2 node list | grep person_detection\"",
                raspiUser, raspiIp
            );

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();

            boolean running = line != null && line.contains("person_detection");

            if (!running && personDetectionActive) {
                logger.warn("Person detection service not running!");
                personDetectionActive = false;
            } else if (running && !personDetectionActive) {
                logger.info("Person detection service detected, resubscribing...");
                subscribeToPersonDetection();
            }

        } catch (Exception e) {
            logger.debug("Error checking person detection service: {}", e.getMessage());
        }
    }

    private void checkAprilTagService() {
        try {
            String command = String.format(
                "ssh -o StrictHostKeyChecking=no -o ConnectTimeout=2 %s@%s " +
                "\"ros2 node list | grep apriltag\"",
                raspiUser, raspiIp
            );

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();

            boolean running = line != null && line.contains("apriltag");

            if (!running && aprilTagDetectionActive) {
                logger.warn("AprilTag detection service not running!");
                aprilTagDetectionActive = false;
            } else if (running && !aprilTagDetectionActive) {
                logger.info("AprilTag detection service detected, resubscribing...");
                subscribeToAprilTagDetection();
            }

        } catch (Exception e) {
            logger.debug("Error checking AprilTag service: {}", e.getMessage());
        }
    }

    // Getters and setters

    public boolean isPersonDetectionEnabled() {
        return personDetectionEnabled;
    }

    public boolean setPersonDetectionEnabled(boolean enabled) {
        try {
            String command = String.format(
                "ssh -o StrictHostKeyChecking=no %s@%s " +
                "\"ros2 param set /person_detection_node enable_detection %s\"",
                raspiUser, raspiIp, enabled
            );

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.start();

            personDetectionEnabled = enabled;
            logger.info("Person detection {}", enabled ? "enabled" : "disabled");

            if (enabled && !personDetectionActive) {
                subscribeToPersonDetection();
            }

            return true;

        } catch (Exception e) {
            logger.error("Failed to set person detection enabled state", e);
            return false;
        }
    }

    public double getDetectionDistance() {
        return detectionDistance;
    }

    public boolean setDetectionDistance(double distance) {
        try {
            String command = String.format(
                "ssh -o StrictHostKeyChecking=no %s@%s " +
                "\"ros2 param set /person_detection_node detection_distance %.2f\"",
                raspiUser, raspiIp, distance
            );

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.start();

            detectionDistance = distance;
            logger.info("Detection distance set to {}m", distance);
            return true;

        } catch (Exception e) {
            logger.error("Failed to set detection distance", e);
            return false;
        }
    }

    public int getPersonsDetected() {
        return personsDetected;
    }

    public long getLastPersonDetectionTime() {
        return lastPersonDetectionTime;
    }

    public boolean isPersonDetectionActive() {
        return personDetectionActive;
    }

    public boolean isAprilTagDetectionEnabled() {
        return aprilTagEnabled;
    }

    public boolean setAprilTagDetectionEnabled(boolean enabled) {
        try {
            String command = String.format(
                "ssh -o StrictHostKeyChecking=no %s@%s " +
                "\"ros2 param set /apriltag_detection_node enable_detection %s\"",
                raspiUser, raspiIp, enabled
            );

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.start();

            aprilTagEnabled = enabled;
            logger.info("AprilTag detection {}", enabled ? "enabled" : "disabled");

            if (enabled && !aprilTagDetectionActive) {
                subscribeToAprilTagDetection();
            }

            return true;

        } catch (Exception e) {
            logger.error("Failed to set AprilTag detection enabled state", e);
            return false;
        }
    }

    public String getTagFamily() {
        return tagFamily;
    }

    public double getTagSize() {
        return tagSize;
    }

    public boolean updateAprilTagConfig(String family, double size) {
        try {
            String command = String.format(
                "ssh -o StrictHostKeyChecking=no %s@%s " +
                "\"ros2 param set /apriltag_detection_node tag_family %s && " +
                "ros2 param set /apriltag_detection_node tag_size %.3f\"",
                raspiUser, raspiIp, family, size
            );

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.start();

            tagFamily = family;
            tagSize = size;
            logger.info("AprilTag config updated: family={}, size={}m", family, size);
            return true;

        } catch (Exception e) {
            logger.error("Failed to update AprilTag configuration", e);
            return false;
        }
    }

    public Map<Integer, Map<String, Object>> getDetectedTags() {
        return new HashMap<>(detectedTags);
    }

    public boolean isAprilTagDetectionActive() {
        return aprilTagDetectionActive;
    }
}
