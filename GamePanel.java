/**
 * Authors: Abhineet Bhardwaj, Rohan Nyman, Ishan Singh
 * Date: 6/9/2026
 * Function: Acts as the main runner and engine for the game. Handles the 
 * core game loop, state updates, player input listening, 
 * component synchronization, and screen rendering.
 */
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

public class GamePanel extends JPanel implements KeyListener, Runnable {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------
    public static final int BASE_WIDTH  = 800;
    public static final int BASE_HEIGHT = 600;
    private static final double KART_RADIUS = 36.0;
    private static final int    TOTAL_LAPS  = 3;

    private static final String[] KART_NAMES  = {"P1", "P2", "AI1", "AI2"};
    private static final Color[]  KART_COLORS = {
        new Color(220, 50,  50),
        new Color(50,  120, 220),
        new Color(30,  200, 70),
        new Color(230, 140, 20)
    };
    private static final String[] POS_SUFFIX = {"1ST", "2ND", "3RD", "4TH"};
    private static final Color[]  POS_COLORS = {
        new Color(255, 215, 0),
        new Color(210, 210, 210),
        new Color(205, 127, 50),
        new Color(180, 180, 180)
    };

    // -------------------------------------------------------------------------
    // Inner data classes
    // -------------------------------------------------------------------------

    private static class Slick {
        double x, y; long expiresAt; boolean active = true;
        Slick(double x, double y) { this.x=x; this.y=y; expiresAt=System.currentTimeMillis()+9000; }
    }

    private static class Particle {
        double x, y, vx, vy; int life, maxLife; Color color;
        Particle(double x, double y, double vx, double vy, int life, Color c) {
            this.x=x; this.y=y; this.vx=vx; this.vy=vy; this.life=life; this.maxLife=life; this.color=c;
        }
    }

    /** Centered on-screen notification that fades out. */
    private static class Notification {
        String text; Color color; int life, maxLife;
        Notification(String t, Color c, int frames) { text=t; color=c; life=maxLife=frames; }
    }

    // -------------------------------------------------------------------------
    // Core objects
    // -------------------------------------------------------------------------
    private Track track;
    private Kart[] karts;
    private AIController[] aiControllers;
    private Item[] itemBoxes;

    private final List<Slick>        slicks        = new CopyOnWriteArrayList<>();
    private final List<Particle>     particles     = new CopyOnWriteArrayList<>();
    private final List<Notification> notifications = new CopyOnWriteArrayList<>();

    // -------------------------------------------------------------------------
    // Game state
    // -------------------------------------------------------------------------
    private final boolean[] keys = new boolean[256];
    private volatile boolean paused        = false;
    private volatile boolean showStartMenu = true;
    private volatile boolean raceOver      = false;

    private volatile int     countdown      = 4;
    private volatile boolean raceStarted    = false;
    private long    lastCountdownMs;
    private long    raceStartTimeMs = 0;
    private long    raceEndTimeMs   = 0;

    // Lap / checkpoint
    private int[]       laps;
    private boolean[]   canScoreLap;
    private boolean[][] checkpointPassed;
    private int[]       kartNearestIdx;
    private Item.Type[] heldItems;
    private int[]       racePositions;    // rank (1-based) of each kart, updated each frame

    // Lap timing
    private long[]   lapStartTime;
    private long[]   bestLapTime;
    private String[] bestLapStr;

    // Results
    private final List<Integer> finishOrder = new CopyOnWriteArrayList<>();

    // Pause menu (0=resume,1=restart,2=quit)
    private int pauseMenuSel = 0;

    // Screen shake
    private int shakeFrames = 0;

    // Rubber-band: last time we auto-boosted each AI kart
    private long[] lastRubberBand;

    // AI difficulty selection (chosen on start menu)
    private AIController.Difficulty selectedDifficulty = AIController.Difficulty.NORMAL;

    // Threading
    private Thread gameThread;

    // =========================================================================
    // Constructor
    // =========================================================================
    public GamePanel() {
        setPreferredSize(new java.awt.Dimension(BASE_WIDTH, BASE_HEIGHT));
        track = new Track();
        double startAngle = track.getStartAngle();

        // Load sprite
        BufferedImage kartImg = loadKartImage();

        // Create 4 karts
        karts = new Kart[4];
        for (int i = 0; i < karts.length; i++) {
            int[] start = track.getStartPosition(i, karts.length);
            karts[i] = new Kart(start[0], start[1], KART_COLORS[i], i + 1);
            if (kartImg != null) karts[i].setSprite(kartImg);
            karts[i].reset(start[0], start[1], startAngle);
        }

        aiControllers = new AIController[]{
            new AIController(AIController.Difficulty.EASY),
            new AIController(AIController.Difficulty.NORMAL)
        };

        initTracking();
        placeItemBoxes();

        setFocusable(true);
        requestFocusInWindow();
        addKeyListener(this);
        gameThread = new Thread(this);
        gameThread.start();
    }

