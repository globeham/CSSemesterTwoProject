/*
Constructor for all the Tracks in the game
 */
import java.awt.Color;
import java.awt.Graphics;

public class Track {
	public void draw(Graphics g) {
		// Draw a simple oval track
		g.setColor(Color.GRAY);
		g.fillOval(100, 100, 600, 400);
		g.setColor(Color.GREEN);
		g.fillOval(200, 200, 400, 200);
	}
}
