package com.rmitbot.webcontroller.controller;

import com.rmitbot.webcontroller.model.RobotCommand;
import com.rmitbot.webcontroller.service.ROSBridgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API controller for robot control
 * UPDATED: Now uses ROSBridgeService instead of ROS2CommandService
 */
@RestController
@RequestMapping("/api/robot")
@CrossOrigin(origins = "*")
public class RobotController {

    private final ROSBridgeService rosBridgeService;

    public RobotController(ROSBridgeService rosBridgeService) {
        this.rosBridgeService = rosBridgeService;
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "Robot controller is running");
        response.put("rosbridge", rosBridgeService.getConnectionStatus());
        return ResponseEntity.ok(response);
    }

    /**
     * Get ROSBridge connection status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("rosbridge", rosBridgeService.getConnectionStatus());
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * Send movement command via POST
     */
    @PostMapping("/command")
    public ResponseEntity<Map<String, Object>> sendCommand(@RequestBody Map<String, Object> request) {
        String action = (String) request.get("action");
        Double speedMultiplier = request.containsKey("speedMultiplier")
                ? ((Number) request.get("speedMultiplier")).doubleValue()
                : 1.0;

        RobotCommand command = RobotCommand.fromAction(action, speedMultiplier);
        rosBridgeService.sendCommand(command);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("command", command);
        response.put("rosBridgeConnected", rosBridgeService.isConnected());
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * Send stop command
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        rosBridgeService.sendStopCommand();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Robot stopped");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * Get available commands
     */
    @GetMapping("/commands")
    public ResponseEntity<Map<String, Object>> getCommands() {
        Map<String, Object> response = new HashMap<>();
        response.put("movement", new String[]{"w", "a", "s", "d", "q", "e", "z", "c"});
        response.put("rotation", new String[]{"left", "right"});
        response.put("control", new String[]{"stop"});

        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("w", "Forward");
        descriptions.put("s", "Backward");
        descriptions.put("a", "Strafe Left");
        descriptions.put("d", "Strafe Right");
        descriptions.put("q", "Diagonal Forward-Left");
        descriptions.put("e", "Diagonal Forward-Right");
        descriptions.put("z", "Diagonal Backward-Left");
        descriptions.put("c", "Diagonal Backward-Right");
        descriptions.put("left", "Rotate Left");
        descriptions.put("right", "Rotate Right");

        response.put("descriptions", descriptions);
        return ResponseEntity.ok(response);
    }
}
