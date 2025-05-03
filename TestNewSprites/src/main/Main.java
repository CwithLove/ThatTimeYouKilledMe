package main;

import javax.swing.JFrame;

public class Main {
    
    public static void main(String[] args) {
        JFrame window = new JFrame();
        
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);
        window.setTitle("Test 3D effect on 2D game");
        
        GamePanel gamePanel = new GamePanel();
        window.add(gamePanel);

        window.pack(); // Adjusts the window size to fit the preferred size of the panel

        window.setLocationRelativeTo(null); // Center the window on the screen
        window.setVisible(true);

        gamePanel.startGameThread(); // Start the game loop
    }
    
}
