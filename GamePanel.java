/*
Manages pause menus and the game's window
 */

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

public class GamePanel extends JPanel implements KeyListener, Runnable {
	private Track track;
	private Kart[] karts;
	public static final int BASE_WIDTH = 800;
	public static final int BASE_HEIGHT = 600;
	private Thread gameThread;
	private boolean[] keys;
	private boolean running = false;
	private boolean paused = false;
	private boolean showStartMenu = true;
	private int[] laps;
	private int totalLaps = 3;
	private int countdown = 4; // 3..2..1..GO
	private boolean raceStarted = false;

	public GamePanel() {
		setPreferredSize(new Dimension(BASE_WIDTH, BASE_HEIGHT));
		setBackground(Color.CYAN);
		track = new Track();
		karts = new Kart[2];
		// Center karts at start line
		int panelW = BASE_WIDTH, panelH = BASE_HEIGHT;
		for (int i = 0; i < karts.length; i++) {
			int[] pos = track.getStartPosition(panelW, panelH, i, karts.length);
			karts[i] = new Kart(pos[0], pos[1], i == 0 ? Color.RED : Color.BLUE);
			karts[i].setAngle(Math.PI / 2); // Point downwards
		}
		laps = new int[karts.length];
		keys = new boolean[256];
		setFocusable(true);
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
		Graphics scaledG = g.create();
		((Graphics)scaledG).translate(0, 0);
		((Graphics)scaledG).setClip(0, 0, w, h);
		((Graphics)scaledG).setColor(getBackground());
		((Graphics)scaledG).fillRect(0, 0, w, h);
		((Graphics)scaledG).dispose();
		Graphics2D g2 = (Graphics2D) g.create();
		g2.scale(scaleX, scaleY);
		if (showStartMenu) {
			drawStartMenu(g2);
			g2.dispose();
			return;
		}
		track.draw(g2);
		for (Kart kart : karts) {
			kart.draw(g2);
		}
		drawLapCounters(g2);
		if (!raceStarted && countdown > 0) {
			drawCountdown(g2);
		}
		if (paused) {
			drawPauseScreen(g2);
		}
		g2.dispose();
	}

	private void drawLapCounters(Graphics g) {
		g.setColor(Color.WHITE);
		g.setFont(g.getFont().deriveFont(24f));
		for (int i = 0; i < karts.length; i++) {
			g.drawString("P" + (i+1) + " Lap: " + laps[i] + "/" + totalLaps, 20, 40 + i*30);
		}
	}

	private void drawCountdown(Graphics g) {
		g.setColor(new Color(0,0,0,180));
		g.fillRect(0, 0, BASE_WIDTH, BASE_HEIGHT);
		g.setColor(Color.YELLOW);
		g.setFont(g.getFont().deriveFont(80f));
		String text = countdown > 1 ? String.valueOf(countdown-1) : "GO!";
		int strW = g.getFontMetrics().stringWidth(text);
		g.drawString(text, BASE_WIDTH/2 - strW/2, BASE_HEIGHT/2);
	}

	private void drawStartMenu(Graphics g) {
		g.setColor(new Color(0,0,0,180));
		g.fillRect(0, 0, BASE_WIDTH, BASE_HEIGHT);
		g.setColor(Color.WHITE);
		g.setFont(g.getFont().deriveFont(48f));
		g.drawString("Super Kart Racer 3000", 140, 200);
		g.setFont(g.getFont().deriveFont(28f));
		g.drawString("Press ENTER to Start", 230, 300);
	}

	private void drawPauseScreen(Graphics g) {
		g.setColor(new Color(0,0,0,180));
		g.fillRect(0, 0, BASE_WIDTH, BASE_HEIGHT);
		g.setColor(Color.WHITE);
		g.setFont(g.getFont().deriveFont(48f));
		g.drawString("Paused", 320, 250);
		g.setFont(g.getFont().deriveFont(28f));
		g.drawString("Press P to Resume", 260, 350);
	}

