/*
File will be the main runner for the game
 */

import javax.swing.JFrame;

public class SuperKartRacer3000 {
	public static void main(String[] args) {
		JFrame frame = new JFrame("Super Kart Racer 3000");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
		frame.setResizable(true);
		GamePanel panel = new GamePanel();
		frame.add(panel);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
}
