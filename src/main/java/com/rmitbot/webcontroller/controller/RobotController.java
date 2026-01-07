package com.rmitbot.webcontroller.controller;

import com.rmitbot.webcontroller.model.RobotCommand;
import com.rmitbot.webcontroller.service.ROS2CommandService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API controller for robot control
 * Provides HTTP endpoints as alternative to WebSocket
 */
@RestController
@RequestMapping("/api/robot")
@CrossOrigin(origins = "*")
public class RobotController {

    private final ROS2CommandService ros2CommandService;

    public RobotController(ROS2CommandService ros2CommandService) {
        this.ros2CommandService = ros2CommandService;
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "Robot controller is running");
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
        ros2CommandService.sendCommand(command);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("command", command);
        return ResponseEntity.ok(response);
    }

    /**
     * Send stop command
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, String>> stop() {
        ros2CommandService.sendStopCommand();

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Robot stopped");
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