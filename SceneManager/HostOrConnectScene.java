package SceneManager;

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
    
    // 添加鼠标悬停和点击效果的变量
    private Rectangle hoverButton = null;
    private Rectangle clickButton = null;
    private long clickTime = 0;

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
                        clickButton = hostButton;
                        clickTime = System.currentTimeMillis();
                        sceneManager.setScene(new MultiHostScene(sceneManager));
                    } else if (connectButton.contains(e.getPoint())) {
                        clickButton = connectButton;
                        clickTime = System.currentTimeMillis();
                        sceneManager.setScene(new MultiConnectScene(sceneManager));
                    } else if (backButton.contains(e.getPoint())) {
                        clickButton = backButton;
                        clickTime = System.currentTimeMillis();
                        sceneManager.setScene(new MenuScene(sceneManager));
                    }
                }
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                if (fadeComplete) {
                    if (hostButton.contains(e.getPoint())) {
                        clickButton = hostButton;
                        clickTime = System.currentTimeMillis();
                    } else if (connectButton.contains(e.getPoint())) {
                        clickButton = connectButton;
                        clickTime = System.currentTimeMillis();
                    } else if (backButton.contains(e.getPoint())) {
                        clickButton = backButton;
                        clickTime = System.currentTimeMillis();
                    }
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                clickButton = null;
            }
        });
        
        // 添加鼠标移动监听器用于悬停效果
        sceneManager.getPanel().addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (fadeComplete) {
                    hoverButton = null;
                    if (hostButton.contains(e.getPoint())) {
                        hoverButton = hostButton;
                    } else if (connectButton.contains(e.getPoint())) {
                        hoverButton = connectButton;
                    } else if (backButton.contains(e.getPoint())) {
                        hoverButton = backButton;
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
        g2d.drawString(title, (width - titleWidth) / 2, height/6);
        
        // 动态调整按钮大小
        int buttonWidth = width / 3;
        int buttonHeight = height / 12;
        int buttonSpacing = height / 10;
        
        // 设置主机按钮大小和位置
        hostButton.setSize(buttonWidth, buttonHeight);
        hostButton.setLocation(width/2 - buttonWidth/2, height/3);
        
        // 设置连接按钮大小和位置
        connectButton.setSize(buttonWidth, buttonHeight);
        connectButton.setLocation(width/2 - buttonWidth/2, height/3 + buttonHeight + buttonSpacing);
        
        // 设置返回按钮大小和位置
        int backButtonWidth = width / 6;
        int backButtonHeight = height / 16;
        backButton.setSize(backButtonWidth, backButtonHeight);
        backButton.setLocation(width/6 - backButtonWidth/2, height * 4/5);

        // 绘制主机按钮
        if (clickButton == hostButton) {
            g2d.setColor(new Color(70, 70, 150)); // 点击颜色
        } else if (hoverButton == hostButton) {
            g2d.setColor(new Color(130, 130, 230)); // 悬停颜色
        } else {
            g2d.setColor(new Color(100, 100, 200)); // 正常颜色
        }
        g2d.fill(hostButton);
        
        // 如果是点击状态，绘制一个轻微的阴影效果
        if (clickButton == hostButton) {
            g2d.setColor(new Color(0, 0, 0, 50));
            g2d.fillRect(hostButton.x + 2, hostButton.y + 2, hostButton.width - 4, hostButton.height - 4);
        }
        
        // 绘制连接按钮
        if (clickButton == connectButton) {
            g2d.setColor(new Color(70, 70, 150)); // 点击颜色
        } else if (hoverButton == connectButton) {
            g2d.setColor(new Color(130, 130, 230)); // 悬停颜色
        } else {
            g2d.setColor(new Color(100, 100, 200)); // 正常颜色
        }
        g2d.fill(connectButton);
        
        // 如果是点击状态，绘制一个轻微的阴影效果
        if (clickButton == connectButton) {
            g2d.setColor(new Color(0, 0, 0, 50));
            g2d.fillRect(connectButton.x + 2, connectButton.y + 2, connectButton.width - 4, connectButton.height - 4);
        }
        
        // 绘制返回按钮
        if (clickButton == backButton) {
            g2d.setColor(new Color(70, 70, 150)); // 点击颜色
        } else if (hoverButton == backButton) {
            g2d.setColor(new Color(130, 130, 230)); // 悬停颜色
        } else {
            g2d.setColor(new Color(100, 100, 200)); // 正常颜色
        }
        g2d.fill(backButton);
        
        // 如果是点击状态，绘制一个轻微的阴影效果
        if (clickButton == backButton) {
            g2d.setColor(new Color(0, 0, 0, 50));
            g2d.fillRect(backButton.x + 2, backButton.y + 2, backButton.width - 4, backButton.height - 4);
        }
        
        // 绘制按钮文字
        g2d.setColor(Color.WHITE);
        int buttonFontSize = Math.min(width, height) / 35;
        g2d.setFont(new Font("Arial", Font.BOLD, buttonFontSize));
        
        // 在主机按钮中居中显示文本
        String hostText = "Devenir host";
        FontMetrics hostMetrics = g2d.getFontMetrics();
        int hostTextWidth = hostMetrics.stringWidth(hostText);
        int hostTextHeight = hostMetrics.getHeight();
        g2d.drawString(hostText, 
                      hostButton.x + (hostButton.width - hostTextWidth) / 2,
                      hostButton.y + (hostButton.height + hostTextHeight / 2) / 2);
        
        // 在连接按钮中居中显示文本
        String connectText = "Connecter a un host";
        FontMetrics connectMetrics = g2d.getFontMetrics();
        int connectTextWidth = connectMetrics.stringWidth(connectText);
        int connectTextHeight = connectMetrics.getHeight();
        g2d.drawString(connectText, 
                      connectButton.x + (connectButton.width - connectTextWidth) / 2,
                      connectButton.y + (connectButton.height + connectTextHeight / 2) / 2);
        
        // 在返回按钮中居中显示文本
        String backText = "Retour";
        FontMetrics backMetrics = g2d.getFontMetrics();
        int backTextWidth = backMetrics.stringWidth(backText);
        int backTextHeight = backMetrics.getHeight();
        g2d.drawString(backText, 
                      backButton.x + (backButton.width - backTextWidth) / 2,
                      backButton.y + (backButton.height + backTextHeight / 2) / 2);
        
        g2d.dispose();
    }

    @Override
    public void dispose() {
        // 移除鼠标监听器
        sceneManager.getPanel().removeMouseListener(sceneManager.getPanel().getMouseListeners()[0]);
        sceneManager.getPanel().removeMouseMotionListener(sceneManager.getPanel().getMouseMotionListeners()[0]);
    }
} 