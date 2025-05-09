package SceneManager;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

public class IntroductionScene implements Scene {

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
                if (elapsed >= 1000) {
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
        g2d.drawString("Made by Me", 300, 300);

        g2d.dispose();
    }

    @Override
    public void dispose() {
        // Clean up resources if needed
    }
}
