package com.rmitbot.webcontroller.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing robot movement commands
 * Matches the gamepad controller key bindings from the Python script
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RobotCommand {
    private String action;      // w, a, s, d, q, e, z, c, left, right, stop
    private double linearX;     // Forward/backward velocity
    private double linearY;     // Left/right velocity (strafe)
    private double angularZ;    // Rotation velocity
    private double speedMultiplier; // Speed multiplier (0.2 to 3.0)

    /**
     * Create command from action key
     */
    public static RobotCommand fromAction(String action, double speedMultiplier) {
        RobotCommand cmd = new RobotCommand();
        cmd.setAction(action);
        cmd.setSpeedMultiplier(speedMultiplier);

        // Base speeds (matching Python script)
        double baseLinear = 0.3;
        double baseAngular = 0.5;

        switch (action.toLowerCase()) {
            case "w":  // Forward
                cmd.setLinearX(1.0 * baseLinear * speedMultiplier);
                cmd.setLinearY(0.0);
                cmd.setAngularZ(0.0);
                break;
            case "s":  // Backward
                cmd.setLinearX(-1.0 * baseLinear * speedMultiplier);
                cmd.setLinearY(0.0);
                cmd.setAngularZ(0.0);
                break;
            case "a":  // Strafe left
                cmd.setLinearX(0.0);
                cmd.setLinearY(1.0 * baseLinear * speedMultiplier);
                cmd.setAngularZ(0.0);
                break;
            case "d":  // Strafe right
                cmd.setLinearX(0.0);
                cmd.setLinearY(-1.0 * baseLinear * speedMultiplier);
                cmd.setAngularZ(0.0);
                break;
            case "q":  // Diagonal forward-left
                cmd.setLinearX(1.0 * baseLinear * speedMultiplier);
                cmd.setLinearY(1.0 * baseLinear * speedMultiplier);
                cmd.setAngularZ(0.0);
                break;
            case "e":  // Diagonal forward-right
                cmd.setLinearX(1.0 * baseLinear * speedMultiplier);
                cmd.setLinearY(-1.0 * baseLinear * speedMultiplier);
                cmd.setAngularZ(0.0);
                break;
            case "z":  // Diagonal backward-left
                cmd.setLinearX(-1.0 * baseLinear * speedMultiplier);
                cmd.setLinearY(1.0 * baseLinear * speedMultiplier);
                cmd.setAngularZ(0.0);
                break;
            case "c":  // Diagonal backward-right
                cmd.setLinearX(-1.0 * baseLinear * speedMultiplier);
                cmd.setLinearY(-1.0 * baseLinear * speedMultiplier);
                cmd.setAngularZ(0.0);
                break;
            case "left":  // Rotate left
                cmd.setLinearX(0.0);
                cmd.setLinearY(0.0);
                cmd.setAngularZ(1.0 * baseAngular * speedMultiplier);
                break;
            case "right":  // Rotate right
                cmd.setLinearX(0.0);
                cmd.setLinearY(0.0);
                cmd.setAngularZ(-1.0 * baseAngular * speedMultiplier);
                break;
            case "stop":
            default:
                cmd.setLinearX(0.0);
                cmd.setLinearY(0.0);
                cmd.setAngularZ(0.0);
                break;
        }

        return cmd;
    }

    /**
     * Check if this is a stop command
     */
    public boolean isStop() {
        return linearX == 0.0 && linearY == 0.0 && angularZ == 0.0;
    }
}