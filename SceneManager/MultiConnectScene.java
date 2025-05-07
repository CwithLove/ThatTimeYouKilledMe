package SceneManager;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class MultiConnectScene implements Scene {
    private SceneManager sceneManager;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private boolean isConnected = false;
    private Rectangle connectButton;
    private Rectangle backButton;
    private String ipAddress = "";
    private int cursorPosition = 0;
    private boolean cursorVisible = true;
    private long lastCursorBlinkTime = 0;
    
    // 添加鼠标悬停和点击效果的变量
    private Rectangle hoverButton = null;
    private Rectangle clickButton = null;
    private long clickTime = 0;
    
    public MultiConnectScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        connectButton = new Rectangle(350, 300, 100, 40);
        backButton = new Rectangle(50, 500, 150, 40);
        

        sceneManager.getPanel().addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (fadeComplete) {
                    char c = e.getKeyChar();
                    if (Character.isDigit(c) || c == '.') {
                        if (ipAddress.length() < 15) {  // Limite de la longueur de l'IP
                            ipAddress += c;
                            cursorPosition = ipAddress.length();
                        }
                    } else if (c == KeyEvent.VK_BACK_SPACE && ipAddress.length() > 0) {
                        ipAddress = ipAddress.substring(0, ipAddress.length() - 1);
                        cursorPosition = ipAddress.length();
                    }
                }
            }
        });
        
        // Mouse Listener
        sceneManager.getPanel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) {
                    if (connectButton.contains(e.getPoint()) && !ipAddress.isEmpty()) {
                        clickButton = connectButton;
                        clickTime = System.currentTimeMillis();
                        connectToHost(ipAddress);
                    } else if (backButton.contains(e.getPoint())) {
                        clickButton = backButton;
                        clickTime = System.currentTimeMillis();
                        sceneManager.setScene(new HostOrConnectScene(sceneManager));
                    }
                }
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                if (fadeComplete) {
                    if (connectButton.contains(e.getPoint()) && !ipAddress.isEmpty()) {
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
                    if (connectButton.contains(e.getPoint()) && !ipAddress.isEmpty() && !isConnected) {
                        hoverButton = connectButton;
                    } else if (backButton.contains(e.getPoint())) {
                        hoverButton = backButton;
                    }
                }
            }
        });
    }
    
    private void connectToHost(String ip) {
        // Simuler la connexion
        new Thread(() -> {
            try {
                isConnected = true;
                Thread.sleep(1000);  // Simuler le retard de connexion
                // Apres connexion, passer a la salle d'attente
                SwingUtilities.invokeLater(() -> {
                    sceneManager.setScene(new LobbyScene(sceneManager, false));
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void init() {
        startTime = System.currentTimeMillis();
        lastCursorBlinkTime = startTime;
        sceneManager.getPanel().requestFocusInWindow();  // 确保键盘监听器正常工作
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
        
        // 更新光标闪烁
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCursorBlinkTime > 500) {  // 每500毫秒闪烁一次
            cursorVisible = !cursorVisible;
            lastCursorBlinkTime = currentTime;
        }
    }

    @Override
    public void render(Graphics g, int width, int height) {
        // 绘制背景
        g.setColor(new Color(40, 40, 80));
        g.fillRect(0, 0, 800, 600);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        
        // 绘制标题
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        g2d.drawString("Connexion à l'hôte", 260, 100);
        
        // 绘制输入框
        g2d.setColor(Color.WHITE);
        g2d.drawRect(250, 200, 300, 40);
        g2d.setFont(new Font("Arial", Font.PLAIN, 20));
        g2d.drawString("Entrez l'adresse IP de l'hôte:", 250, 180);
        g2d.drawString(ipAddress + (cursorVisible ? "|" : ""), 260, 225);
        
        // 绘制连接按钮
        if (!ipAddress.isEmpty() && !isConnected) {
            // 根据按钮状态设置颜色
            if (clickButton == connectButton) {
                g2d.setColor(new Color(0, 100, 0)); // 点击颜色
            } else if (hoverButton == connectButton) {
                g2d.setColor(new Color(0, 180, 0)); // 悬停颜色
            } else {
                g2d.setColor(new Color(0, 150, 0)); // 正常颜色
            }
        } else {
            g2d.setColor(Color.GRAY);
        }
        g2d.fill(connectButton);
        
        // 如果是点击状态，绘制一个轻微的阴影效果
        if (clickButton == connectButton && !isConnected) {
            g2d.setColor(new Color(0, 0, 0, 50));
            g2d.fillRect(connectButton.x + 2, connectButton.y + 2, connectButton.width - 4, connectButton.height - 4);
        }
        
        g2d.setColor(Color.WHITE);
        g2d.drawString("Connecter", connectButton.x + 5, connectButton.y + 25);
        
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
        
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        g2d.drawString("Retour", backButton.x + 45, backButton.y + 25);
        
        // 如果正在连接，显示状态
        if (isConnected) {
            g2d.setColor(Color.GREEN);
            g2d.setFont(new Font("Arial", Font.BOLD, 20));
            g2d.drawString("Connexion en cours...", 310, 400);
        }
        
        g2d.dispose();
    }

    @Override
    public void dispose() {
        // 移除键盘和鼠标监听器
        KeyListener[] keyListeners = sceneManager.getPanel().getKeyListeners();
        if (keyListeners.length > 0) {
            sceneManager.getPanel().removeKeyListener(keyListeners[0]);
        }
        
        MouseListener[] mouseListeners = sceneManager.getPanel().getMouseListeners();
        if (mouseListeners.length > 0) {
            sceneManager.getPanel().removeMouseListener(mouseListeners[0]);
        }
        
        // 移除鼠标移动监听器
        MouseMotionListener[] motionListeners = sceneManager.getPanel().getMouseMotionListeners();
        if (motionListeners.length > 0) {
            sceneManager.getPanel().removeMouseMotionListener(motionListeners[0]);
        }
    }
} 