/*
Constructor for all the Tracks in the game
 */
import java.awt.Color;
import java.awt.Graphics;

public class Track {
/*
Constructor for all the Tracks in the game
 */
import java.awt.Color;
import java.awt.Graphics;

public class Track {
   // Returns the center of the track's start line (top center of the oval)
   public int[] getStartPosition(int panelW, int panelH, int playerIdx, int totalPlayers) {
	   int outerW = (int)(900.0 / 800 * GamePanel.BASE_WIDTH);
	   int outerH = (int)(600.0 / 600 * GamePanel.BASE_HEIGHT);
	   double scaleX = panelW / (double)GamePanel.BASE_WIDTH;
	   double scaleY = panelH / (double)GamePanel.BASE_HEIGHT;
	   int scaledOuterW = (int)(outerW * scaleX);
	   int scaledOuterH = (int)(outerH * scaleY);
	   int outerX = (panelW - scaledOuterW) / 2;
	   int outerY = (panelH - scaledOuterH) / 2;
	   // Spread players horizontally along the start line
	   int spacing = scaledOuterW / (totalPlayers + 1);
	   int x = outerX + spacing * (playerIdx + 1);
	   int y = outerY + 10; // 10px below the top of the track
	   return new int[]{x, y};
   }

   // Returns true if the given (x, y) is on the track (between outer and inner ovals)
   public boolean isOnTrack(double x, double y, int panelW, int panelH) {
	   int outerW = (int)(900.0 / 800 * GamePanel.BASE_WIDTH);
	   int outerH = (int)(600.0 / 600 * GamePanel.BASE_HEIGHT);
	   int innerW = (int)(600.0 / 800 * GamePanel.BASE_WIDTH);
	   int innerH = (int)(350.0 / 600 * GamePanel.BASE_HEIGHT);
	   double scaleX = panelW / (double)GamePanel.BASE_WIDTH;
	   double scaleY = panelH / (double)GamePanel.BASE_HEIGHT;
	   int scaledOuterW = (int)(outerW * scaleX);
	   int scaledOuterH = (int)(outerH * scaleY);
	   int scaledInnerW = (int)(innerW * scaleX);
	   int scaledInnerH = (int)(innerH * scaleY);
	   int outerX = (panelW - scaledOuterW) / 2;
	   int outerY = (panelH - scaledOuterH) / 2;
	   int innerX = (panelW - scaledInnerW) / 2;
	   int innerY = (panelH - scaledInnerH) / 2;
	   // Check if inside outer oval
	   double ox = x - (outerX + scaledOuterW / 2.0);
	   double oy = y - (outerY + scaledOuterH / 2.0);
	   double outerNorm = (ox * ox) / ((scaledOuterW / 2.0) * (scaledOuterW / 2.0)) + (oy * oy) / ((scaledOuterH / 2.0) * (scaledOuterH / 2.0));
	   // Check if outside inner oval
	   double ix = x - (innerX + scaledInnerW / 2.0);
	   double iy = y - (innerY + scaledInnerH / 2.0);
	   double innerNorm = (ix * ix) / ((scaledInnerW / 2.0) * (scaledInnerW / 2.0)) + (iy * iy) / ((scaledInnerH / 2.0) * (scaledInnerH / 2.0));
	   return (outerNorm <= 1.0) && (innerNorm >= 1.0);
   }
   public void draw(Graphics g) {
	   // Draw a scalable and centered oval track
	   int panelW = 800, panelH = 600;
	   try {
		   panelW = g.getClipBounds().width;
		   panelH = g.getClipBounds().height;
	   } catch (Exception e) {}
	}

	   int outerW = (int)(900.0 / 800 * GamePanel.BASE_WIDTH);
	   int outerH = (int)(600.0 / 600 * GamePanel.BASE_HEIGHT);
	   int innerW = (int)(600.0 / 800 * GamePanel.BASE_WIDTH);
	   int innerH = (int)(350.0 / 600 * GamePanel.BASE_HEIGHT);

	   // Scale to panel size
	   double scaleX = panelW / (double)GamePanel.BASE_WIDTH;
	   double scaleY = panelH / (double)GamePanel.BASE_HEIGHT;
	   int scaledOuterW = (int)(outerW * scaleX);
	   int scaledOuterH = (int)(outerH * scaleY);
	   int scaledInnerW = (int)(innerW * scaleX);
	   int scaledInnerH = (int)(innerH * scaleY);

	   // Center the track
	   int outerX = (panelW - scaledOuterW) / 2;
	   int outerY = (panelH - scaledOuterH) / 2;
	   int innerX = (panelW - scaledInnerW) / 2;
	   int innerY = (panelH - scaledInnerH) / 2;

	   g.setColor(Color.DARK_GRAY);
	   g.fillOval(outerX, outerY, scaledOuterW, scaledOuterH);
	   g.setColor(new Color(34, 139, 34));
	   g.fillOval(innerX, innerY, scaledInnerW, scaledInnerH);
   }
}
