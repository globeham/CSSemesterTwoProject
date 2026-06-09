/**
 * Authors: Abhineet Bhardwaj, Rohan Nyman, Ishan Singh
 * Date: 6/9/2026
 * Function: Manages vehicle physics, including position coordinates, 
 * velocity, acceleration, traction, steering angle, and bounding 
 * boxes for collision handling.
 */
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * A racing kart with velocity-accurate physics, drift, and a programmatic
 * top-down F1-style fallback renderer that looks good without a sprite.
 */
public class Kart {
    // Physics fields
    private double x, y;
    private double prevX, prevY;
    private double angle;
    private double speed;

    // Timer fields
    private int boostTimer;
    private int shieldTimer;
    private int spinOutTimer;
    private int driftTimer;
    private boolean drifting;
    private double slideLag;

    // Visual
    private Color color;
    private int kartNumber; // 1-based player/kart number, drawn on the body

    // Constants
    static final int BASE_WIDTH  = 80;
    static final int BASE_HEIGHT = 60;
    private static final double MAX_SPEED           = 5.2;
    private static final double OFF_TRACK_MAX_SPEED = 1.4;
    private static final double ACCELERATION        = 0.15;
    private static final double BRAKE_DECEL         = 0.24;
    private static final double FRICTION            = 0.055;
    private static final double TURN_SPEED          = Math.PI / 46;
    private static final int    BOOST_DURATION      = 55;
    private static final int    SHIELD_DURATION     = 160;
    private static final double BOOST_MULTIPLIER    = 1.75;
    private static final int    DRIFT_ENTER_FRAMES  = 10;
    private static final int    DRIFT_BOOST_THRESH  = 22;
    private static final double MAX_SLIDE           = 0.32;
    private static final double SLIDE_ATTACK        = 0.045;

    private BufferedImage sprite = null;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    public Kart(int x, int y) { this(x, y, Color.RED, 1); }

    public Kart(int x, int y, Color color) { this(x, y, color, 1); }

    public Kart(int x, int y, Color color, int kartNumber) {
        this.x = x; this.y = y;
        this.prevX = x; this.prevY = y;
        this.angle = Math.PI / 2;
        this.color = color;
        this.kartNumber = kartNumber;
    }

    // -------------------------------------------------------------------------
    // Getters / setters
    // -------------------------------------------------------------------------

    public void loadSprite(String path) {
        try { sprite = ImageIO.read(new File(path)); } catch (IOException e) { sprite = null; }
    }
    public void setSprite(BufferedImage img) { this.sprite = img; }
    public void setColor(Color c)           { this.color = c; }
    public void setKartNumber(int n)        { this.kartNumber = n; }
    public Color  getColor()               { return color; }
    public boolean hasSprite()             { return sprite != null; }

    public double  getX()        { return x; }
    public double  getY()        { return y; }
    public double  getPrevX()    { return prevX; }
    public double  getPrevY()    { return prevY; }
    public void    setPrevX(double px) { this.prevX = px; }
    public void    setPrevY(double py) { this.prevY = py; }
    public double  getAngle()    { return angle; }
    public double  getSpeed()    { return speed; }
    public boolean isBoostActive()  { return boostTimer  > 0; }
    public boolean isShieldActive() { return shieldTimer > 0; }
    public boolean isDrifting()     { return drifting; }
    public boolean isSpinningOut()  { return spinOutTimer > 0; }

    public void applyBoost()   { boostTimer  = BOOST_DURATION; }
    public void applyShield()  { shieldTimer = SHIELD_DURATION; }
    public void applySpinOut() {
        spinOutTimer = 85; speed *= 0.38; drifting = false; driftTimer = 0; slideLag = 0;
    }

    public void offsetPosition(double dx, double dy) { x += dx; y += dy; }

    public void reset(int rx, int ry, double a) {
        x = rx; y = ry; prevX = rx; prevY = ry;
        angle = a; speed = 0;
        boostTimer = 0; shieldTimer = 0; spinOutTimer = 0;
        driftTimer = 0; drifting = false; slideLag = 0;
    }

    // -------------------------------------------------------------------------
    // Physics update
    // -------------------------------------------------------------------------

