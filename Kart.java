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
    private static final int BASE_WIDTH = 80;
    private static final int BASE_HEIGHT = 60;
    private static final double MAX_SPEED = 4.8;
    private static final double OFF_TRACK_MAX_SPEED = 1.6;
    private static final double ACCELERATION = 0.14;
    private static final double BRAKE_DECEL = 0.22;
    private static final double FRICTION = 0.06;
    private static final double TURN_SPEED = Math.PI / 48;
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

    public void reset(int x, int y, double angle) {
        this.x = x; this.y = y; this.prevX = x; this.prevY = y; this.angle = angle; this.speed = 0; this.boostTimer = 0;
    }

    public void update(boolean accelerate, boolean brake, boolean turnLeft, boolean turnRight, boolean onTrack) {
        double targetMax = onTrack ? MAX_SPEED : OFF_TRACK_MAX_SPEED;
        if (boostTimer > 0) { targetMax *= BOOST_MULTIPLIER; boostTimer--; }

        if (accelerate) speed += ACCELERATION;
        else if (brake) speed -= BRAKE_DECEL;
        else speed *= (1.0 - FRICTION);

        if (speed > targetMax) speed = targetMax;
        if (speed < -targetMax * 0.4) speed = -targetMax * 0.4;

        if (Math.abs(speed) > 0.2) {
            double turnFactor = Math.max(0.35, Math.abs(speed) / MAX_SPEED);
            if (turnLeft) angle -= TURN_SPEED * turnFactor;
            if (turnRight) angle += TURN_SPEED * turnFactor;
        }

        x += speed * Math.cos(angle);
        y += speed * Math.sin(angle);

        if (!onTrack && Math.abs(speed) > 0.1) {
            double pushBack = 0.8;
            x -= Math.signum(speed) * Math.cos(angle) * pushBack;
            y -= Math.signum(speed) * Math.sin(angle) * pushBack;
        }
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();

        g2.translate((int)x, (int)y);
        g2.rotate(angle);
        g2.translate(-BASE_WIDTH/2, -BASE_HEIGHT/2);

        if (sprite == null) {
            g2.dispose();
            return;
        }
        int imgW = BASE_WIDTH;
        int imgH = BASE_HEIGHT;
        g2.drawImage(sprite, 0, 0, imgW, imgH, null);
        g2.dispose();
    }


}