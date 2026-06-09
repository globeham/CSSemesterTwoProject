/**
 * Authors: Abhineet Bhardwaj, Rohan Nyman, Ishan Singh
 * Date: 6/9/2026
 * Function: Defines the attributes, collision states, and behavior of 
 * on-track entities, power-ups, or performance-altering hazards 
 * interacted with during a race.
 */
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * An item box on the track.  Karts that drive over it receive a random item
 * into their held-item slot.  The box respawns after RESPAWN_MS milliseconds.
 */
public class Item {
    public enum Type { SPEED_BOOST, SHIELD, OIL_SLICK }

    private final double x, y;
    private volatile boolean collected   = false;
    private volatile long    collectedAt = 0;
    private static final long RESPAWN_MS = 8000;
    private static final int R = 14;

    public Item(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public boolean isActive() {
        if (!collected) return true;
        if (System.currentTimeMillis() - collectedAt >= RESPAWN_MS) {
            collected = false; // respawn
        }
        return !collected;
    }

    /**
     * If active and the kart is close enough, mark as collected and return
     * a random item type for the kart to hold.  Returns null if not collected.
     */
    public Type tryPickup(double kx, double ky) {
        if (!isActive()) return null;
        if (Math.hypot(kx - x, ky - y) < R + 22) {
            collected = true;
            collectedAt = System.currentTimeMillis();
            return randomType();
        }
        return null;
    }

    private static Type randomType() {
        double r = Math.random();
        if (r < 0.45) return Type.SPEED_BOOST;
        if (r < 0.75) return Type.SHIELD;
        return Type.OIL_SLICK;
    }

    public double getX() { return x; }
    public double getY() { return y; }

    public void draw(Graphics2D g2) {
        if (!isActive()) {
            // Ghost outline when respawning
            g2.setColor(new Color(255, 255, 255, 40));
            g2.fillOval((int)(x - R), (int)(y - R), R * 2, R * 2);
            return;
        }
        // Rotating rainbow-ish box
        long t = System.currentTimeMillis();
        float hue = (t % 3000) / 3000f;
        Color fill = Color.getHSBColor(hue, 0.9f, 1.0f);
        g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 60));
        g2.fillOval((int)(x - R - 6), (int)(y - R - 6), (R + 6) * 2, (R + 6) * 2);
        g2.setColor(fill);
        g2.fillOval((int)(x - R), (int)(y - R), R * 2, R * 2);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval((int)(x - R), (int)(y - R), R * 2, R * 2);
        // "?" symbol
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawString("?", (int)x - 4, (int)y + 5);
    }

    // ------------------------------------------------------------------
    // Static helpers for drawing held-item icons in the HUD
    // ------------------------------------------------------------------

    public static void drawItemIcon(Graphics2D g2, Type type, int cx, int cy, int r) {
        if (type == null) return;
        Color fill;
        String label;
        switch (type) {
            case SPEED_BOOST: fill = new Color(255, 200, 0);   label = "S"; break;
            case SHIELD:      fill = new Color(60,  160, 255); label = "W"; break;
            default:          fill = new Color(60,   60,  60); label = "O"; break;
        }
        g2.setColor(fill);
        g2.fillOval(cx - r, cy - r, r * 2, r * 2);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(cx - r, cy - r, r * 2, r * 2);
        g2.setColor(Color.BLACK);
        g2.drawString(label, cx - 4, cy + 5);
    }
}