    public void update(boolean accelerate, boolean brake,
                       boolean turnLeft, boolean turnRight, boolean onTrack) {

        // Spin-out overrides all inputs
        if (spinOutTimer > 0) {
            angle += 0.19;
            speed *= 0.955;
            spinOutTimer--;
            x += speed * Math.cos(angle);
            y += speed * Math.sin(angle);
            return;
        }

        // Speed limit
        double targetMax = onTrack ? MAX_SPEED : OFF_TRACK_MAX_SPEED;
        if (boostTimer > 0) { targetMax *= BOOST_MULTIPLIER; boostTimer--; }
        if (shieldTimer > 0) shieldTimer--;

        // Longitudinal inputs
        if (accelerate) speed += ACCELERATION;
        else if (brake)  speed -= BRAKE_DECEL;
        else             speed *= (1.0 - FRICTION);

        speed = Math.max(-targetMax * 0.38, Math.min(targetMax, speed));

        // Steering (turn rate proportional to speed ratio)
        boolean turning = turnLeft || turnRight;
        if (Math.abs(speed) > 0.18) {
            double tf = Math.max(0.32, Math.abs(speed) / MAX_SPEED);
            if (turnLeft)  angle -= TURN_SPEED * tf;
            if (turnRight) angle += TURN_SPEED * tf;
        }

        // Auto-drift detection
        double sr = Math.abs(speed) / MAX_SPEED;
        if (turning && sr > 0.52 && onTrack) {
            driftTimer++;
            if (driftTimer >= DRIFT_ENTER_FRAMES) drifting = true;
        } else {
            if (drifting && driftTimer >= DRIFT_BOOST_THRESH) {
                boostTimer = Math.min(BOOST_DURATION, driftTimer / 2);
            }
            drifting = false;
            driftTimer = 0;
        }

        // Lateral slide builds during drift
        double targetSlide = drifting ? (turnLeft ? -MAX_SLIDE : MAX_SLIDE) : 0;
        slideLag += (targetSlide - slideLag) * SLIDE_ATTACK;

        // Move
        double moveAngle = angle + slideLag;
        x += speed * Math.cos(moveAngle);
        y += speed * Math.sin(moveAngle);

        // Off-track pushback
        if (!onTrack && Math.abs(speed) > 0.1) {
            x -= Math.signum(speed) * Math.cos(moveAngle) * 0.75;
            y -= Math.signum(speed) * Math.sin(moveAngle) * 0.75;
        }
    }

    // -------------------------------------------------------------------------
    // Rendering — proper top-down F1 car
    // -------------------------------------------------------------------------

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Drift marks in world space (behind kart)
        if (drifting && Math.abs(speed) > 1.2) {
            double mx = x - Math.cos(angle) * 34;
            double my = y - Math.sin(angle) * 34;
            g2.setColor(new Color(20, 20, 20, 110));
            g2.fillOval((int) mx - 4, (int) my - 4, 9, 9);
        }

        // Transform to kart local space
        g2.translate(x, y);
        g2.rotate(angle);
        g2.translate(-BASE_WIDTH / 2.0, -BASE_HEIGHT / 2.0);

        if (sprite != null) {
            drawSpriteMode(g2);
        } else {
            drawCarBody(g2);
        }

