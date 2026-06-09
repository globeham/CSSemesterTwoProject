/**
 * Authors: Abhineet Bhardwaj, Rohan Nyman, Ishan Singh
 * Date: 6/9/2026
 * Function: Manages autonomous vehicle logic, utilizing a forward-only 
 * waypoint tracking window to calculate steering angles and 
 * inputs for AI karts to navigate the track circuit.
 */

import java.util.List;
import java.awt.geom.Point2D;

public class AIController {
    public enum Difficulty { EASY, NORMAL, HARD }

    private int nearestIdx = -1;
    private final int lookahead;

    public AIController(Difficulty diff) {
        switch (diff) {
            case EASY:   lookahead = 10; break;
            case HARD:   lookahead = 24; break;
            default:     lookahead = 16; break; // Normal
        }
    }

    public AIController() { this(Difficulty.NORMAL); }

    public boolean[] computeInputs(double kx, double ky, double angle,
                                   List<Point2D.Double> centerline) {
        int n = centerline.size();

        // 1. First-frame setup: Find the absolute closest point on the track
        if (nearestIdx < 0) {
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double d = Math.hypot(kx - centerline.get(i).x, ky - centerline.get(i).y);
                if (d < minDist) {
                    minDist = d;
                    nearestIdx = i;
                }
            }
        }

// 2. FIXED: Bidirectional Smart Scan
        // Scans 15 points behind and 45 points ahead to ensure the AI never skips 
        // track indices or gets stuck, matching the GamePanel's tracking precisely.
        int bestIdx = nearestIdx;
        double minDist = Math.hypot(kx - centerline.get(nearestIdx).x, ky - centerline.get(nearestIdx).y);
        
        for (int i = -15; i <= 45; i++) {
            int checkIdx = (nearestIdx + i + n) % n;
            double d = Math.hypot(kx - centerline.get(checkIdx).x, ky - centerline.get(checkIdx).y);
            if (d < minDist) {
                minDist = d;
                bestIdx = checkIdx;
            }
        }
        nearestIdx = bestIdx;

        // 3. Aim further down the track based on lookahead configuration
        int targetIdx = (nearestIdx + lookahead) % n;
        Point2D.Double target = centerline.get(targetIdx);

        // 4. Calculate steering angle error
        double desiredAngle = Math.atan2(target.y - ky, target.x - kx);
        double diff = normalizeAngle(desiredAngle - angle);

        // 5. Force absolute progression outputs
        boolean goForward = true;
        boolean brake     = false;
        
        // Tight steering deadzone to keep them pinned to the line
        boolean turnLeft  = diff < -0.04;
        boolean turnRight = diff > 0.04;

        return new boolean[]{ goForward, brake, turnLeft, turnRight };
    }

    public int getNearestIdx() { return nearestIdx; }

    private static double normalizeAngle(double a) {
        while (a >  Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}