	@Override
	public void run() {
		long lastCountdown = System.currentTimeMillis();
		while (true) {
			if (!showStartMenu && !paused) {
				if (!raceStarted && countdown > 0) {
					long now = System.currentTimeMillis();
					if (now - lastCountdown >= 1000) {
						countdown--;
						lastCountdown = now;
						if (countdown == 0) {
							raceStarted = true;
						}
					}
				} else if (raceStarted) {
					int panelW = getWidth();
					int panelH = getHeight();
					for (int i = 0; i < karts.length; i++) {
						boolean onTrack = track.isOnTrack(karts[i].getX(), karts[i].getY(), panelW, panelH);
						if (i == 0) {
							if (keys[KeyEvent.VK_W]) karts[0].moveForward(onTrack);
							if (keys[KeyEvent.VK_S]) karts[0].moveBackward(onTrack);
							if (keys[KeyEvent.VK_A]) karts[0].turnLeft();
							if (keys[KeyEvent.VK_D]) karts[0].turnRight();
						} else if (i == 1) {
							if (keys[KeyEvent.VK_UP]) karts[1].moveForward(onTrack);
							if (keys[KeyEvent.VK_DOWN]) karts[1].moveBackward(onTrack);
							if (keys[KeyEvent.VK_LEFT]) karts[1].turnLeft();
							if (keys[KeyEvent.VK_RIGHT]) karts[1].turnRight();
						}
						// Lap detection (simple: crossing start line at y < BASE_HEIGHT/2)
						if (karts[i].getY() < BASE_HEIGHT/2 && karts[i].getPrevY() >= BASE_HEIGHT/2) {
							laps[i] = Math.min(laps[i]+1, totalLaps);
						}
						karts[i].setPrevY(karts[i].getY());
					}
				}
			}
			repaint();
			try {
				Thread.sleep(20); // Slightly slower for smoother feel
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {
		int code = e.getKeyCode();
		if (code < keys.length) keys[code] = true;
		if (showStartMenu && code == KeyEvent.VK_ENTER) {
			showStartMenu = false;
			paused = false;
			running = true;
		} else if (!showStartMenu && code == KeyEvent.VK_P) {
			paused = !paused;
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
		int code = e.getKeyCode();
		if (code < keys.length) keys[code] = false;
	}

	@Override
	public void keyTyped(KeyEvent e) {}
}
/*
Manages pause menus and the game's window
 */

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

public class GamePanel extends JPanel implements KeyListener, Runnable {
	private Track track;
	private Kart[] karts;
	public static final int BASE_WIDTH = 800;
	public static final int BASE_HEIGHT = 600;
	private Thread gameThread;
	private boolean[] keys;
	private boolean running = false;
	private boolean paused = false;
	private boolean showStartMenu = true;
	private int[] laps;
	private int totalLaps = 3;
	private int countdown = 4; // 3..2..1..GO
	private boolean raceStarted = false;

	   public GamePanel() {
		   setPreferredSize(new Dimension(BASE_WIDTH, BASE_HEIGHT));
		   setBackground(Color.CYAN);
		   track = new Track();
		   karts = new Kart[] {
			   new Kart(200, 300, Color.RED), // Player 1
			   new Kart(250, 320, Color.BLUE) // Player 2
		   };
		   laps = new int[karts.length];
		   keys = new boolean[256];
		   setFocusable(true);
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
		   Graphics scaledG = g.create();
		   ((Graphics)scaledG).translate(0, 0);
		   ((Graphics)scaledG).setClip(0, 0, w, h);
		   ((Graphics)scaledG).setColor(getBackground());
		   ((Graphics)scaledG).fillRect(0, 0, w, h);
		   ((Graphics)scaledG).dispose();
		   Graphics2D g2 = (Graphics2D) g.create();
		   g2.scale(scaleX, scaleY);
		   if (showStartMenu) {
			   drawStartMenu(g2);
			   g2.dispose();
			   return;
		   }
		   track.draw(g2);
		   for (Kart kart : karts) {
			   kart.draw(g2);
		   }
		   drawLapCounters(g2);
		   if (!raceStarted && countdown > 0) {
			   drawCountdown(g2);
		   }
		   if (paused) {
			   drawPauseScreen(g2);
		   }
		   g2.dispose();
	   }

	   private void drawLapCounters(Graphics g) {
		   g.setColor(Color.WHITE);
		   g.setFont(g.getFont().deriveFont(24f));
		   for (int i = 0; i < karts.length; i++) {
			   g.drawString("P" + (i+1) + " Lap: " + laps[i] + "/" + totalLaps, 20, 40 + i*30);
		   }
	   }

	   private void drawCountdown(Graphics g) {
		   g.setColor(new Color(0,0,0,180));
		   g.fillRect(0, 0, BASE_WIDTH, BASE_HEIGHT);
		   g.setColor(Color.YELLOW);
		   g.setFont(g.getFont().deriveFont(80f));
		   String text = countdown > 1 ? String.valueOf(countdown-1) : "GO!";
		   int strW = g.getFontMetrics().stringWidth(text);
		   g.drawString(text, BASE_WIDTH/2 - strW/2, BASE_HEIGHT/2);
	   }

	private void drawStartMenu(Graphics g) {
		g.setColor(new Color(0,0,0,180));
		g.fillRect(0, 0, BASE_WIDTH, BASE_HEIGHT);
		g.setColor(Color.WHITE);
		g.setFont(g.getFont().deriveFont(48f));
		g.drawString("Super Kart Racer 3000", 140, 200);
		g.setFont(g.getFont().deriveFont(28f));
		g.drawString("Press ENTER to Start", 230, 300);
	}

	private void drawPauseScreen(Graphics g) {
		g.setColor(new Color(0,0,0,180));
		g.fillRect(0, 0, BASE_WIDTH, BASE_HEIGHT);
		g.setColor(Color.WHITE);
		g.setFont(g.getFont().deriveFont(48f));
		g.drawString("Paused", 320, 250);
		g.setFont(g.getFont().deriveFont(28f));
		g.drawString("Press P to Resume", 260, 350);
	}

	   @Override
	   public void run() {
		   long lastCountdown = System.currentTimeMillis();
		   while (true) {
			   if (!showStartMenu && !paused) {
				   if (!raceStarted && countdown > 0) {
					   long now = System.currentTimeMillis();
					   if (now - lastCountdown >= 1000) {
						   countdown--;
						   lastCountdown = now;
						   if (countdown == 0) {
							   raceStarted = true;
						   }
					   }
				   } else if (raceStarted) {
					   for (int i = 0; i < karts.length; i++) {
						   if (i == 0) {
							   if (keys[KeyEvent.VK_W]) karts[0].moveForward();
							   if (keys[KeyEvent.VK_S]) karts[0].moveBackward();
							   if (keys[KeyEvent.VK_A]) karts[0].turnLeft();
							   if (keys[KeyEvent.VK_D]) karts[0].turnRight();
						   } else if (i == 1) {
							   if (keys[KeyEvent.VK_UP]) karts[1].moveForward();
							   if (keys[KeyEvent.VK_DOWN]) karts[1].moveBackward();
							   if (keys[KeyEvent.VK_LEFT]) karts[1].turnLeft();
							   if (keys[KeyEvent.VK_RIGHT]) karts[1].turnRight();
						   }
						   // Lap detection (simple: crossing start line at y < BASE_HEIGHT/2)
						   if (karts[i].getY() < BASE_HEIGHT/2 && karts[i].getPrevY() >= BASE_HEIGHT/2) {
							   laps[i] = Math.min(laps[i]+1, totalLaps);
						   }
						   karts[i].setPrevY(karts[i].getY());
					   }
				   }
			   }
			   repaint();
			   try {
				   Thread.sleep(20); // Slightly slower for smoother feel
			   } catch (InterruptedException e) {
				   e.printStackTrace();
			   }
		   }
	   }

	   @Override
	   public void keyPressed(KeyEvent e) {
		   int code = e.getKeyCode();
		   if (code < keys.length) keys[code] = true;
		   if (showStartMenu && code == KeyEvent.VK_ENTER) {
			   showStartMenu = false;
			   paused = false;
			   running = true;
		   } else if (!showStartMenu && code == KeyEvent.VK_P) {
			   paused = !paused;
		   }
	   }

	@Override
	public void keyReleased(KeyEvent e) {
		int code = e.getKeyCode();
		if (code < keys.length) keys[code] = false;
	}

	@Override
	public void keyTyped(KeyEvent e) {}
}
