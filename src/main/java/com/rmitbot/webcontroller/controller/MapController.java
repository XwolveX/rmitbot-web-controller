package com.rmitbot.webcontroller.controller;

import com.rmitbot.webcontroller.service.MapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/map")
@CrossOrigin(origins = "*")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> getMapData() {
        return ResponseEntity.ok(mapService.getMapData());
    }

    @PostMapping("/goal")
    public ResponseEntity<Map<String, Object>> sendGoal(@RequestBody Map<String, Double> request) {
        double x = request.getOrDefault("x", 0.0);
        double y = request.getOrDefault("y", 0.0);
        double theta = request.getOrDefault("theta", 0.0);

        boolean success = mapService.sendNavigationGoal(x, y, theta);

        Map<String, Object> response = new HashMap<>();
        response.put("status", success ? "success" : "error");
        response.put("message", success ? "Navigation goal sent" : "Failed to send goal");
        response.put("goal", Map.of("x", x, "y", y, "theta", theta));

        return ResponseEntity.ok(response);
    }
}