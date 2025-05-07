package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HostOrConnectScene implements Scene {

    private SceneManager sceneManager;
    private Button hostButton;
    private Button connectButton;
    private Button backButton;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;

    public HostOrConnectScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        
        // 创建按钮并设置点击事件
        hostButton = new Button(300, 250, 300, 50, "Devenir host", () -> {
            sceneManager.setScene(new MultiHostScene(sceneManager));
        });
        
        connectButton = new Button(300, 350, 300, 50, "Connecter a un host", () -> {
            sceneManager.setScene(new MultiConnectScene(sceneManager));
        });
        
        backButton = new Button(50, 500, 150, 40, "Retour", () -> {
            sceneManager.setScene(new MenuScene(sceneManager));
        });

        // Mouse Listener
        sceneManager.getPanel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    if (hostButton.contains(mousePoint)) {
                        hostButton.onClick();
                    } else if (connectButton.contains(mousePoint)) {
                        connectButton.onClick();
                    } else if (backButton.contains(mousePoint)) {
                        backButton.onClick();
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    if (hostButton.contains(mousePoint)) {
                        hostButton.setClicked(true);
                    } else if (connectButton.contains(mousePoint)) {
                        connectButton.setClicked(true);
                    } else if (backButton.contains(mousePoint)) {
                        backButton.setClicked(true);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                hostButton.setClicked(false);
                connectButton.setClicked(false);
                backButton.setClicked(false);
            }
        });

        // 添加鼠标移动监听器用于悬停效果
        sceneManager.getPanel().addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    hostButton.update(mousePoint);
                    connectButton.update(mousePoint);
                    backButton.update(mousePoint);
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
    public void render(Graphics g, int width, int height) {
        // 绘制背景
        g.setColor(new Color(50, 50, 100));
        g.fillRect(0, 0, width, height);

        // 使用alpha值创建Graphics2D
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // 绘制标题
        g2d.setColor(Color.WHITE);
        int titleFontSize = Math.min(width, height) / 20;
        g2d.setFont(new Font("Arial", Font.BOLD, titleFontSize));
        String title = "Mode Multijoueur";
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int titleWidth = titleMetrics.stringWidth(title);
        g2d.drawString(title, (width - titleWidth) / 2, height / 6);

        // 调整按钮大小和位置
        int buttonWidth = width / 3;
        int buttonHeight = height / 12;
        int buttonSpacing = height / 10;
        int buttonFontSize = Math.min(width, height) / 35;
        Font buttonFont = new Font("Arial", Font.BOLD, buttonFontSize);

        hostButton.setSize(buttonWidth, buttonHeight);
        hostButton.setLocation(width / 2 - buttonWidth / 2, height / 3);
        hostButton.setFont(buttonFont);

        connectButton.setSize(buttonWidth, buttonHeight);
        connectButton.setLocation(width / 2 - buttonWidth / 2, height / 3 + buttonHeight + buttonSpacing);
        connectButton.setFont(buttonFont);

        int backButtonWidth = width / 6;
        int backButtonHeight = height / 16;
        backButton.setSize(backButtonWidth, backButtonHeight);
        backButton.setLocation(width / 6 - backButtonWidth / 2, height * 4 / 5);
        backButton.setFont(buttonFont);

        // 渲染按钮
        hostButton.render(g2d);
        connectButton.render(g2d);
        backButton.render(g2d);

        g2d.dispose();
    }

    @Override
    public void dispose() {
        sceneManager.getPanel().removeMouseListener(sceneManager.getPanel().getMouseListeners()[0]);
        sceneManager.getPanel().removeMouseMotionListener(sceneManager.getPanel().getMouseMotionListeners()[0]);
    }
}
