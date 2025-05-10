package SceneManager;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.io.File;

public class IntroductionScene implements Scene {
    private BufferedImage logo;  
    private SceneManager sceneManager;
    private long startTime;
    private float alpha = 0.0f;

    private enum Phase {
        FADE_IN,
        HOLD,
        FADE_OUT,
        DONE
    }

    private Phase phase = Phase.FADE_IN;

    public IntroductionScene(SceneManager sceneManager) {
        try {
            logo = ImageIO.read(new File("res/logo/logo.png")); // Load your image here
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        this.sceneManager = sceneManager;
    }

    @Override
    public void init() {
        startTime = System.currentTimeMillis();
    }

    @Override
    public void update() {
        long elapsed = System.currentTimeMillis() - startTime;

        switch (phase) {
            case FADE_IN:
                alpha = Math.min(1f, elapsed / 1000f);
                if (elapsed >= 1000) {
                    phase = Phase.HOLD;
                    startTime = System.currentTimeMillis();
                }
                break;

            case HOLD:
                alpha = 1f;
                if (elapsed >= 1500) {
                    phase = Phase.FADE_OUT;
                    startTime = System.currentTimeMillis();
                }
                break;

            case FADE_OUT:
                alpha = Math.max(0f, 1f - (elapsed / 500f));
                if (elapsed >= 1000) {
                    phase = Phase.DONE;
                    sceneManager.setScene(new MenuScene(sceneManager));
                }
                break;

            case DONE:
                break;
        }
    }

    @Override
    public void render(Graphics g, int width, int height) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        String text = "Made by";
        Font font = g2d.getFont();
        int textWidth = g2d.getFontMetrics(font).stringWidth(text);
        int textHeight = g2d.getFontMetrics(font).getHeight();
        int x = (width - textWidth) / 2;
        int y = height * 2 / 5;
        g2d.drawString(text, x, y);

        // Draw the image with alpha composite
        if (logo != null) {
            int imgWidth = (int) (logo.getWidth() * 0.3);
            int imgHeight = (int) (logo.getHeight() * 0.3);
            int imgX = (width - imgWidth) / 2;
            int imgY = height * 2 / 5;
            g2d.drawImage(logo, imgX, imgY, imgWidth, imgHeight, null);
        }

        g2d.dispose();
    }

    @Override
    public void dispose() {
        // Clean up resources if needed
    }
}
