
package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class GameScene implements Scene {
    private static int lastLogin = 0; // 0: single, 1: multi
    private SceneManager sceneManager;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private Rectangle backButton;
    
    public GameScene() {
        // 用于多人游戏的情况
    }
    
    public GameScene(SceneManager sceneManager) {
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
            alpha = Math.min(1f, elapsed / 1000f);
            if (alpha >= 1f) {
                fadeComplete = true;
            }
        }
        
        // 游戏逻辑更新
    }
    
    @Override
    public void render(Graphics g, int width, int height) {
        // 绘制背景
        g.setColor(new Color(20, 20, 20));
        g.fillRect(0, 0, width, height);
        
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        
        // 绘制游戏内容
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        g2d.drawString("Partie en cours...", width/4, height/2);

        backButton = new Rectangle(width / 10, height * 9 / 10, 150, 40);

        // 添加鼠标监听器
        sceneManager.getPanel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete && backButton.contains(e.getPoint())) {
                    if (lastLogin == 0) {
                        sceneManager.setScene(new MenuScene(sceneManager));
                    } else if (lastLogin == 1) {
                        sceneManager.setScene(new HostOrConnectScene(sceneManager));
                    }
                }
            }
        });
        
        // 绘制返回按钮
        g2d.setColor(new Color(100, 100, 200));
        g2d.fill(backButton);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        g2d.drawString("Retour", backButton.x + width/20, backButton.y + height/30);
        
        g2d.dispose();
    }
    
    @Override
    public void dispose() {
        // 移除鼠标监听器
        if (sceneManager != null) {
            MouseListener[] mouseListeners = sceneManager.getPanel().getMouseListeners();
            if (mouseListeners.length > 0) {
                sceneManager.getPanel().removeMouseListener(mouseListeners[0]);
            }
        }
    }

    public void updateLastLogin(int loginType) {
        lastLogin = loginType;
    }
}
