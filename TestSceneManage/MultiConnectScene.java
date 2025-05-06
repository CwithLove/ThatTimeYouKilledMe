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
                        if (ipAddress.length() < 15) {  // 防止IP地址太长
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
                        connectToHost(ipAddress);
                    } else if (backButton.contains(e.getPoint())) {
                        sceneManager.setScene(new HostOrConnectScene(sceneManager));
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
    public void render(Graphics g) {
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
            g2d.setColor(new Color(0, 150, 0));
        } else {
            g2d.setColor(Color.GRAY);
        }
        g2d.fill(connectButton);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Connecter", connectButton.x + 5, connectButton.y + 25);
        
        // 绘制返回按钮
        g2d.setColor(new Color(100, 100, 200));
        g2d.fill(backButton);
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
    }
} 