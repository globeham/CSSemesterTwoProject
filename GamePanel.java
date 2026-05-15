/*
Manages pause menus and the game's window
 */

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

public class GamePanel extends JPanel implements KeyListener, Runnable {
	private Track track;
	private Kart[] karts;
	public static final int WIDTH = 800;
	public static final int HEIGHT = 600;
	private Thread gameThread;
	private boolean[] keys;

	public GamePanel() {
		setPreferredSize(new Dimension(WIDTH, HEIGHT));
		setBackground(Color.CYAN);
		track = new Track();
		karts = new Kart[] {
			new Kart(200, 300, Color.RED), // Player 1
			new Kart(250, 320, Color.BLUE) // Player 2
		};
		keys = new boolean[256];
		setFocusable(true);
		addKeyListener(this);
		gameThread = new Thread(this);
		gameThread.start();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		track.draw(g);
		for (Kart kart : karts) {
			kart.draw(g);
		}
	}

	@Override
	public void run() {
		while (true) {
			// Player 1 controls: W A S D
			if (keys[KeyEvent.VK_W]) karts[0].moveForward();
			if (keys[KeyEvent.VK_S]) karts[0].moveBackward();
			if (keys[KeyEvent.VK_A]) karts[0].turnLeft();
			if (keys[KeyEvent.VK_D]) karts[0].turnRight();
			// Player 2 controls: Arrow keys
			if (keys[KeyEvent.VK_UP]) karts[1].moveForward();
			if (keys[KeyEvent.VK_DOWN]) karts[1].moveBackward();
			if (keys[KeyEvent.VK_LEFT]) karts[1].turnLeft();
			if (keys[KeyEvent.VK_RIGHT]) karts[1].turnRight();
			repaint();
			try {
				Thread.sleep(16); // ~60 FPS
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {
		int code = e.getKeyCode();
		if (code < keys.length) keys[code] = true;
	}

	@Override
	public void keyReleased(KeyEvent e) {
		int code = e.getKeyCode();
		if (code < keys.length) keys[code] = false;
	}

	@Override
	public void keyTyped(KeyEvent e) {}
}
