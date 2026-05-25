import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.JPanel;

public class GamePanel extends JPanel implements KeyListener, Runnable {
    private Track track;
    private Kart[] karts;
    public static final int BASE_WIDTH = 800;
    public static final int BASE_HEIGHT = 600;
    private Thread gameThread;
    private boolean[] keys;
    private boolean paused = false;
    private boolean showStartMenu = true;
    private int[] laps;
    private boolean[] canScoreLap;
    private int totalLaps = 3;
    private int countdown = 4;
    private boolean raceStarted = false;
    private boolean raceOver = false;
    private int winnerIndex = -1;

    public GamePanel() {
        setPreferredSize(new Dimension(BASE_WIDTH, BASE_HEIGHT));
        track = new Track();
        karts = new Kart[2];
        double startAngle = track.getStartAngle();
        for (int i = 0; i < karts.length; i++) {
            int[] start = track.getStartPosition(i, karts.length);
            karts[i] = new Kart(start[0], start[1], i == 0 ? Color.RED : Color.BLUE);
            karts[i].reset(start[0], start[1], startAngle);
        }
        laps = new int[karts.length];
        canScoreLap = new boolean[karts.length];
        for (int i = 0; i < karts.length; i++) canScoreLap[i] = true;
        keys = new boolean[256];
        setFocusable(true);
        requestFocusInWindow();
        addKeyListener(this);
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth();
        int h = getHeight();
        double scaleX = w / (double) BASE_WIDTH;
        double scaleY = h / (double) BASE_HEIGHT;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.scale(scaleX, scaleY);
        if (showStartMenu) {
            drawStartMenu(g2);
            g2.dispose();
            return;
        }
        track.draw(g2);
        for (Kart kart : karts) kart.draw(g2);
        drawLapCounters(g2);
        if (!raceStarted && countdown > 0) drawCountdown(g2);
        if (paused) drawPauseScreen(g2);
        if (raceOver) drawRaceEndScreen(g2);
        g2.dispose();
    }

    private void drawLapCounters(Graphics g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        String[] lines = new String[karts.length*2];
        for (int i=0;i<karts.length;i++) {
            lines[i*2] = "P" + (i+1) + " Lap: " + laps[i] + "/" + totalLaps + (karts[i].isBoostActive()?" BOOST":"");
            lines[i*2+1] = String.format("Speed: %.1f", karts[i].getSpeed());
        }
        int padding = 8;
        int maxW = 0;
        for (String s: lines) if (s!=null) maxW = Math.max(maxW, g.getFontMetrics().stringWidth(s));
        int boxW = maxW + padding*2;
        int boxH = g.getFontMetrics().getHeight()*lines.length + padding*2;
        int x = 12, y = 12;
        g.setColor(new Color(0,0,0,160)); g.fillRect(x,y,boxW,boxH);
        g.setColor(Color.WHITE);
        int lineH = g.getFontMetrics().getHeight();
        for (int i=0;i<lines.length;i++) g.drawString(lines[i], x+padding, y+padding + (i+1)*lineH - 6);
    }

    private void drawCountdown(Graphics g) {
        g.setColor(new Color(0,0,0,200)); g.fillRect(0,0,BASE_WIDTH,BASE_HEIGHT);
        g.setColor(Color.YELLOW); g.setFont(new Font("SansSerif", Font.BOLD, 72));
        String text = countdown>1?String.valueOf(countdown-1):"GO!";
        int sw = g.getFontMetrics().stringWidth(text);
        g.drawString(text, BASE_WIDTH/2 - sw/2, BASE_HEIGHT/2);
    }

