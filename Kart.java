/*
File will contain constructor for the karts in the game
 */

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

public class Kart {
	private double x, y;
	private double prevY;
	private double angle;
	private Color color;
	private static final int BASE_WIDTH = 50;
	private static final int BASE_HEIGHT = 28;
	private static final double SPEED = 3.0;
	private static final double TURN_SPEED = Math.PI / 36; // 5 degrees, smoother

	public Kart(int x, int y, Color color) {
		this.x = x;
		this.y = y;
		this.prevY = y;
		this.color = color;
		this.angle = 0;
	}

	public double getY() {
		return y;
	}

	public double getPrevY() {
		return prevY;
	}

	public void setPrevY(double py) {
		this.prevY = py;
	}

	public double getX() {
		return x;
	}

	public void setAngle(double a) {
		this.angle = a;
	}

	public double getAngle() {
		return angle;
	}

	public void moveForward(boolean onTrack) {
		double speed = onTrack ? SPEED : SPEED * 0.4;
		x += speed * Math.cos(angle);
		y += speed * Math.sin(angle);
	}

	public void moveBackward(boolean onTrack) {
		double speed = onTrack ? SPEED : SPEED * 0.4;
		x -= speed * Math.cos(angle);
		y -= speed * Math.sin(angle);
	}

	public void turnLeft() {
		angle -= TURN_SPEED;
	}

	public void turnRight() {
		angle += TURN_SPEED;
	}

	public void draw(Graphics g) {
		int panelW = 800, panelH = 600;
		try {
			panelW = g.getClipBounds().width;
			panelH = g.getClipBounds().height;
		} catch (Exception e) {}
		double scaleX = panelW / 800.0;
		double scaleY = panelH / 600.0;
		int scaledWidth = (int)(BASE_WIDTH * scaleX);
		int scaledHeight = (int)(BASE_HEIGHT * scaleY);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setColor(color);
		g2.translate((int)(x * scaleX) + scaledWidth/2, (int)(y * scaleY) + scaledHeight/2);
		g2.rotate(angle);
		g2.translate(-scaledWidth/2, -scaledHeight/2);
		// Main body
		g2.fillRoundRect(0, 0, scaledWidth, scaledHeight, 14, 14);
		// Windshield
		g2.setColor(new Color(180, 220, 255, 200));
		g2.fillRoundRect((int)(10*scaleX), (int)(4*scaleY), scaledWidth-(int)(20*scaleX), (int)(10*scaleY), 8, 8);
		// Spoiler
		g2.setColor(Color.DARK_GRAY);
		g2.fillRect(scaledWidth/2-12, scaledHeight-6, 24, 6);
		// Wheels
		g2.setColor(Color.BLACK);
		g2.fillOval(2, scaledHeight-8, 12, 12);
		g2.fillOval(scaledWidth-14, scaledHeight-8, 12, 12);
		g2.fillOval(2, -4, 12, 12);
		g2.fillOval(scaledWidth-14, -4, 12, 12);
		// Racing stripe
		g2.setColor(Color.WHITE);
		g2.fillRect(scaledWidth/2-4, 0, 8, scaledHeight);
		g2.dispose();
	}
}
/*
File will contain constructor for the karts in the game
 */

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

public class Kart {
	private double x, y;
	private double prevY;
	private double angle;
	private Color color;
	private static final int BASE_WIDTH = 50;
	private static final int BASE_HEIGHT = 28;
	private static final double SPEED = 3.0;
	private static final double TURN_SPEED = Math.PI / 36; // 5 degrees, smoother

	   public Kart(int x, int y, Color color) {
		   this.x = x;
		   this.y = y;
		   this.prevY = y;
		   this.color = color;
		   this.angle = 0;
	   }

	   public double getY() {
		   return y;
	   }

	   public double getPrevY() {
		   return prevY;
	   }

	   public void setPrevY(double py) {
		   this.prevY = py;
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
		   int panelW = 800, panelH = 600;
		   try {
			   panelW = g.getClipBounds().width;
			   panelH = g.getClipBounds().height;
		   } catch (Exception e) {}
		   double scaleX = panelW / 800.0;
		   double scaleY = panelH / 600.0;
		   int scaledWidth = (int)(BASE_WIDTH * scaleX);
		   int scaledHeight = (int)(BASE_HEIGHT * scaleY);
		   Graphics2D g2 = (Graphics2D) g.create();
		   g2.setColor(color);
		   g2.translate((int)(x * scaleX) + scaledWidth/2, (int)(y * scaleY) + scaledHeight/2);
		   g2.rotate(angle);
		   g2.translate(-scaledWidth/2, -scaledHeight/2);
		   // Main body
		   g2.fillRoundRect(0, 0, scaledWidth, scaledHeight, 14, 14);
		   // Windshield
		   g2.setColor(new Color(180, 220, 255, 200));
		   g2.fillRoundRect((int)(10*scaleX), (int)(4*scaleY), scaledWidth-(int)(20*scaleX), (int)(10*scaleY), 8, 8);
		   // Spoiler
		   g2.setColor(Color.DARK_GRAY);
		   g2.fillRect(scaledWidth/2-12, scaledHeight-6, 24, 6);
		   // Wheels
		   g2.setColor(Color.BLACK);
		   g2.fillOval(2, scaledHeight-8, 12, 12);
		   g2.fillOval(scaledWidth-14, scaledHeight-8, 12, 12);
		   g2.fillOval(2, -4, 12, 12);
		   g2.fillOval(scaledWidth-14, -4, 12, 12);
		   // Racing stripe
		   g2.setColor(Color.WHITE);
		   g2.fillRect(scaledWidth/2-4, 0, 8, scaledHeight);
		   g2.dispose();
	   }
}
