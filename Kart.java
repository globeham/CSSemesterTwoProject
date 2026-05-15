/*
File will contain constructor for the karts in the game
 */

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

public class Kart {
	private double x, y;
	private double angle;
	private Color color;
	private static final int WIDTH = 40;
	private static final int HEIGHT = 20;
	private static final double SPEED = 4.0;
	private static final double TURN_SPEED = Math.PI / 18; // 10 degrees

	public Kart(int x, int y, Color color) {
		this.x = x;
		this.y = y;
		this.color = color;
		this.angle = 0;
	}

	public void moveForward() {
		x += SPEED * Math.cos(angle);
		y += SPEED * Math.sin(angle);
	}

	public void moveBackward() {
		x -= SPEED * Math.cos(angle);
		y -= SPEED * Math.sin(angle);
	}

	public void turnLeft() {
		angle -= TURN_SPEED;
	}

	public void turnRight() {
		angle += TURN_SPEED;
	}

	public void draw(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setColor(color);
		g2.translate((int)x + WIDTH/2, (int)y + HEIGHT/2);
		g2.rotate(angle);
		g2.translate(-WIDTH/2, -HEIGHT/2);
		g2.fillRect(0, 0, WIDTH, HEIGHT);
		g2.setColor(Color.BLACK);
		g2.fillOval(0, HEIGHT-5, 10, 10);
		g2.fillOval(WIDTH-10, HEIGHT-5, 10, 10);
		g2.dispose();
	}
}
