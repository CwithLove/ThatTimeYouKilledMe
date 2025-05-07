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
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        g2d.drawString("Mode Multijoueur", width/2 - 40, height/4 - 100);
        
        // 绘制主机按钮
        hostButton = new Rectangle(width/2 - 50, height/4, 300, 50);
        connectButton = new Rectangle(width/2 - 50, height/4 + 100, 300, 50);
        backButton = new Rectangle(width/9, height * 4 / 5, 150, 40);

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
                        sceneManager.setScene(new MenuScene(sceneManager));
                    }
                }
            }
        });
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
        sceneManager.getPanel().removeMouseMotionListener(sceneManager.getPanel().getMouseMotionListeners()[0]);
    }
} 