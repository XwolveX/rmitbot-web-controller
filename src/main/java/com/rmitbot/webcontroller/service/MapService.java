package com.rmitbot.webcontroller.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Service
public class MapService {

    private static final Logger logger = LoggerFactory.getLogger(MapService.class);

    @Value("${raspi.ip}")
    private String raspiIp;

    @Value("${raspi.user}")
    private String raspiUser;

    /**
     * Get current map data from SLAM
     */
    public Map<String, Object> getMapData() {
        Map<String, Object> result = new HashMap<>();

        try {
            // Get map metadata via ros2 topic echo
            String command = String.format(
                    "ssh -o StrictHostKeyChecking=no %s@%s \"source /opt/ros/jazzy/setup.bash && " +
                            "timeout 1s ros2 topic echo --once /map nav_msgs/msg/OccupancyGrid\"",
                    raspiUser, raspiIp
            );

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();

            result.put("status", "success");
            result.put("data", output.toString());

        } catch (Exception e) {
            logger.error("Error getting map data", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Send navigation goal
     */
    public boolean sendNavigationGoal(double x, double y, double theta) {
        try {
            String command = String.format(
                    "ssh -o StrictHostKeyChecking=no %s@%s \"source /opt/ros/jazzy/setup.bash && " +
                            "ros2 topic pub --once /goal_pose geometry_msgs/msg/PoseStamped " +
                            "'{header: {frame_id: map}, pose: {position: {x: %.2f, y: %.2f, z: 0.0}, " +
                            "orientation: {x: 0.0, y: 0.0, z: %.2f, w: %.2f}}}'\"",
                    raspiUser, raspiIp, x, y, Math.sin(theta/2), Math.cos(theta/2)
            );

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.start();

            logger.info("Navigation goal sent: x={}, y={}, theta={}", x, y, theta);
            return true;

        } catch (Exception e) {
            logger.error("Error sending navigation goal", e);
            return false;
        }
    }
}