import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class Kart {
    private double x, y;
    private double prevX, prevY;
    private double angle;
    private double speed;
    private int boostTimer;
    private static final int BASE_WIDTH = 30;
    private static final int BASE_HEIGHT = 20;
    private static final double MAX_SPEED = 3.2;
    private static final double OFF_TRACK_MAX_SPEED = 0.5;
    private static final double ACCELERATION = 0.12;
    private static final double BRAKE_DECEL = 0.10;
    private static final double FRICTION = 0.025;
    private static final double TURN_SPEED = Math.PI / 80;
    private static final int BOOST_DURATION = 50;
    private static final double BOOST_MULTIPLIER = 1.8;
    private BufferedImage sprite = null;

    public Kart(int x, int y) {
        this.x = x;
        this.y = y;
        this.prevX = x; this.prevY = y;
        this.angle = Math.PI / 2;
        this.speed = 0;
        this.boostTimer = 0;
    }

    /**
     * Try loading a PNG sprite from the given file path. If loading fails,
     * the kart will fall back to the programmatic drawing.
     */
    public void loadSprite(String path) {
        try {
            sprite = ImageIO.read(new File(path));
        } catch (IOException e) {
            sprite = null;
        }
    }

    public void setSprite(BufferedImage img) {
        this.sprite = img;
    }

    public boolean hasSprite() { return this.sprite != null; }

    public double getY() { return y; }
    public double getPrevY() { return prevY; }
    public void setPrevY(double py) { this.prevY = py; }
    public double getX() { return x; }
    public double getPrevX() { return prevX; }
    public void setPrevX(double px) { this.prevX = px; }
    public double getAngle() { return angle; }
    public double getSpeed() { return speed; }
    public boolean isBoostActive() { return boostTimer > 0; }

    public void applyBoost() { boostTimer = BOOST_DURATION; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setSpeed(double s) { this.speed = s; }
    public void setAngle(double a) { this.angle = a; }

    public void reset(int x, int y, double angle) {
        this.x = x; this.y = y; this.prevX = x; this.prevY = y; this.angle = angle; this.speed = 0; this.boostTimer = 0;
    }

    public void update(boolean accelerate, boolean brake, boolean turnLeft, boolean turnRight, boolean onTrack) {
        double targetMax = onTrack ? MAX_SPEED : OFF_TRACK_MAX_SPEED;
        if (boostTimer > 0) { targetMax *= BOOST_MULTIPLIER; boostTimer--; }

        if (speed >= 0) {
            if (accelerate) {
                // Torque curve: full power near 0, tapers off approaching top speed
                speed += ACCELERATION * Math.max(0, 1.0 - speed / targetMax);
            } else if (brake) {
                // Hard brakes stop cleanly at 0 — must be stationary to engage reverse
                speed = Math.max(0, speed - BRAKE_DECEL);
            } else {
                // Light rolling friction — car coasts naturally
                speed *= (1.0 - FRICTION);
                if (speed < 0.02) speed = 0;
            }
        } else {
            // Reversing
            if (brake) {
                // Accelerate in reverse at half power, limited top speed
                speed = Math.max(-targetMax * 0.35, speed - ACCELERATION * 0.5);
            } else if (accelerate) {
                // Throttle brakes out of reverse
                speed = Math.min(0, speed + BRAKE_DECEL);
            } else {
                speed *= (1.0 - FRICTION);
                if (speed > -0.02) speed = 0;
            }
        }

        if (Math.abs(speed) > 0.15) {
            double turnFactor = 1.0 - Math.abs(speed) / MAX_SPEED * 0.5;
            if (turnLeft)  angle -= TURN_SPEED * turnFactor;
            if (turnRight) angle += TURN_SPEED * turnFactor;
        }

        x += speed * Math.cos(angle);
        y += speed * Math.sin(angle);
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();

        g2.translate((int)x, (int)y);
        g2.rotate(angle);
        g2.translate(-BASE_WIDTH/2, -BASE_HEIGHT/2);

        if (sprite == null) {
            g2.setColor(Color.RED);
            g2.fillRect(0, 0, BASE_WIDTH, BASE_HEIGHT);
        } else {
            g2.drawImage(sprite, 0, 0, BASE_WIDTH, BASE_HEIGHT, null);
        }
        g2.dispose();
    }


}