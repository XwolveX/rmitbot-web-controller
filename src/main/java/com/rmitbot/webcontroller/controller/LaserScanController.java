package com.rmitbot.webcontroller.controller;

import com.google.gson.JsonObject;
import com.rmitbot.webcontroller.service.LaserScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API controller for laser scan data
 */
@RestController
@RequestMapping("/api/lidar")
@CrossOrigin(origins = "*")
public class LaserScanController {

    private final LaserScanService laserScanService;

    public LaserScanController(LaserScanService laserScanService) {
        this.laserScanService = laserScanService;
    }

    /**
     * Get status of laser scan subscription
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("subscribed", laserScanService.isSubscribed());
        response.put("activeClients", laserScanService.getActiveClientCount());
        response.put("hasData", laserScanService.getLatestScanData() != null);
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * Get latest laser scan data
     */
    @GetMapping("/scan")
    public ResponseEntity<Map<String, Object>> getLatestScan() {
        JsonObject scanData = laserScanService.getLatestScanData();

        Map<String, Object> response = new HashMap<>();
        if (scanData != null) {
            response.put("status", "success");
            response.put("data", scanData.toString());
        } else {
            response.put("status", "no_data");
            response.put("message", "No laser scan data available yet");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Force subscribe to laser scan (for debugging)
     */
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, String>> subscribe() {
        laserScanService.subscribeToLaserScan();

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Subscription requested");
        return ResponseEntity.ok(response);
    }
}