        g2.dispose();
    }

    /** When sprite is loaded — draw it plus overlay effects. */
    private void drawSpriteMode(Graphics2D g2) {
        // Ground shadow
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillOval(6, 10, BASE_WIDTH - 8, BASE_HEIGHT - 14);

        g2.drawImage(sprite, 0, 0, BASE_WIDTH, BASE_HEIGHT, null);
        drawEffects(g2);
    }

    /**
     * Programmatic top-down F1-style car.
     * Local coords: x=0 is rear, x=W is front (nose). y=0 top, y=H bottom.
     */
    private void drawCarBody(Graphics2D g2) {
        int W  = BASE_WIDTH;   // 80
        int H  = BASE_HEIGHT;  // 60
        int cy = H / 2;        // 30

        // Ground shadow
        g2.setColor(new Color(0, 0, 0, 45));
        g2.fillOval(8, 12, W - 10, H - 16);

        // ---- REAR WING (full-height dark bar at rear) ----
        g2.setColor(new Color(18, 18, 18));
        g2.fillRoundRect(2, 2, 16, H - 4, 3, 3);
        // wing endplate highlight
        g2.setColor(new Color(45, 45, 45));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(2, 2, 16, H - 4, 3, 3);
        // horizontal strut
        g2.setColor(new Color(35, 35, 35));
        g2.fillRect(5, cy - 3, 10, 6);

        // ---- WHEELS (draw before body so body covers their inner parts) ----
        Color wheelDark = new Color(24, 24, 24);
        Color wheelRim  = new Color(62, 62, 62);
        int   ww = 13, wh = 17;
        int[] wxs = {4, W - 4 - ww};
        int[] wys = {3, H - 3 - wh};
        g2.setStroke(new BasicStroke(1f));
        for (int wx : wxs) {
            for (int wy : wys) {
                g2.setColor(wheelDark);
                g2.fillRoundRect(wx, wy, ww, wh, 4, 4);
                g2.setColor(wheelRim);
                g2.drawRoundRect(wx, wy, ww, wh, 4, 4);
                // center dot (rim lock)
                g2.setColor(new Color(80, 80, 80));
                g2.fillOval(wx + ww/2 - 2, wy + wh/2 - 2, 4, 4);
            }
        }

        // ---- MAIN BODY (tapered nose toward front/right) ----
        Color bodyC = color != null ? color : Color.RED;
        Path2D.Double body = new Path2D.Double();
        int bodyTop = 14, bodyBot = H - 14;
        body.moveTo(18, bodyTop);              // rear-right
        body.lineTo(W - 22, bodyTop - 2);      // forward-right edge
        body.curveTo(W - 6, bodyTop - 1, W - 2, cy - 5, W - 2, cy); // nose right curve
        body.curveTo(W - 2, cy + 5, W - 6, bodyBot + 1, W - 22, bodyBot + 2);
        body.lineTo(18, bodyBot);              // rear-left
        body.closePath();

        g2.setColor(bodyC);
        g2.fill(body);

        // Sidepods (darker wing areas on the flanks)
        g2.setColor(new Color(Math.max(0, bodyC.getRed() - 40),
                              Math.max(0, bodyC.getGreen() - 40),
                              Math.max(0, bodyC.getBlue() - 40)));
        g2.fillRect(20, bodyTop, 28, 6);
        g2.fillRect(20, bodyBot - 6, 28, 6);

        // Gloss highlight (white translucent strip on upper surface)
        g2.setColor(new Color(255, 255, 255, 52));
        Path2D.Double hi = new Path2D.Double();
        hi.moveTo(22, bodyTop + 2);
        hi.lineTo(W - 24, bodyTop);
        hi.curveTo(W - 12, bodyTop, W - 7, cy - 5, W - 8, cy - 2);
        hi.lineTo(W - 24, cy - 2);
        hi.lineTo(22, cy - 4);
        hi.closePath();
        g2.fill(hi);

        // Body outline
        g2.setColor(bodyC.darker());
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(body);

        // Color stripe (accent line down center)
        g2.setColor(new Color(255, 255, 255, 80));
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(22, cy, W - 24, cy);

        // ---- FRONT WING ----
        g2.setColor(new Color(18, 18, 18));
        g2.fillRoundRect(W - 18, 2, 14, H - 4, 3, 3);
        g2.setColor(new Color(45, 45, 45));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(W - 18, 2, 14, H - 4, 3, 3);

        // ---- COCKPIT ----
        int cpX = W / 2 - 12, cpW = 24, cpH = 18;
        // Roll hoop surround
        g2.setColor(new Color(10, 10, 15));
        g2.fillRoundRect(cpX, cy - cpH / 2, cpW, cpH, 7, 7);
        // Helmet
        g2.setColor(new Color(225, 225, 235));
        g2.fillOval(cpX + 3, cy - 7, 17, 14);
        // Visor
        g2.setColor(new Color(70, 140, 210, 210));
        g2.fillRoundRect(cpX + 10, cy - 5, 11, 10, 4, 4);
        // Halo bar
        g2.setColor(new Color(190, 190, 190, 180));
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(cpX + 1, cy, cpX + cpW - 2, cy);
        g2.drawLine(cpX + cpW / 2, cy - 7, cpX + cpW / 2, cy + 7);

        // ---- Kart number on nose ----
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        String num = String.valueOf(kartNumber);
        int nsw = g2.getFontMetrics().stringWidth(num);
        g2.setColor(Color.WHITE);
        g2.drawString(num, W - 14 - nsw / 2, cy + 4);

        drawEffects(g2);
    }

    /** Shared effects drawn in local space (shield ring, drift glow). */
    private void drawEffects(Graphics2D g2) {
        int W = BASE_WIDTH, H = BASE_HEIGHT, cy = H / 2;

        if (drifting) {
            // Orange glow around rear wheels when drifting
            g2.setColor(new Color(255, 130, 0, 140));
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(4, 3, 12, 16, 4, 4);
            g2.drawRoundRect(4, H - 19, 12, 16, 4, 4);
        }

        if (shieldTimer > 0) {
            g2.translate(W / 2.0, cy);
            int alpha = Math.min(200, shieldTimer * 3 + 60);
            // Outer pulsing ring
            g2.setColor(new Color(80, 180, 255, alpha / 2));
            int sr = W / 2 + 12;
            g2.fillOval(-sr, -sr, sr * 2, sr * 2);
            g2.setColor(new Color(120, 210, 255, alpha));
            g2.setStroke(new BasicStroke(3f));
            g2.drawOval(-sr, -sr, sr * 2, sr * 2);
            g2.drawOval(-sr + 4, -sr + 4, (sr - 4) * 2, (sr - 4) * 2);
        }
    }
}
