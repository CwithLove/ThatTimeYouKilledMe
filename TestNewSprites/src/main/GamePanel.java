package main;

import entity.Player;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;

public class GamePanel extends JPanel implements Runnable {
    final static int ORIGINAL_TILE_SIZE = 16; // 48*48 pixel tiles
    final static int SCALE = 3; // Scale factor for rendering

    public final int TILE_SIZE = ORIGINAL_TILE_SIZE * SCALE; // 48x48 pixel tiles
    public final int MAX_SCREEN_COL = 16; // Maximum columns on the screen
    public final int MAX_SCREEN_ROW = 12; // Maximum rows on the screen
    public final int SCREEN_WIDTH = TILE_SIZE * MAX_SCREEN_COL; // 768 pixels
    public final int SCREEN_HEIGHT = TILE_SIZE * MAX_SCREEN_ROW; // 576 pixels

    // FPS (Frames Per Second)
    final int FPS = 60; // 60 frames per second

    // For creating animations
    KeyHandler keyHandler = new KeyHandler();
    Thread gameThread;
    Player player = new Player(this, keyHandler, true); // Create a player object

    // Player position
    int playerX = 100;
    int playerY = 100;
    int playerSpeed = 4; // 4 pixels per frame
    int playerDirection = 0; // 0: up, 1: down, 2: left, 3: right

    public GamePanel() {
        this.addKeyListener(keyHandler);
        this.setPreferredSize(new Dimension(SCREEN_WIDTH, SCREEN_HEIGHT));
        this.setBackground(Color.WHITE);
        this.setDoubleBuffered(true);
        this.setFocusable(true); // Make the panel focusable to receive key events
    }

    public void startGameThread() {
        gameThread = new Thread(this);
        // player.setDefaultValues(); // Set default values for the player
        gameThread.start();
        // Trong start có gọi đến run
        // Trong run có game loop
    }

    @Override
    public void run() {
        // trong này sẽ là game loop
        double drawInterval = 1000000000 / FPS; // 1 second = 1000000000 nanoseconds
        double delta = 0; // Time difference between frames
        long lastTime = System.nanoTime(); // Get the current time in nanoseconds
        // double nextDrawTime = System.nanoTime() + drawInterval; // Calculate the next draw time
        long currentTime; // Variable to store the current time
        int drawCount = 0; // Counter for frames drawn
        long timer = 0;

        while (gameThread != null) {
            // long currentTime = System.nanoTime(); // Get the current time in nanoseconds 10000000 nanoseconds = 1 second
            // long currentTimeMillis = System.currentTimeMillis(); // Get the current time
            // in milliseconds 1000 milliseconds = 1 second

            /* -------------- Day la mot cach -------------- */
            // Update game state
            // update();

            // Render the game
            // repaint();

            // try {
                // double remainingTime = nextDrawTime - System.nanoTime(); // Calculate the remaining time until the next
                                                                         // draw
                // Thread.sleep((long) (remainingTime / 1000000)); // Sleep for the remaining time in milliseconds
                // if (remainingTime < 0) 
                // remainingTime = 0; // Ensure remaining time is not negative
            // } catch (InterruptedException e) {
                // e.printStackTrace();
            
            // Sleep for a short duration to control the frame rate
            /* ----------------------------------- */

            currentTime = System.nanoTime(); // Get the current time in nanoseconds
            delta += (double) (currentTime - lastTime) / drawInterval; // Calculate the time difference
            timer += (double) (currentTime - lastTime); // Calculate the time difference in milliseconds
            lastTime = currentTime; // Update the last time

            if (delta >= 1) { // If the time difference is greater than or equal to 1
                update(); // Update the game state
                repaint(); // Repaint the panel
                delta--; // Decrease delta by 1
                drawCount++; // Increment the draw count
            }

            if (timer >= 1000000000) { // If 1 second has passed
                System.out.println("FPS: " + drawCount); // Print the FPS
                drawCount = 0; // Reset the draw count
                timer = 0; // Reset the timer
            }


        }

    }

    public void update() {
        player.update(); // Update the player
        // Example of updating player position
        // playerX += playerSpeed; // Move right
        // playerY += playerSpeed; // Move down
        // playerX -= playerSpeed; // Move left
        // playerY -= playerSpeed; // Move up
        // playerX = Math.max(0, Math.min(playerX, SCREEN_WIDTH - TILE_SIZE));
        // playerY = Math.max(0, Math.min(playerY, SCREEN_HEIGHT - TILE_SIZE));
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        player.draw(g2); // Draw the player

        // g2.setColor(Color.WHITE);

        // g2.fillRect(playerX, playerY, TILE_SIZE, TILE_SIZE); // Example of drawing a tile

        g2.dispose(); // Equal to free() in C/C++ after malloc (here is Graphics2D)
        // Draw the game here
        // g.drawImage(image, x, y, this);
    }
}