    private BufferedImage loadKartImage() {
        String wd = System.getProperty("user.dir");
        for (String path : new String[]{
                wd + File.separator + "f1carimage.png",
                wd + File.separator + "CSSemesterTwoProject" + File.separator + "f1carimage.png"}) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) { System.out.println("Sprite: " + path); return img; }
                } catch (IOException ignored) {}
            }
        }
        System.err.println("f1carimage.png not found — using built-in car renderer.");
        return null;
    }

    private void initTracking() {
        int n = karts.length;
        laps             = new int[n];
        canScoreLap      = new boolean[n];
        checkpointPassed = new boolean[n][track.getCheckpointIndices().length];
        kartNearestIdx   = new int[n];
        heldItems        = new Item.Type[n];
        racePositions    = new int[n];
        lapStartTime     = new long[n];
        bestLapTime      = new long[n];
        bestLapStr       = new String[n];
        lastRubberBand   = new long[n];
        Arrays.fill(canScoreLap, true);
        Arrays.fill(bestLapTime, Long.MAX_VALUE);
        Arrays.fill(bestLapStr, "--");
        for (int i = 0; i < n; i++) racePositions[i] = i + 1;
    }

    private void placeItemBoxes() {
        List<Point2D.Double> cl = track.getCenterline();
        int n = cl.size();
        itemBoxes = new Item[]{
            new Item(cl.get(n / 6).x,     cl.get(n / 6).y),
            new Item(cl.get(n * 2 / 6).x, cl.get(n * 2 / 6).y),
            new Item(cl.get(n * 3 / 6).x, cl.get(n * 3 / 6).y),
            new Item(cl.get(n * 4 / 6).x, cl.get(n * 4 / 6).y),
            new Item(cl.get(n * 5 / 6).x, cl.get(n * 5 / 6).y),
        };
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Scale to logical size
        double sx = getWidth() / (double) BASE_WIDTH;
        double sy = getHeight() / (double) BASE_HEIGHT;
        g2.scale(sx, sy);

        // Screen shake (in logical space)
        if (shakeFrames > 0) {
            double intensity = shakeFrames * 0.9;
            g2.translate((Math.random() - 0.5) * intensity, (Math.random() - 0.5) * intensity);
        }

        if (showStartMenu) { drawStartMenu(g2); g2.dispose(); return; }

        // World
        track.draw(g2);
        drawSlicks(g2);
        for (Item box : itemBoxes) box.draw(g2);
        drawParticles(g2);
        for (Kart k : karts) k.draw(g2);
        drawPositionBadges(g2);   // position badges above each kart

        // UI
        drawHUD(g2);
        drawMinimap(g2);
        drawNotifications(g2);

        if (!raceStarted && countdown > 0) drawCountdown(g2);
        if (paused)   drawPauseMenu(g2);
        if (raceOver) drawResults(g2);

        g2.dispose();
    }

    // ---- Position badges ----
    private void drawPositionBadges(Graphics2D g2) {
        // racePositions[i] = rank of kart i (1-based)
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < karts.length; i++) {
            int rank = racePositions[i] - 1; // 0-based
            String label = POS_SUFFIX[rank];
            double kx = karts[i].getX();
            double ky = karts[i].getY() - 44;
            int sw = fm.stringWidth(label);
            int bw = sw + 10, bh = 17;
            // Badge background
            g2.setColor(new Color(0, 0, 0, 175));
            g2.fillRoundRect((int) kx - bw / 2, (int) ky - bh + 3, bw, bh, 7, 7);
            // Badge border in position color
            g2.setColor(POS_COLORS[rank]);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect((int) kx - bw / 2, (int) ky - bh + 3, bw, bh, 7, 7);
            // Label text
            g2.setColor(POS_COLORS[rank]);
            g2.drawString(label, (int) kx - sw / 2, (int) ky);
            // Kart name below badge
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(new Color(255, 255, 255, 180));
            String nm = KART_NAMES[i];
            int nsw = g2.getFontMetrics().stringWidth(nm);
            g2.drawString(nm, (int) kx - nsw / 2, (int) ky + 11);
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            fm = g2.getFontMetrics();
        }
    }

    // ---- Oil slick patches ----
    private void drawSlicks(Graphics2D g2) {
        long now = System.currentTimeMillis();
        for (Slick s : slicks) {
            if (!s.active || now > s.expiresAt) continue;
            // Rainbow shimmer
            long t = now / 100;
            float hue = (t % 30) / 30f;
            Color shimmer = Color.getHSBColor(hue, 0.7f, 0.4f);
            g2.setColor(new Color(shimmer.getRed(), shimmer.getGreen(), shimmer.getBlue(), 160));
            g2.fillOval((int) s.x - 22, (int) s.y - 14, 44, 28);
            g2.setColor(new Color(15, 15, 15, 150));
            g2.fillOval((int) s.x - 20, (int) s.y - 12, 40, 24);
        }
    }

    // ---- Particles ----
    private void drawParticles(Graphics2D g2) {
        for (Particle p : particles) {
            if (p.life <= 0) continue;
            float alpha = (float) p.life / p.maxLife;
            int a = (int)(alpha * 200);
            g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), a));
            int sz = Math.max(2, (int)(alpha * 7));
            g2.fillOval((int) p.x - sz / 2, (int) p.y - sz / 2, sz, sz);
        }
    }

    // ---- Main HUD ----
    private void drawHUD(Graphics2D g2) {
        if (!raceStarted && countdown > 0) return; // don't clutter during countdown

        // Race timer (top center)
        if (raceStarted && raceStartTimeMs > 0) {
            long elapsed = System.currentTimeMillis() - raceStartTimeMs;
            String timer = formatTime(elapsed);
            g2.setFont(new Font("SansSerif", Font.BOLD, 18));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(timer);
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(BASE_WIDTH / 2 - tw / 2 - 10, 8, tw + 20, 26, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawString(timer, BASE_WIDTH / 2 - tw / 2, 27);
        }

        // Per-kart HUD panel (top-left)
        Font fnt = new Font("SansSerif", Font.BOLD, 14);
        g2.setFont(fnt);
        FontMetrics fm = g2.getFontMetrics();
        int lineH = fm.getHeight() + 2;
        int pad = 8;

        String[] lines = new String[karts.length];
        for (int i = 0; i < karts.length; i++) {
            String eff = karts[i].isBoostActive()  ? " ⚡BOOST"  :
                         karts[i].isShieldActive() ? " 🛡SHLD"  :
                         karts[i].isDrifting()     ? " ↺DRIFT"  :
                         karts[i].isSpinningOut()  ? " ✸SPIN"   : "";
            String item = heldItems[i] != null ? " [" + itemLabel(heldItems[i]) + "]" : "";
            lines[i] = KART_NAMES[i] + "  " + racePositions[i] + "/" + karts.length
                     + "  Lap:" + laps[i] + "/" + TOTAL_LAPS
                     + "  " + String.format("%.0f", Math.abs(karts[i].getSpeed()) * 30) + "km/h"
                     + eff + item + "  BL:" + bestLapStr[i];
        }

        int maxW = 0;
        for (String s : lines) maxW = Math.max(maxW, fm.stringWidth(s));
        int boxW = maxW + pad * 2;
        int boxH = lineH * lines.length + pad * 2;

        // Panel background with gradient
        GradientPaint gp = new GradientPaint(8, 8, new Color(0, 0, 0, 185),
                                             8, 8 + boxH, new Color(0, 0, 0, 130));
        g2.setPaint(gp);
        g2.fillRoundRect(8, 8, boxW, boxH, 10, 10);
        g2.setPaint(null);
        g2.setColor(new Color(255, 255, 255, 30));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(8, 8, boxW, boxH, 10, 10);

        // Lines
        for (int i = 0; i < lines.length; i++) {
            // Colored left bar
            g2.setColor(KART_COLORS[i]);
            g2.fillRoundRect(8, 8 + i * lineH + pad / 2, 4, lineH - 2, 2, 2);
            g2.setColor(KART_COLORS[i]);
            g2.drawString(lines[i], 8 + pad + 4, 8 + pad + (i + 1) * lineH - 6);
        }

        // Position ranking bar (bottom center)
        int[] order = computeRankOrder();
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        fm = g2.getFontMetrics();
        String[] parts = new String[karts.length];
        for (int r = 0; r < order.length; r++) {
            parts[r] = (r + 1) + "." + KART_NAMES[order[r]];
        }
        String full = "  " + String.join("   ", parts) + "  ";
        int fw = fm.stringWidth(full);
        int by = BASE_HEIGHT - 14;
        g2.setColor(new Color(0, 0, 0, 165));
        g2.fillRoundRect(BASE_WIDTH / 2 - fw / 2 - 4, by - fm.getHeight() + 2, fw + 8, fm.getHeight() + 4, 8, 8);
        // Draw each segment with its kart color
        int dx = BASE_WIDTH / 2 - fw / 2;
        String lead = "  ";
        g2.setColor(Color.WHITE);
        g2.drawString(lead, dx, by);
        dx += fm.stringWidth(lead);
        for (int r = 0; r < parts.length; r++) {
            g2.setColor(KART_COLORS[order[r]]);
            g2.drawString(parts[r], dx, by);
            dx += fm.stringWidth(parts[r]);
            if (r < parts.length - 1) {
                g2.setColor(new Color(180, 180, 180));
                g2.drawString("   ", dx, by);
                dx += fm.stringWidth("   ");
            }
        }

        // Controls reminder (bottom-right, first few seconds of race)
        if (raceStarted && raceStartTimeMs > 0 &&
                System.currentTimeMillis() - raceStartTimeMs < 8000) {
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(new Color(200, 200, 200, 160));
            String[] hints = {"P1: WASD  SPACE=item", "P2: Arrows  SHIFT=item"};
            int hy = BASE_HEIGHT - 38;
            fm = g2.getFontMetrics();
            for (String h : hints) {
                g2.drawString(h, BASE_WIDTH - fm.stringWidth(h) - 10, hy);
                hy += 14;
            }
        }
    }

    // ---- Minimap ----
    private void drawMinimap(Graphics2D g2) {
        int mmW = 140, mmH = 105;
        int mmX = BASE_WIDTH - mmW - 10, mmY = 50;

        // Background
        g2.setColor(new Color(0, 0, 0, 175));
        g2.fillRoundRect(mmX - 4, mmY - 4, mmW + 8, mmH + 8, 10, 10);
        g2.setColor(new Color(255, 255, 255, 25));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(mmX - 4, mmY - 4, mmW + 8, mmH + 8, 10, 10);

        List<Point2D.Double> cl = track.getCenterline();
        // Bounding box
        double minCx = Double.MAX_VALUE, maxCx = -Double.MAX_VALUE;
        double minCy = Double.MAX_VALUE, maxCy = -Double.MAX_VALUE;
        for (Point2D.Double p : cl) {
            minCx = Math.min(minCx, p.x); maxCx = Math.max(maxCx, p.x);
            minCy = Math.min(minCy, p.y); maxCy = Math.max(maxCy, p.y);
        }
        double scale = Math.min(mmW / (maxCx - minCx + 1), mmH / (maxCy - minCy + 1)) * 0.82;
        double offX  = mmX + (mmW - (maxCx - minCx) * scale) / 2;
        double offY  = mmY + (mmH - (maxCy - minCy) * scale) / 2;

        // Track ribbon
        g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(70, 70, 70));
        for (int i = 0; i < cl.size(); i++) {
            Point2D.Double a = cl.get(i), b = cl.get((i + 1) % cl.size());
            g2.drawLine(
                (int)((a.x - minCx) * scale + offX), (int)((a.y - minCy) * scale + offY),
                (int)((b.x - minCx) * scale + offX), (int)((b.y - minCy) * scale + offY));
        }
        // Centerline highlight
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(120, 120, 120));
        for (int i = 0; i < cl.size(); i++) {
            Point2D.Double a = cl.get(i), b = cl.get((i + 1) % cl.size());
            g2.drawLine(
                (int)((a.x - minCx) * scale + offX), (int)((a.y - minCy) * scale + offY),
                (int)((b.x - minCx) * scale + offX), (int)((b.y - minCy) * scale + offY));
        }

        // Start line dot
        Point2D.Double sp = cl.get(0);
        int sx2 = (int)((sp.x - minCx) * scale + offX);
        int sy2 = (int)((sp.y - minCy) * scale + offY);
        g2.setColor(Color.WHITE);
        g2.fillRect(sx2 - 3, sy2 - 5, 6, 10);

        // Kart dots with direction arrow
        g2.setStroke(new BasicStroke(1f));
        for (int i = 0; i < karts.length; i++) {
            int kx = (int)((karts[i].getX() - minCx) * scale + offX);
            int ky = (int)((karts[i].getY() - minCy) * scale + offY);
            g2.setColor(KART_COLORS[i]);
            g2.fillOval(kx - 4, ky - 4, 9, 9);
            // Direction tick
            double a = karts[i].getAngle();
            g2.setColor(Color.WHITE);
            g2.drawLine(kx, ky, kx + (int)(Math.cos(a) * 6), ky + (int)(Math.sin(a) * 6));
            // Position number
            g2.setFont(new Font("SansSerif", Font.BOLD, 8));
            g2.setColor(Color.BLACK);
            g2.drawString(String.valueOf(racePositions[i]), kx - 2, ky + 3);
        }
    }

    // ---- Notifications ----
    private void drawNotifications(Graphics2D g2) {
        if (notifications.isEmpty()) return;
        int ny = BASE_HEIGHT / 2 - 30;
        for (Notification n : notifications) {
            if (n.life <= 0) continue;
            float alpha = Math.min(1f, n.life / 35f);
            float scl = n.life > n.maxLife - 12 ? 1f + (float)(n.maxLife - n.life) / 12f * 0.25f : 1f;

            int fontSize = (int)(30 * scl);
            g2.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            FontMetrics fm = g2.getFontMetrics();
            int sw = fm.stringWidth(n.text);

            // Shadow backing
            g2.setColor(new Color(0, 0, 0, (int)(alpha * 170)));
            g2.fillRoundRect(BASE_WIDTH/2 - sw/2 - 12, ny - fm.getAscent() - 4,
                             sw + 24, fm.getHeight() + 6, 12, 12);
            // Text
            g2.setColor(new Color(n.color.getRed(), n.color.getGreen(), n.color.getBlue(),
                                  (int)(alpha * 255)));
            g2.drawString(n.text, BASE_WIDTH/2 - sw/2, ny);
            ny -= fm.getHeight() + 8;
        }
    }

    // ---- Countdown ----
    private void drawCountdown(Graphics g) {
        g.setColor(new Color(0, 0, 0, 155));
        g.fillRect(0, 0, BASE_WIDTH, BASE_HEIGHT);
        String text; Color color;
        if (countdown > 3)      { text = "3";   color = new Color(255, 80,  80); }
        else if (countdown > 2) { text = "2";   color = new Color(255, 200, 0);  }
        else if (countdown > 1) { text = "1";   color = new Color(80,  230, 80); }
        else                    { text = "GO!"; color = Color.WHITE; }
        g.setFont(new Font("SansSerif", Font.BOLD, 110));
        g.setColor(new Color(0, 0, 0, 200));
        int sw = g.getFontMetrics().stringWidth(text);
        g.drawString(text, BASE_WIDTH/2 - sw/2 + 3, BASE_HEIGHT/2 + 40);
        g.setColor(color);
        g.drawString(text, BASE_WIDTH/2 - sw/2, BASE_HEIGHT/2 + 37);
    }

    // ---- Start menu ----
    private void drawStartMenu(Graphics2D g2) {
        GradientPaint bgGrad = new GradientPaint(0, 0, new Color(8, 8, 25), 0, BASE_HEIGHT, new Color(20, 20, 50));
        g2.setPaint(bgGrad);
        g2.fillRect(0, 0, BASE_WIDTH, BASE_HEIGHT);
        g2.setPaint(null);

        // Title glow
        g2.setFont(new Font("SansSerif", Font.BOLD, 52));
        String title = "SUPER KART RACER 3000";
        int tw = g2.getFontMetrics().stringWidth(title);
        g2.setColor(new Color(255, 200, 0, 60));
        g2.drawString(title, BASE_WIDTH/2 - tw/2 + 2, 122);
        g2.setColor(new Color(255, 210, 40));
        g2.drawString(title, BASE_WIDTH/2 - tw/2, 120);

        // Colored kart name strip
        String[] names = {"P1", "P2", "AI1", "AI2"};
        g2.setFont(new Font("SansSerif", Font.BOLD, 22));
        int nx = BASE_WIDTH/2 - 120;
        for (int i = 0; i < names.length; i++) {
            g2.setColor(KART_COLORS[i]);
            g2.drawString(names[i], nx, 160);
            nx += 70;
        }

        // Instructions
        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g2.setColor(new Color(210, 210, 210));
        String[] info = {
            "Player 1 (RED):     W/S = gas/brake   A/D = steer   SPACE = use item",
            "Player 2 (BLUE):  Up/Down = gas/brake   Left/Right = steer   Shift = use item",
            "",
            "Race " + TOTAL_LAPS + " laps  ·  2 AI opponents  ·  Drift = hold turn at speed → BOOST on release",
            "Item boxes give: ⚡Speed Boost  ·  🛡Shield  ·  🛢Oil Slick (drops behind you)",
            "",
            "ESC / P = Pause",
        };
        int y = 205;
        for (String s : info) {
            int iw = g2.getFontMetrics().stringWidth(s);
            g2.drawString(s, BASE_WIDTH/2 - iw/2, y);
            y += 26;
        }

        // Difficulty selector
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        String[] diffs = {"1 EASY", "2 NORMAL", "3 HARD"};
        AIController.Difficulty[] dvals = {AIController.Difficulty.EASY, AIController.Difficulty.NORMAL, AIController.Difficulty.HARD};
        int dx2 = BASE_WIDTH/2 - 180;
        for (int i = 0; i < diffs.length; i++) {
            boolean sel = dvals[i] == selectedDifficulty;
            g2.setColor(sel ? new Color(255, 210, 40) : new Color(130, 130, 130));
            if (sel) {
                FontMetrics fmd = g2.getFontMetrics();
                g2.setColor(new Color(60, 60, 0, 120));
                g2.fillRoundRect(dx2 - 6, 385, fmd.stringWidth(diffs[i]) + 12, 22, 6, 6);
                g2.setColor(new Color(255, 210, 40));
            }
            g2.drawString(diffs[i], dx2, 400);
            dx2 += 130;
        }
        g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g2.setColor(new Color(150, 150, 150));
        String dh = "Press 1 / 2 / 3 to change AI difficulty";
        g2.drawString(dh, BASE_WIDTH/2 - g2.getFontMetrics().stringWidth(dh)/2, 420);

        // Flashing "PRESS ENTER"
        long flash = System.currentTimeMillis() / 600;
        if (flash % 2 == 0) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 28));
            g2.setColor(new Color(255, 230, 80));
            String ps = "▶  PRESS ENTER TO RACE  ◀";
            int pw = g2.getFontMetrics().stringWidth(ps);
            g2.drawString(ps, BASE_WIDTH/2 - pw/2, 450);
        }

        // Bottom credits
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.setColor(new Color(100, 100, 100));
        String cr = "Super Kart Racer 3000";
        g2.drawString(cr, BASE_WIDTH/2 - g2.getFontMetrics().stringWidth(cr)/2, BASE_HEIGHT - 15);
    }

    // ---- Pause menu ----
    private void drawPauseMenu(Graphics2D g2) {
        g2.setColor(new Color(0, 0, 0, 185));
        g2.fillRect(0, 0, BASE_WIDTH, BASE_HEIGHT);

        g2.setFont(new Font("SansSerif", Font.BOLD, 50));
        g2.setColor(Color.WHITE);
        String hdr = "PAUSED";
        g2.drawString(hdr, BASE_WIDTH/2 - g2.getFontMetrics().stringWidth(hdr)/2, 190);

        String[] opts = {"Resume (P)", "Restart", "Quit"};
        g2.setFont(new Font("SansSerif", Font.PLAIN, 28));
        int oy = 265;
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < opts.length; i++) {
            boolean sel = i == pauseMenuSel;
            if (sel) {
                g2.setColor(new Color(255, 210, 40));
                String cursor = "▶  " + opts[i];
                g2.drawString(cursor, BASE_WIDTH/2 - fm.stringWidth(cursor)/2, oy);
            } else {
                g2.setColor(new Color(160, 160, 160));
                g2.drawString(opts[i], BASE_WIDTH/2 - fm.stringWidth(opts[i])/2, oy);
            }
            oy += 50;
        }
        g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2.setColor(new Color(130, 130, 130));
        String nav = "↑↓ to navigate · Enter to select";
        g2.drawString(nav, BASE_WIDTH/2 - g2.getFontMetrics().stringWidth(nav)/2, oy + 16);
    }

    // ---- Results screen ----
    private void drawResults(Graphics2D g2) {
        GradientPaint bg = new GradientPaint(0, 0, new Color(5, 5, 20, 230),
                                             0, BASE_HEIGHT, new Color(20, 10, 40, 230));
        g2.setPaint(bg);
        g2.fillRect(0, 0, BASE_WIDTH, BASE_HEIGHT);
        g2.setPaint(null);

        g2.setFont(new Font("SansSerif", Font.BOLD, 54));
        g2.setColor(new Color(255, 215, 40));
        String t = "RACE FINISHED!";
        g2.drawString(t, BASE_WIDTH/2 - g2.getFontMetrics().stringWidth(t)/2, 135);

        int[] order = computeRankOrder();
        g2.setFont(new Font("SansSerif", Font.BOLD, 26));
        int ry = 200;
        for (int rank = 0; rank < order.length; rank++) {
            int ki = order[rank];
            Color rc = POS_COLORS[rank];
            String medal = rank == 0 ? "🥇" : rank == 1 ? "🥈" : rank == 2 ? "🥉" : "  ";
            String line = (rank + 1) + ".  " + KART_NAMES[ki]
                        + "        Best lap: " + bestLapStr[ki];
            // Highlighted background for 1st
            if (rank == 0) {
                g2.setColor(new Color(255, 215, 0, 30));
                FontMetrics fm = g2.getFontMetrics();
                g2.fillRoundRect(BASE_WIDTH/2 - fm.stringWidth(line)/2 - 20, ry - 24,
                                 fm.stringWidth(line) + 40, 34, 8, 8);
            }
            g2.setColor(rc);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(line, BASE_WIDTH/2 - fm.stringWidth(line)/2, ry);
            ry += 52;
        }

        // Race total time
        if (raceStartTimeMs > 0 && raceEndTimeMs > 0) {
            long totalMs = raceEndTimeMs - raceStartTimeMs;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g2.setColor(new Color(180, 180, 180));
            String ts = "Total race time: " + formatTime(totalMs);
            g2.drawString(ts, BASE_WIDTH/2 - g2.getFontMetrics().stringWidth(ts)/2, ry + 10);
        }

        // Prompt
        long flash = System.currentTimeMillis() / 700;
        if (flash % 2 == 0) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 20));
            g2.setColor(new Color(255, 255, 255, 200));
            String pr = "R = Restart   Q = Quit";
            g2.drawString(pr, BASE_WIDTH/2 - g2.getFontMetrics().stringWidth(pr)/2, BASE_HEIGHT - 35);
        }
    }

    // =========================================================================
    // Game Loop
    // =========================================================================

    @Override
    public void run() {
        lastCountdownMs = System.currentTimeMillis();
        List<Point2D.Double> centerline = track.getCenterline();
        int clSize = centerline.size();
        int[] cpIdx = track.getCheckpointIndices();
        long lastRubberBandCheck = 0;

        while (true) {
            long now = System.currentTimeMillis();

            if (!showStartMenu && !paused && !raceOver) {

                // ---- Countdown ----
                if (!raceStarted) {
                    if (now - lastCountdownMs >= 1000) {
                        countdown--;
                        lastCountdownMs = now;
                        if (countdown == 0) {
                            raceStarted = true;
                            raceStartTimeMs = System.currentTimeMillis();
                            Arrays.fill(lapStartTime, raceStartTimeMs);
                            notify("GO!", Color.WHITE, 90);
                        }
                    }
                } else {
                    // ---- Race tick ----

                    // Clean up expired objects
                    slicks.removeIf(s -> !s.active || now > s.expiresAt);
                    for (Particle p : particles) { p.x += p.vx; p.y += p.vy; p.life--; }
                    particles.removeIf(p -> p.life <= 0);
                    notifications.forEach(n -> n.life--);
                    notifications.removeIf(n -> n.life <= 0);
                    if (shakeFrames > 0) shakeFrames--;

                    // Update kart ranking
                    updateRacePositions(clSize);

                    // Rubber-banding: if an AI kart is >40% behind leader, give it a nudge
                    if (now - lastRubberBandCheck > 4000) {
                        lastRubberBandCheck = now;
                        long leaderProg = leaderProgress(clSize);
                        for (int i = 2; i < karts.length; i++) {
                            long aiProg = (long) laps[i] * clSize + kartNearestIdx[i];
                            if (leaderProg - aiProg > clSize * 0.45 && now - lastRubberBand[i] > 8000) {
                                karts[i].applyBoost();
                                lastRubberBand[i] = now;
                            }
                        }
                    }

                    // Update each kart
                    for (int i = 0; i < karts.length; i++) {
                        boolean onTrack = track.isOnTrack(karts[i].getX(), karts[i].getY());
                        boolean acc, br, left, right;

                        if (i == 0) {
                            acc = keys[KeyEvent.VK_W]; br = keys[KeyEvent.VK_S];
                            left = keys[KeyEvent.VK_A]; right = keys[KeyEvent.VK_D];
                        } else if (i == 1) {
                            acc = keys[KeyEvent.VK_UP]; br = keys[KeyEvent.VK_DOWN];
                            left = keys[KeyEvent.VK_LEFT]; right = keys[KeyEvent.VK_RIGHT];
                        } else {
                            boolean[] inp = aiControllers[i - 2].computeInputs(
                                karts[i].getX(), karts[i].getY(), karts[i].getAngle(), centerline);
                            acc = inp[0]; br = inp[1]; left = inp[2]; right = inp[3];
                        }

                        double px = karts[i].getPrevX(), py = karts[i].getPrevY();
                        boolean wasDrifting = karts[i].isDrifting();

                        karts[i].update(acc, br, left, right, onTrack);

                        // Wall push
                        double[] push = track.getWallPush(karts[i].getX(), karts[i].getY());

                        if (push[0] != 0 || push[1] != 0) karts[i].offsetPosition(push[0], push[1]);

                        // Boost pads
                        if (track.checkBoost(karts[i].getX(), karts[i].getY())) {
                            karts[i].applyBoost();
                            emitBoostParticles(karts[i]);
                            if (i < 2) notify("⚡ BOOST PAD!", new Color(255, 220, 40), 80);
                        }

                        // Drift → boost notification
                        if (wasDrifting && !karts[i].isDrifting() && karts[i].isBoostActive() && i < 2) {
                            notify("DRIFT BOOST!", new Color(255, 140, 0), 80);
                        }

                        // Boost trail particles
                        if (karts[i].isBoostActive() && Math.random() < 0.6) emitBoostParticles(karts[i]);

                        // Item pickup
                        for (Item box : itemBoxes) {
                            Item.Type got = box.tryPickup(karts[i].getX(), karts[i].getY());
                            if (got != null && heldItems[i] == null) {
                                heldItems[i] = got;
                                if (i < 2) notify("ITEM: " + itemLabel(got) + "!", new Color(120, 220, 255), 100);
                                if (i >= 2) aiUseItem(i);
                            }
                        }

                        // Oil slick contact
                        for (Slick s : slicks) {
                            if (!s.active) continue;
                            if (Math.hypot(karts[i].getX() - s.x, karts[i].getY() - s.y) < 28) {
                                s.active = false;
                                if (karts[i].isShieldActive()) {
                                    if (i < 2) notify("SHIELD BLOCKED!", new Color(80, 180, 255), 80);
                                } else {
                                    karts[i].applySpinOut();
                                    emitSparks(s.x, s.y, new Color(40, 40, 40));
                                    if (i < 2) notify("SPUN OUT!", new Color(255, 60, 60), 100);
                                }
                            }
                        }

// Nearest centerline index
                        int oldNearIdx = kartNearestIdx[i];
                        if (i >= 2) {
                            kartNearestIdx[i] = aiControllers[i - 2].getNearestIdx();
                        } else {
                            kartNearestIdx[i] = track.getNearestCenterlineIdx(karts[i].getX(), karts[i].getY());
                        }

// BULLETPROOF CHECKPOINT TRACKING
for (int ci = 0; ci < cpIdx.length; ci++) {
    int targetCp = cpIdx[ci];
    boolean hit = false;

    // Unified tracking: Check if the index advanced past the checkpoint
    // Normal forward progression
    if (oldNearIdx <= targetCp && kartNearestIdx[i] >= targetCp && (kartNearestIdx[i] - oldNearIdx) < 50) {
        hit = true;
    }
    // Handle track array wrap-around at the finish line (e.g., crossing index 599 -> 2)
    if (oldNearIdx > clSize * 0.85 && kartNearestIdx[i] < clSize * 0.15) {
        // If the checkpoint is near the start of the line, they crossed it during the wrap
        if (targetCp < clSize * 0.1 || targetCp > clSize * 0.9) {
            hit = true;
        }
    }

    // Apply sequential checkpoint validation
    if (hit) {
        // ONLY allow unlocking if it's the first checkpoint, OR the previous one was already unlocked
        if (ci == 0) {
            checkpointPassed[i][ci] = true;
        } else if (checkpointPassed[i][ci - 1]) {
            checkpointPassed[i][ci] = true;
        }
    }
}

// Lap scoring verification
                        boolean allCp = true;
                        for (boolean b : checkpointPassed[i]) {
                            if (!b) { allCp = false; break; }
                        }

                        // Determine if finish line was crossed
                        boolean crossedLine = false;
                        if (i >= 2) {
                            // AI crosses line when index rolls over from end of array back to 0
                            crossedLine = (oldNearIdx > clSize * 0.75 && kartNearestIdx[i] < clSize * 0.25);
                        } else {
                            // Human crosses line via physics geometry boundary
                            crossedLine = track.crossedStartLine(px, py, karts[i].getX(), karts[i].getY(), karts[i].getAngle());
                        }

                        if (allCp && canScoreLap[i] && crossedLine) {
                            laps[i]++;
                            canScoreLap[i] = false;
                            Arrays.fill(checkpointPassed[i], false);

                            // Lap time
                            long elapsed = now - lapStartTime[i];
                            if (elapsed < bestLapTime[i]) {
                                bestLapTime[i] = elapsed;
                                bestLapStr[i] = formatTime(elapsed);
                                if (i < 2) notify("NEW BEST LAP! " + bestLapStr[i], new Color(0, 230, 255), 140);
                            }
                            lapStartTime[i] = now;

                            // Notifications
                            if (i < 2) {
                                if (laps[i] >= TOTAL_LAPS) {
                                    notify("RACE COMPLETE!", new Color(255, 215, 40), 180);
                                } else if (laps[i] == TOTAL_LAPS - 1) {
                                    notify("FINAL LAP!", new Color(255, 60, 60), 130);
                                } else {
                                    notify("LAP " + laps[i] + "/" + TOTAL_LAPS, new Color(255, 230, 80), 110);
                                }
                            }

                            if (laps[i] >= TOTAL_LAPS) {
                                if (!finishOrder.contains(i)) {
                                    finishOrder.add(i);
                                }
                                if (finishOrder.size() == karts.length) {
                                    raceOver = true;
                                    raceEndTimeMs = now;
                                }
                            }
                        }
                        
                        // Safely re-arm the lap trigger mechanism only when past the first quarter of the track
                        if (!canScoreLap[i] && kartNearestIdx[i] > clSize * 0.25 && kartNearestIdx[i] < clSize * 0.75) {
                            canScoreLap[i] = true;
                        }

                        karts[i].setPrevX(karts[i].getX());
                        karts[i].setPrevY(karts[i].getY());
                    }

                    handleKartCollisions();
                }
            }
            repaint();
            try { Thread.sleep(16); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    // =========================================================================
    // Item use
    // =========================================================================

    private void aiUseItem(int idx) {
        Item.Type it = heldItems[idx];
        if (it == null) return;
        if (it == Item.Type.SPEED_BOOST) {
            // use boost when not in 1st place
            if (racePositions[idx] > 1) useItem(idx);
        } else if (it == Item.Type.OIL_SLICK) {
            // drop slick when a kart is within 200px behind
            for (int j = 0; j < karts.length; j++) {
                if (j == idx) continue;
                double dist = Math.hypot(karts[idx].getX() - karts[j].getX(),
                                         karts[idx].getY() - karts[j].getY());
                long myProg  = (long) laps[idx] * track.getCenterline().size() + kartNearestIdx[idx];
                long thProg  = (long) laps[j]   * track.getCenterline().size() + kartNearestIdx[j];
                if (dist < 200 && thProg < myProg) { useItem(idx); return; }
            }
        } else {
            useItem(idx);
        }
    }

    private void useItem(int idx) {
        Item.Type it = heldItems[idx];
        if (it == null) return;
        heldItems[idx] = null;
        switch (it) {
            case SPEED_BOOST:
                karts[idx].applyBoost();
                emitBoostParticles(karts[idx]);
                if (idx < 2) notify("⚡ SPEED BOOST!", new Color(255, 220, 40), 90);
                break;
            case SHIELD:
                karts[idx].applyShield();
                if (idx < 2) notify("🛡 SHIELD!", new Color(80, 180, 255), 90);
                break;
            case OIL_SLICK:
                double a = karts[idx].getAngle();
                slicks.add(new Slick(karts[idx].getX() - Math.cos(a) * 52,
                                     karts[idx].getY() - Math.sin(a) * 52));
                if (idx < 2) notify("🛢 OIL DROPPED!", new Color(160, 200, 60), 90);
                break;
        }
    }

    // =========================================================================
    // Physics helpers
    // =========================================================================

    private void handleKartCollisions() {
        double minDist = KART_RADIUS * 2;
        for (int i = 0; i < karts.length; i++) {
            for (int j = i + 1; j < karts.length; j++) {
                double dx = karts[j].getX() - karts[i].getX();
                double dy = karts[j].getY() - karts[i].getY();
                double dist = Math.hypot(dx, dy);
                if (dist > 0 && dist < minDist) {
                    double overlap = (minDist - dist) / 2.0;
                    double nx = dx / dist, ny = dy / dist;
                    karts[i].offsetPosition(-nx * overlap, -ny * overlap);
                    karts[j].offsetPosition( nx * overlap,  ny * overlap);
                    // Screen shake only when closing speed is high and player involved
                    double cs = Math.abs(karts[i].getSpeed()) + Math.abs(karts[j].getSpeed());
                    if (cs > 3.5 && (i < 2 || j < 2)) shakeFrames = Math.max(shakeFrames, 7);
                    // Sparks
                    double mx = (karts[i].getX() + karts[j].getX()) / 2;
                    double my = (karts[i].getY() + karts[j].getY()) / 2;
                    emitSparks(mx, my, new Color(255, 230, 100));
                }
            }
        }
    }

    private void emitBoostParticles(Kart kart) {
        double a = kart.getAngle() + Math.PI;
        for (int k = 0; k < 3; k++) {
            double ang = a + (Math.random() - 0.5) * 0.9;
            double spd = 2 + Math.random() * 2.5;
            particles.add(new Particle(kart.getX(), kart.getY(),
                Math.cos(ang) * spd, Math.sin(ang) * spd,
                10 + (int)(Math.random() * 10),
                new Color(255, 130 + (int)(Math.random() * 100), 0)));
        }
    }

    private void emitSparks(double x, double y, Color c) {
        for (int k = 0; k < 8; k++) {
            double ang = Math.random() * 2 * Math.PI;
            double spd = 1 + Math.random() * 4;
            particles.add(new Particle(x, y, Math.cos(ang)*spd, Math.sin(ang)*spd,
                                       8 + (int)(Math.random()*8), c));
        }
    }

    // =========================================================================
    // Ranking helpers
    // =========================================================================

    private void updateRacePositions(int clSize) {
        int[] order = computeRankOrder();
        for (int rank = 0; rank < order.length; rank++) racePositions[order[rank]] = rank + 1;
    }

    private int[] computeRankOrder() {
        int n = track.getCenterline().size();
        long[] prog = new long[karts.length];
        for (int i = 0; i < karts.length; i++) prog[i] = (long) laps[i] * n + kartNearestIdx[i];
        Integer[] idx = {0, 1, 2, 3};
        Arrays.sort(idx, (a, b) -> Long.compare(prog[b], prog[a]));
        return new int[]{idx[0], idx[1], idx[2], idx[3]};
    }

    private long leaderProgress(int clSize) {
        long best = 0;
        for (int i = 0; i < karts.length; i++) {
            long p = (long) laps[i] * clSize + kartNearestIdx[i];
            if (p > best) best = p;
        }
        return best;
    }

    // =========================================================================
    // Misc helpers
    // =========================================================================

    private void notify(String text, Color color, int frames) {
        notifications.add(new Notification(text, color, frames));
    }

    private String itemLabel(Item.Type t) {
        switch (t) {
            case SPEED_BOOST: return "BOOST";
            case SHIELD:      return "SHIELD";
            case OIL_SLICK:   return "OIL SLICK";
            default: return "?";
        }
    }

    private String formatTime(long ms) {
        long m = ms / 60000;
        long s = (ms % 60000) / 1000;
        long c = (ms % 1000) / 10;
        return m > 0 ? String.format("%d:%02d.%02d", m, s, c) : String.format("%d.%02ds", s, c);
    }

    private void restartRace() {
        double startAngle = track.getStartAngle();
        for (int i = 0; i < karts.length; i++) {
            int[] start = track.getStartPosition(i, karts.length);
            karts[i].reset(start[0], start[1], startAngle);
        }
        slicks.clear(); particles.clear(); notifications.clear(); finishOrder.clear();
        Arrays.fill(keys, false);
        initTracking();
        placeItemBoxes();
        shakeFrames = 0;
        countdown = 4; raceStarted = false; raceOver = false;
        raceStartTimeMs = 0; raceEndTimeMs = 0;
        showStartMenu = true; paused = false; pauseMenuSel = 0;
    }

    // =========================================================================
    // Input
    // =========================================================================

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code < keys.length) keys[code] = true;

        if (showStartMenu) {
            if (code == KeyEvent.VK_1) { selectedDifficulty = AIController.Difficulty.EASY; return; }
            if (code == KeyEvent.VK_2) { selectedDifficulty = AIController.Difficulty.NORMAL; return; }
            if (code == KeyEvent.VK_3) { selectedDifficulty = AIController.Difficulty.HARD; return; }
            if (code == KeyEvent.VK_ENTER) {
                aiControllers[0] = new AIController(selectedDifficulty);
                aiControllers[1] = new AIController(selectedDifficulty);
                showStartMenu = false;
                lastCountdownMs = System.currentTimeMillis();
            }
            return;
        }
        if (raceOver) {
            if (code == KeyEvent.VK_R) restartRace();
            if (code == KeyEvent.VK_Q) System.exit(0);
            return;
        }
        if (paused) {
            if (code == KeyEvent.VK_UP)   { pauseMenuSel = (pauseMenuSel - 1 + 3) % 3; return; }
            if (code == KeyEvent.VK_DOWN) { pauseMenuSel = (pauseMenuSel + 1) % 3; return; }
            if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_P || code == KeyEvent.VK_ESCAPE) {
                switch (pauseMenuSel) {
                    case 0: paused = false; break;
                    case 1: restartRace(); break;
                    case 2: System.exit(0);
                }
                return;
            }
        }
        if (code == KeyEvent.VK_P || code == KeyEvent.VK_ESCAPE) { paused = !paused; pauseMenuSel = 0; }
        if (raceStarted && !paused) {
            if (code == KeyEvent.VK_SPACE) useItem(0);
            if (code == KeyEvent.VK_SHIFT) useItem(1);
        }
    }

    @Override public void keyReleased(KeyEvent e) { int c = e.getKeyCode(); if (c < keys.length) keys[c] = false; }
    @Override public void keyTyped(KeyEvent e) {}
}
