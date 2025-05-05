import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

public class GameScene implements Scene {
    private GamePanel panel;
    private long startTime;
    private float alpha = 0.0f;
    public boolean fadeComplete = false;

    public GameScene() {
        this.panel = new GamePanel();
    }

    @Override
    public void init() {
        startTime = System.currentTimeMillis();
    }

    @Override
    public void update() {
        if (!fadeComplete) {
            long elapsed = System.currentTimeMillis() - startTime;
            alpha = Math.min(1f, elapsed / 1000f); // Fade in trong 1 giây
            if (alpha >= 1f) {
                fadeComplete = true; // Dừng cập nhật alpha sau khi hoàn tất fade in
            }
        }
    }

    @Override
    public void render(Graphics g) {
        // Vẽ scene chính
        BufferedImage buffer = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buffer.createGraphics();
        panel.paint(g2); // vẽ toàn bộ GamePanel vào buffer

        Graphics2D gMain = (Graphics2D) g.create();
        gMain.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        gMain.drawImage(buffer, 0, 0, null);
        gMain.dispose();
    }

    @Override
    public void dispose() {
        // Cleanup nếu cần
    }
    

}
