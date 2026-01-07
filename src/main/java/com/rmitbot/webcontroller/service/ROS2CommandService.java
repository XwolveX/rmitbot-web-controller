package com.rmitbot.webcontroller.service;

import com.rmitbot.webcontroller.model.RobotCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;

/**
 * Service to send commands to ROS2 system
 * Uses ros2 topic pub command to publish velocity commands
 * FIXED VERSION - Better timeout and synchronization
 */
@Service
public class ROS2CommandService {

    private static final Logger logger = LoggerFactory.getLogger(ROS2CommandService.class);

    @Value("${ros2.topic.cmd_vel:/cmd_vel}")
    private String cmdVelTopic;

    @Value("${ros2.enabled:true}")
    private boolean ros2Enabled;

    @Value("${raspi.ip:localhost}")
    private String raspiIp;

    @Value("${raspi.user:ubuntu}")
    private String raspiUser;

    /**
     * Send velocity command to robot via ROS2
     * FIXED: Now properly waits for command completion with adequate timeout
     */
    public void sendCommand(RobotCommand command) {
        if (!ros2Enabled) {
            logger.info("ROS2 disabled - Command would be: {}", command);
            return;
        }

        // Execute in background thread to avoid blocking WebSocket
        CompletableFuture.runAsync(() -> {
            try {
                // Build ROS2 command
                String ros2Command = buildROS2Command(command);
                logger.debug("Executing: {}", ros2Command);

                // Execute command
                ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", ros2Command);
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();

                // Read output for debugging (optional)
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.trace("ROS2 output: {}", line);
                    }
                }

                // Wait for completion with adequate timeout
                boolean finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    logger.warn("ROS2 command timed out after 3 seconds");
                } else {
                    int exitCode = process.exitValue();
                    if (exitCode != 0) {
                        logger.warn("ROS2 command exited with code: {}", exitCode);
                    }
                }

            } catch (IOException | InterruptedException e) {
                logger.error("Error sending ROS2 command", e);
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Build ROS2 topic publish command
     * FIXED: Better SSH settings and using --once for immediate response
     */
    private String buildROS2Command(RobotCommand command) {
        // Build TwistStamped message in YAML format with proper quoting
        String message = String.format(
                "{header: {stamp: {sec: 0, nanosec: 0}, frame_id: base_link}, " +
                        "twist: {linear: {x: %.2f, y: %.2f, z: 0.0}, angular: {x: 0.0, y: 0.0, z: %.2f}}}",
                command.getLinearX(),
                command.getLinearY(),
                command.getAngularZ()
        );

        // If running on different machine, use SSH
        if (!"localhost".equals(raspiIp)) {
            // FIXED: Better SSH connection settings
            // - ConnectTimeout: Max time to establish SSH connection
            // - ServerAliveInterval: Keep SSH connection alive
            return String.format(
                    "ssh -o StrictHostKeyChecking=no -o ConnectTimeout=2 -o ServerAliveInterval=1 " +
                            "%s@%s \"bash -c 'source /opt/ros/jazzy/setup.bash && " +
                            "ros2 topic pub --once %s geometry_msgs/msg/TwistStamped \\\"%s\\\"'\"",
                    raspiUser, raspiIp, cmdVelTopic, message
            );
        } else {
            // Local execution
            return String.format(
                    "bash -c \"source /opt/ros/jazzy/setup.bash && " +
                            "ros2 topic pub --once %s geometry_msgs/msg/TwistStamped '%s'\"",
                    cmdVelTopic, message
            );
        }
    }

    /**
     * Send stop command
     */
    public void sendStopCommand() {
        RobotCommand stopCommand = RobotCommand.fromAction("stop", 1.0);
        sendCommand(stopCommand);
    }
}