/*
 * @Author: ThearchyHelios work@thearchyhelios.com
 * @Date: 2025-05-05 23:41:00
 * @LastEditors: ThearchyHelios work@thearchyhelios.com
 * @LastEditTime: 2025-05-06 01:08:30
 * @FilePath: /ThatTimeYouKilledMe/TestSceneManage/MenuScene.java
 * @Description: 
 */
import java.awt.*;

public class MenuScene implements Scene {
    private SceneManager sceneManager;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;

    public MenuScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    @Override
    public void init() {
        startTime = System.currentTimeMillis();
    }

    @Override
    public void update() {
        if (!fadeComplete) {
            long elapsed = System.currentTimeMillis() - startTime;
            alpha = Math.min(1f, elapsed / 1000f);  // Fade in trong 1 giây
            if (alpha >= 1f) {
                fadeComplete = true; // Dừng cập nhật alpha sau khi hoàn tất fade in
            }
        }
        // Xử lý input nếu cần
        
        sceneManager.setScene(new GameScene());

        // Bạn có thể thêm xử lý input ở đây nếu cần
    }

    @Override
    public void render(Graphics g) {
        // Vẽ nền
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 800, 600);

        // Vẽ chữ có alpha
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2d.setColor(Color.YELLOW);
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        g2d.drawString("Main Menu", 300, 300);
        g2d.dispose();
    }

    @Override
    public void dispose() {
        // Cleanup nếu cần
    }
}
