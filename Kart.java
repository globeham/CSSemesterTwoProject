import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;

public class Kart {
    private double x, y;
    private double prevX, prevY;
    private double angle;
    private double speed;
    private Color color;
    private int boostTimer;
    private static final int BASE_WIDTH = 60;
    private static final int BASE_HEIGHT = 34;
    private static final double MAX_SPEED = 4.8;
    private static final double OFF_TRACK_MAX_SPEED = 1.6;
    private static final double ACCELERATION = 0.14;
    private static final double BRAKE_DECEL = 0.22;
    private static final double FRICTION = 0.06;
    private static final double TURN_SPEED = Math.PI / 48;
    private static final int BOOST_DURATION = 50;
    private static final double BOOST_MULTIPLIER = 1.8;

    public Kart(int x, int y, Color color) {
        this.x = x;
        this.y = y;
        this.prevX = x; this.prevY = y;
        this.color = color;
        this.angle = Math.PI / 2;
        this.speed = 0;
        this.boostTimer = 0;
    }

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
        // shadow
        g2.setColor(new Color(0,0,0,120));
        g2.fillOval((int)(x - BASE_WIDTH / 2 + 8), (int)(y - BASE_HEIGHT / 2 + 14), BASE_WIDTH, BASE_HEIGHT / 2);

        g2.translate((int)x, (int)y);
        g2.rotate(angle);
        g2.translate(-BASE_WIDTH / 2, -BASE_HEIGHT / 2);

        int bodyW = BASE_WIDTH;
        int bodyH = BASE_HEIGHT;
        GradientPaint gp = new GradientPaint(0, 0, color.brighter(), bodyW, bodyH, color.darker());
        g2.setPaint(gp);
        g2.fillRoundRect(0, 4, bodyW, bodyH - 8, 22, 22);

        g2.setColor(color.darker().darker());
        g2.fillRoundRect(6, bodyH / 2 - 8, 12, 16, 10, 10);
        g2.fillRoundRect(bodyW - 18, bodyH / 2 - 8, 12, 16, 10, 10);

        g2.setColor(new Color(255, 255, 255, 200));
        g2.fillRoundRect(10, 8, bodyW - 20, 10, 12, 12);
        g2.fillOval(bodyW - 24, 12, 10, 10);
        g2.fillOval(bodyW - 24, bodyH - 22, 10, 10);

        g2.setColor(new Color(255, 255, 255, 180));
        g2.fillRect(bodyW / 2 - 6, 14, 12, bodyH - 28);

        g2.setColor(color.darker());
        g2.fillRoundRect(bodyW - 22, 6, 16, 8, 8, 8);
        g2.fillRoundRect(bodyW - 30, 10, 24, 6, 6, 6);

        g2.setColor(new Color(0, 0, 0, 220));
        g2.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g2.drawRoundRect(0, 4, bodyW, bodyH - 8, 22, 22);
        g2.setStroke(new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g2.drawLine(10, 14, bodyW - 14, 14);
        g2.drawLine(10, bodyH - 14, bodyW - 14, bodyH - 14);

        drawWheel(g2, 8, bodyH - 10, 14);
        drawWheel(g2, bodyW - 22, bodyH - 10, 14);
        drawWheel(g2, 8, -2, 14);
        drawWheel(g2, bodyW - 22, -2, 14);

        if (isBoostActive()) {
            float cx = bodyW / 2f;
            float cy = bodyH / 2f;
            float radius = 32f;
            RadialGradientPaint rg = new RadialGradientPaint(new Point2D.Float(cx, cy), radius,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 190, 40, 200), new Color(255, 190, 40, 0)});
            g2.setPaint(rg);
            g2.fillOval((int)(cx - radius), (int)(cy - radius), (int)(radius * 2), (int)(radius * 2));
            g2.setColor(new Color(255, 180, 40, 140));
            for (int i = 0; i < 4; i++) {
                g2.fillRoundRect(-bodyW + i * 14, bodyH / 2 - 7, 18, 12, 10, 10);
            }
        }

        g2.dispose();
    }

    private void drawWheel(Graphics2D g2, int wx, int wy, int size) {
        g2.setColor(new Color(22, 22, 22));
        g2.fillOval(wx, wy, size, size);
        g2.setColor(new Color(90, 90, 90));
        g2.fillOval(wx + 3, wy + 3, size - 6, size - 6);
        g2.setColor(new Color(180, 180, 180, 220));
        g2.setStroke(new java.awt.BasicStroke(2f));
        g2.drawOval(wx + 4, wy + 4, size - 8, size - 8);
    }
}
