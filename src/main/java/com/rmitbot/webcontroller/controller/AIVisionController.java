package com.rmitbot.webcontroller.controller;

import com.rmitbot.webcontroller.service.AIVisionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API controller for AI vision features
 * - Person detection with auto-stop
 * - AprilTag detection
 */
@RestController
@RequestMapping("/api/vision")
@CrossOrigin(origins = "*")
public class AIVisionController {

    private final AIVisionService visionService;

    public AIVisionController(AIVisionService visionService) {
        this.visionService = visionService;
    }

    /**
     * Get person detection status
     */
    @GetMapping("/person-detection/status")
    public ResponseEntity<Map<String, Object>> getPersonDetectionStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", visionService.isPersonDetectionEnabled());
        response.put("detectionDistance", visionService.getDetectionDistance());
        response.put("personsDetected", visionService.getPersonsDetected());
        response.put("lastDetectionTime", visionService.getLastPersonDetectionTime());
        return ResponseEntity.ok(response);
    }

    /**
     * Enable/disable person detection
     */
    @PostMapping("/person-detection/enable")
    public ResponseEntity<Map<String, Object>> enablePersonDetection(@RequestBody Map<String, Boolean> request) {
        boolean enable = request.getOrDefault("enable", true);
        boolean success = visionService.setPersonDetectionEnabled(enable);

        Map<String, Object> response = new HashMap<>();
        response.put("status", success ? "success" : "error");
        response.put("enabled", enable);
        response.put("message", enable ? "Person detection enabled" : "Person detection disabled");
        return ResponseEntity.ok(response);
    }

    /**
     * Update person detection distance threshold
     */
    @PostMapping("/person-detection/distance")
    public ResponseEntity<Map<String, Object>> setDetectionDistance(@RequestBody Map<String, Double> request) {
        double distance = request.getOrDefault("distance", 1.0);
        boolean success = visionService.setDetectionDistance(distance);

        Map<String, Object> response = new HashMap<>();
        response.put("status", success ? "success" : "error");
        response.put("distance", distance);
        response.put("message", "Detection distance updated to " + distance + "m");
        return ResponseEntity.ok(response);
    }

    /**
     * Get AprilTag detection status
     */
    @GetMapping("/apriltag/status")
    public ResponseEntity<Map<String, Object>> getAprilTagStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", visionService.isAprilTagDetectionEnabled());
        response.put("tagFamily", visionService.getTagFamily());
        response.put("tagSize", visionService.getTagSize());
        response.put("detectedTags", visionService.getDetectedTags());
        return ResponseEntity.ok(response);
    }

    /**
     * Enable/disable AprilTag detection
     */
    @PostMapping("/apriltag/enable")
    public ResponseEntity<Map<String, Object>> enableAprilTagDetection(@RequestBody Map<String, Boolean> request) {
        boolean enable = request.getOrDefault("enable", true);
        boolean success = visionService.setAprilTagDetectionEnabled(enable);

        Map<String, Object> response = new HashMap<>();
        response.put("status", success ? "success" : "error");
        response.put("enabled", enable);
        response.put("message", enable ? "AprilTag detection enabled" : "AprilTag detection disabled");
        return ResponseEntity.ok(response);
    }

    /**
     * Get detected AprilTags
     */
    @GetMapping("/apriltag/tags")
    public ResponseEntity<Map<String, Object>> getDetectedTags() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("tags", visionService.getDetectedTags());
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * Update AprilTag configuration
     */
    @PostMapping("/apriltag/config")
    public ResponseEntity<Map<String, Object>> updateAprilTagConfig(@RequestBody Map<String, Object> request) {
        String tagFamily = (String) request.getOrDefault("tagFamily", "tag36h11");
        double tagSize = request.containsKey("tagSize") 
            ? ((Number) request.get("tagSize")).doubleValue() 
            : 0.165;

        boolean success = visionService.updateAprilTagConfig(tagFamily, tagSize);

        Map<String, Object> response = new HashMap<>();
        response.put("status", success ? "success" : "error");
        response.put("tagFamily", tagFamily);
        response.put("tagSize", tagSize);
        response.put("message", "AprilTag configuration updated");
        return ResponseEntity.ok(response);
    }

    /**
     * Get vision system health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getVisionHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("personDetection", Map.of(
            "enabled", visionService.isPersonDetectionEnabled(),
            "active", visionService.isPersonDetectionActive()
        ));
        response.put("aprilTag", Map.of(
            "enabled", visionService.isAprilTagDetectionEnabled(),
            "active", visionService.isAprilTagDetectionActive()
        ));
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
}