    private void drawStartMenu(Graphics g) {
        g.setColor(new Color(0,0,0,200)); g.fillRect(0,0,BASE_WIDTH,BASE_HEIGHT);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 48));
        String title = "Super Kart Racer 3000"; int tw = g.getFontMetrics().stringWidth(title);
        g.drawString(title, BASE_WIDTH/2 - tw/2, 160);
        g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        String p1 = "Player1: W/S accelerate/brake, A/D steer";
        String p2 = "Player2: Up/Down accelerate/brake, Left/Right steer";
        g.drawString(p1, BASE_WIDTH/2 - g.getFontMetrics().stringWidth(p1)/2, 230);
        g.drawString(p2, BASE_WIDTH/2 - g.getFontMetrics().stringWidth(p2)/2, 260);
        String start = "Press ENTER to Start"; g.drawString(start, BASE_WIDTH/2 - g.getFontMetrics().stringWidth(start)/2, 320);
    }

    private void drawPauseScreen(Graphics g) {
        g.setColor(new Color(0,0,0,180)); g.fillRect(0,0,BASE_WIDTH,BASE_HEIGHT);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 48));
        String s = "Paused"; g.drawString(s, BASE_WIDTH/2 - g.getFontMetrics().stringWidth(s)/2, BASE_HEIGHT/2);
    }

    private void drawRaceEndScreen(Graphics g) {
        g.setColor(new Color(0,0,0,200)); g.fillRect(0,0,BASE_WIDTH,BASE_HEIGHT);
        g.setColor(Color.YELLOW); g.setFont(new Font("SansSerif", Font.BOLD, 52));
        String t = "Race Finished!"; g.drawString(t, BASE_WIDTH/2 - g.getFontMetrics().stringWidth(t)/2, 220);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.PLAIN, 32));
        String w = "Winner: Player " + (winnerIndex+1); g.drawString(w, BASE_WIDTH/2 - g.getFontMetrics().stringWidth(w)/2, 280);
        g.setFont(new Font("SansSerif", Font.PLAIN, 20)); String r = "Press R to Restart"; g.drawString(r, BASE_WIDTH/2 - g.getFontMetrics().stringWidth(r)/2, 340);
    }

    private void restartRace() {
        double startAngle = track.getStartAngle();
        for (int i = 0; i < karts.length; i++) {
            int[] start = track.getStartPosition(i, karts.length);
            karts[i].reset(start[0], start[1], startAngle);
            laps[i] = 0;
            canScoreLap[i] = true;
        }
        countdown = 4; raceStarted = false; raceOver = false; winnerIndex = -1; showStartMenu = true;
    }

    @Override
    public void run() {
        long lastCountdown = System.currentTimeMillis();
        while (true) {
            if (!showStartMenu && !paused && !raceOver) {
                if (!raceStarted && countdown > 0) {
                    long now = System.currentTimeMillis(); if (now - lastCountdown >= 1000) { countdown--; lastCountdown = now; if (countdown==0) raceStarted=true; }
                } else if (raceStarted) {
                    for (int i=0;i<karts.length;i++) {
                        boolean onTrack = track.isOnTrack(karts[i].getX(), karts[i].getY());
                        boolean acc=false, br=false, l=false, r=false;
                        if (i==0) { acc=keys[KeyEvent.VK_W]; br=keys[KeyEvent.VK_S]; l=keys[KeyEvent.VK_A]; r=keys[KeyEvent.VK_D]; }
                        else { acc=keys[KeyEvent.VK_UP]; br=keys[KeyEvent.VK_DOWN]; l=keys[KeyEvent.VK_LEFT]; r=keys[KeyEvent.VK_RIGHT]; }
                        double px = karts[i].getPrevX();
                        double py = karts[i].getPrevY();
                        karts[i].update(acc, br, l, r, onTrack);
                        if (track.checkBoost(karts[i].getX(), karts[i].getY())) karts[i].applyBoost();

                        double currentSigned = track.getStartLineSignedDistance(karts[i].getX(), karts[i].getY());
                        if (track.crossedStartLine(px, py, karts[i].getX(), karts[i].getY(), karts[i].getAngle())
                                && canScoreLap[i]) {
                            laps[i] = Math.min(laps[i] + 1, totalLaps);
                            canScoreLap[i] = false;
                            if (laps[i] >= totalLaps) { raceOver=true; winnerIndex=i; }
                        }
                        if (currentSigned <= 0) {
                            canScoreLap[i] = true;
                        }

                        karts[i].setPrevX(karts[i].getX());
                        karts[i].setPrevY(karts[i].getY());
                    }
                }
            }
            repaint();
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode(); if (code < keys.length) keys[code] = true;
        if (showStartMenu && code == KeyEvent.VK_ENTER) { showStartMenu=false; paused=false; }
        else if (!showStartMenu && code == KeyEvent.VK_P) paused=!paused;
        else if (raceOver && code == KeyEvent.VK_R) restartRace();
    }

    @Override
    public void keyReleased(KeyEvent e) { int code = e.getKeyCode(); if (code < keys.length) keys[code]=false; }

    @Override
    public void keyTyped(KeyEvent e) {}
}
