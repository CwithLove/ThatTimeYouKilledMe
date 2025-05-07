package SceneManager;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

// A CHANGER DYNAMIQUE LA RESOLUTION

public class MenuScene implements Scene {
    private SceneManager sceneManager;
    private Rectangle singleButton;
    private Rectangle multiButton;
    private Rectangle quitButton;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private int WIDTHOFSCREEN;
    private int HEIGHTOFSCREEN;
    
    // 添加鼠标悬停和点击效果的变量
    private Rectangle hoverButton = null;
    private Rectangle clickButton = null;
    private long clickTime = 0;

    public MenuScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        // A CHANGER DYNAMIQUE
        singleButton = new Rectangle(300, 250, 200, 50);
        multiButton = new Rectangle(300, 350, 200, 50);
        quitButton = new Rectangle(300, 450, 200, 50);


        // Mouse Listener
        sceneManager.getPanel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) {
                    if (singleButton.contains(e.getPoint())) {
                        clickButton = singleButton;
                        clickTime = System.currentTimeMillis();
                        sceneManager.setScene(new GameScene(sceneManager));
                    } else if (multiButton.contains(e.getPoint())) {
                        clickButton = multiButton;
                        clickTime = System.currentTimeMillis();
                        sceneManager.setScene(new HostOrConnectScene(sceneManager));
                    } else if (quitButton.contains(e.getPoint())) {
                        clickButton = quitButton;
                        clickTime = System.currentTimeMillis();
                        System.exit(0); // Normallement retourner le main menu
                    }
                }
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                if (fadeComplete) {
                    if (singleButton.contains(e.getPoint())) {
                        clickButton = singleButton;
                        clickTime = System.currentTimeMillis();
                    } else if (multiButton.contains(e.getPoint())) {
                        clickButton = multiButton;
                        clickTime = System.currentTimeMillis();
                    } else if (quitButton.contains(e.getPoint())) {
                        clickButton = quitButton;
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
                    if (singleButton.contains(e.getPoint())) {
                        hoverButton = singleButton;
                    } else if (multiButton.contains(e.getPoint())) {
                        hoverButton = multiButton;
                    } else if (quitButton.contains(e.getPoint())) {
                        hoverButton = quitButton;
                    }
                }
            }
        });
        
        // Mouse Listener
        sceneManager.getPanel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) {
                    if (singleButton.contains(e.getPoint())) {
                        clickButton = singleButton;
                        clickTime = System.currentTimeMillis();
                        sceneManager.setScene(new GameScene(sceneManager));
                    } else if (multiButton.contains(e.getPoint())) {
                        clickButton = multiButton;
                        clickTime = System.currentTimeMillis();
                        sceneManager.setScene(new HostOrConnectScene(sceneManager));
                    } else if (quitButton.contains(e.getPoint())) {
                        clickButton = quitButton;
                        clickTime = System.currentTimeMillis();
                        System.exit(0); // Normallement retourner le main menu
                    }
                }
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                if (fadeComplete) {
                    if (singleButton.contains(e.getPoint())) {
                        clickButton = singleButton;
                        clickTime = System.currentTimeMillis();
                    } else if (multiButton.contains(e.getPoint())) {
                        clickButton = multiButton;
                        clickTime = System.currentTimeMillis();
                    } else if (quitButton.contains(e.getPoint())) {
                        clickButton = quitButton;
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
                    if (singleButton.contains(e.getPoint())) {
                        hoverButton = singleButton;
                    } else if (multiButton.contains(e.getPoint())) {
                        hoverButton = multiButton;
                    } else if (quitButton.contains(e.getPoint())) {
                        hoverButton = quitButton;
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
            alpha = Math.min(1f, elapsed / 1000f); 
            if (alpha >= 1f) {
                fadeComplete = true;
            }
        }
    }

    @Override
    public void render(Graphics g, int width, int height) {
        // Background
        g.setColor(new Color(50, 50, 100));
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        
        // 绘制标题
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        g2d.drawString("Menu", width/2, height/4);

        // A CHANGER DYNAMIQUE

        singleButton.setLocation(width/2, height/4 + 100);
        multiButton.setLocation(width/2, height/4 + 200);
        quitButton.setLocation(width/2, height/4 + 300);

        
        // 绘制单人游戏按钮
        if (clickButton == singleButton) {
            g2d.setColor(new Color(70, 70, 150)); // 点击颜色
        } else if (hoverButton == singleButton) {
            g2d.setColor(new Color(130, 130, 230)); // 悬停颜色
        } else {
            g2d.setColor(new Color(100, 100, 200)); // 正常颜色
        }
        g2d.fill(singleButton);
        
        // 如果是点击状态，绘制一个轻微的阴影效果
        if (clickButton == singleButton) {
            g2d.setColor(new Color(0, 0, 0, 50));
            g2d.fillRect(singleButton.x + 2, singleButton.y + 2, singleButton.width - 4, singleButton.height - 4);
        }
        
        // 绘制多人游戏按钮
        if (clickButton == multiButton) {
            g2d.setColor(new Color(70, 70, 150)); // 点击颜色
        } else if (hoverButton == multiButton) {
            g2d.setColor(new Color(130, 130, 230)); // 悬停颜色
        } else {
            g2d.setColor(new Color(100, 100, 200)); // 正常颜色
        }
        g2d.fill(multiButton);
        
        // 如果是点击状态，绘制一个轻微的阴影效果
        if (clickButton == multiButton) {
            g2d.setColor(new Color(0, 0, 0, 50));
            g2d.fillRect(multiButton.x + 2, multiButton.y + 2, multiButton.width - 4, multiButton.height - 4);
        }
        
        // 绘制退出按钮
        if (clickButton == quitButton) {
            g2d.setColor(new Color(70, 70, 150)); // 点击颜色
        } else if (hoverButton == quitButton) {
            g2d.setColor(new Color(130, 130, 230)); // 悬停颜色
        } else {
            g2d.setColor(new Color(100, 100, 200)); // 正常颜色
        }
        g2d.fill(quitButton);


        
        // 如果是点击状态，绘制一个轻微的阴影效果
        if (clickButton == quitButton) {
            g2d.setColor(new Color(0, 0, 0, 50));
            g2d.fillRect(quitButton.x + 2, quitButton.y + 2, quitButton.width - 4, quitButton.height - 4);
        }
        
        // 绘制按钮文字
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        g2d.drawString("Single Player", singleButton.x + 50, singleButton.y + 35);
        g2d.drawString("Multi Player", multiButton.x + 50, multiButton.y + 35);
        
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        g2d.drawString("Quit", quitButton.x + 45, quitButton.y + 25);
        
        g2d.dispose();
    }

    @Override
    public void dispose() {
        sceneManager.getPanel().removeMouseListener(sceneManager.getPanel().getMouseListeners()[0]);
        sceneManager.getPanel().removeMouseMotionListener(sceneManager.getPanel().getMouseMotionListeners()[0]);
    }
}
