import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HostOrConnectScene implements Scene {
    private SceneManager sceneManager;
    private Rectangle hostButton;
    private Rectangle connectButton;
    private Rectangle backButton;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;

    public HostOrConnectScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        hostButton = new Rectangle(300, 250, 300, 50);
        connectButton = new Rectangle(300, 350, 300, 50);
        backButton = new Rectangle(50, 500, 150, 40);
        
        // Mouse Listener
        sceneManager.getPanel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) {
                    if (hostButton.contains(e.getPoint())) {
                        sceneManager.setScene(new MultiHostScene(sceneManager));
                    } else if (connectButton.contains(e.getPoint())) {
                        sceneManager.setScene(new MultiConnectScene(sceneManager));
                    } else if (backButton.contains(e.getPoint())) {
                        System.exit(0); // Normallement retourner le main menu
                    }
                }
            }
        });
    }

    @Override
    public void init() {
        startTime = System.currentTimeMillis();
    }

    @Override
    public void update() {
        if (!fadeComplete) {
            long elapsed = System.currentTimeMillis() - startTime;
            alpha = Math.min(1f, elapsed / 1000f);  // 1秒内淡入
            if (alpha >= 1f) {
                fadeComplete = true;
            }
        }
    }

    @Override
    public void render(Graphics g) {
        // 绘制背景
        g.setColor(new Color(50, 50, 100));
        g.fillRect(0, 0, 800, 600);

        // 使用alpha值创建Graphics2D
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        
        // 绘制标题
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        g2d.drawString("Mode Multijoueur", 280, 150);
        
        // 绘制按钮
        g2d.setColor(new Color(100, 100, 200));
        g2d.fill(hostButton);
        g2d.fill(connectButton);
        g2d.fill(backButton);
        
        // 绘制按钮文字
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        g2d.drawString("Devenir host", hostButton.x + 100, hostButton.y + 35);
        g2d.drawString("Connecter a un host", connectButton.x + 60, connectButton.y + 35);
        
        // 绘制返回按钮
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        g2d.drawString("Retour", backButton.x + 45, backButton.y + 25);
        
        g2d.dispose();
    }

    @Override
    public void dispose() {
        // 移除鼠标监听器
        sceneManager.getPanel().removeMouseListener(sceneManager.getPanel().getMouseListeners()[0]);
    }
} 