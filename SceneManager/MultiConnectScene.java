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
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        
        // 绘制标题
        g2d.setColor(Color.WHITE);
        int titleFontSize = Math.min(width, height) / 20;
        g2d.setFont(new Font("Arial", Font.BOLD, titleFontSize));
        String title = "Connexion à l'hôte";
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int titleWidth = titleMetrics.stringWidth(title);
        g2d.drawString(title, (width - titleWidth) / 2, height/6);
        
        // 绘制输入框说明文本
        int textFontSize = Math.min(width, height) / 30;
        g2d.setFont(new Font("Arial", Font.PLAIN, textFontSize));
        String inputPrompt = "Entrez l'adresse IP de l'hôte:";
        FontMetrics promptMetrics = g2d.getFontMetrics();
        int promptWidth = promptMetrics.stringWidth(inputPrompt);
        g2d.drawString(inputPrompt, (width - promptWidth) / 2, height/5);
        
        // 绘制输入框
        int inputWidth = width / 3;
        int inputHeight = height / 20;
        int inputX = (width - inputWidth) / 2;
        int inputY = height/3;
        g2d.setColor(Color.WHITE);
        g2d.drawRect(inputX, inputY, inputWidth, inputHeight);
        
        // 绘制输入的IP地址
        g2d.setFont(new Font("Arial", Font.PLAIN, textFontSize));
        g2d.drawString(ipAddress + (cursorVisible ? "|" : ""), inputX + 10, inputY + inputHeight - 10);
        
        // 动态调整按钮大小
        int buttonWidth = width / 6;
        int buttonHeight = height / 16;
        
        // 设置连接按钮的大小和位置
        connectButton.setSize(buttonWidth, buttonHeight);
        connectButton.setLocation((width - buttonWidth) / 2, height/2);
        
        // 设置返回按钮的大小和位置
        backButton.setSize(buttonWidth, buttonHeight);
        backButton.setLocation(width/6 - buttonWidth/2, height * 5/6);

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
        
        // 在按钮中居中显示文本
        g2d.setColor(Color.WHITE);
        int buttonFontSize = Math.min(width, height) / 40;
        g2d.setFont(new Font("Arial", Font.BOLD, buttonFontSize));
        String connectText = "Connecter";
        FontMetrics connectMetrics = g2d.getFontMetrics();
        int connectTextWidth = connectMetrics.stringWidth(connectText);
        int connectTextHeight = connectMetrics.getHeight();
        g2d.drawString(connectText, 
                     connectButton.x + (connectButton.width - connectTextWidth) / 2,
                     connectButton.y + (connectButton.height + connectTextHeight / 2) / 2);
        
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
        
        // 在按钮中居中显示文本
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, buttonFontSize));
        String backText = "Retour";
        FontMetrics backMetrics = g2d.getFontMetrics();
        int backTextWidth = backMetrics.stringWidth(backText);
        int backTextHeight = backMetrics.getHeight();
        g2d.drawString(backText, 
                     backButton.x + (backButton.width - backTextWidth) / 2,
                     backButton.y + (backButton.height + backTextHeight / 2) / 2);
        
        // 如果正在连接，显示状态
        if (isConnected) {
            g2d.setColor(Color.GREEN);
            int statusFontSize = Math.min(width, height) / 35;
            g2d.setFont(new Font("Arial", Font.BOLD, statusFontSize));
            String statusText = "Connexion en cours...";
            FontMetrics statusMetrics = g2d.getFontMetrics();
            int statusWidth = statusMetrics.stringWidth(statusText);
            g2d.drawString(statusText, (width - statusWidth) / 2, height * 4 /6);
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