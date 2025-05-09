package SceneManager;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class MultiConnectScene implements Scene {

    private SceneManager sceneManager;
    private MouseAdapter mouseAdapter;
    private KeyAdapter keyAdapter;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private boolean isConnected = false;
    private Button connectButton;
    private Button backButton;
    private String ipAddress = "";
    private int cursorPosition = 0;
    private boolean cursorVisible = true;
    private long lastCursorBlinkTime = 0;

    public MultiConnectScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;

        // 创建按钮并设置点击事件
        connectButton = new Button(350, 300, 100, 40, "Connecter", () -> {
            if (!ipAddress.isEmpty() && !isConnected) {
                connectToHost(ipAddress);
            }
        });
        
        backButton = new Button(50, 500, 150, 40, "Retour", () -> {
            sceneManager.setScene(new HostOrConnectScene(sceneManager));
        });

        // 创建键盘事件监听器
        keyAdapter = new KeyAdapter() {
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
        };
        sceneManager.getPanel().addKeyListener(keyAdapter);

        // 创建统一的MouseAdapter来处理所有鼠标事件
        mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    if (connectButton.contains(mousePoint) && !ipAddress.isEmpty() && !isConnected) {
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
                    if (connectButton.contains(mousePoint) && !ipAddress.isEmpty() && !isConnected) {
                        connectButton.setClicked(true);
                    } else if (backButton.contains(mousePoint)) {
                        backButton.setClicked(true);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                connectButton.setClicked(false);
                backButton.setClicked(false);
            }
            
            @Override
            public void mouseMoved(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    connectButton.update(mousePoint);
                    backButton.update(mousePoint);
                }
            }
        };

        // 注册鼠标监听器
        sceneManager.getPanel().addMouseListener(mouseAdapter);
        sceneManager.getPanel().addMouseMotionListener(mouseAdapter);
    }

    private void connectToHost(String ip) {
        // Simuler la connexion
        new Thread(() -> {
            try {
                isConnected = true;
                Thread.sleep(1000);  // Simuler le retard de connexion
                // Apres connexion, passer a la salle d'attente
                // SwingUtilities.invokeLater(() -> {
                //     sceneManager.setScene(new LobbyScene(sceneManager, false));
                // });
                String finalIp = ip;
                SwingUtilities.invokeLater(() -> {
                    sceneManager.setScene(new LobbyScene(sceneManager, false, finalIp));
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
        sceneManager.getPanel().requestFocusInWindow();
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

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCursorBlinkTime > 500) {
            cursorVisible = !cursorVisible;
            lastCursorBlinkTime = currentTime;
        }
    }

    @Override
    public void render(Graphics g, int width, int height) {
        g.setColor(new Color(40, 40, 80));
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g2d.setColor(Color.WHITE);
        int titleFontSize = Math.min(width, height) / 20;
        g2d.setFont(new Font("Arial", Font.BOLD, titleFontSize));
        String title = "Connexion à l'hôte";
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int titleWidth = titleMetrics.stringWidth(title);
        g2d.drawString(title, (width - titleWidth) / 2, height / 6);

        int textFontSize = Math.min(width, height) / 30;
        g2d.setFont(new Font("Arial", Font.PLAIN, textFontSize));
        String inputPrompt = "Entrez l'adresse IP de l'hôte:";
        FontMetrics promptMetrics = g2d.getFontMetrics();
        int promptWidth = promptMetrics.stringWidth(inputPrompt);
        g2d.drawString(inputPrompt, (width - promptWidth) / 2, height / 5);

        int inputWidth = width / 3;
        int inputHeight = height / 20;
        int inputX = (width - inputWidth) / 2;
        int inputY = height / 3;
        g2d.setColor(Color.WHITE);
        g2d.drawRect(inputX, inputY, inputWidth, inputHeight);

        g2d.setFont(new Font("Arial", Font.PLAIN, textFontSize));
        g2d.drawString(ipAddress + (cursorVisible ? "|" : ""), inputX + 10, inputY + inputHeight - 10);

        int buttonWidth = width / 6;
        int buttonHeight = height / 16;
        int buttonFontSize = Math.min(width, height) / 40;
        Font buttonFont = new Font("Arial", Font.BOLD, buttonFontSize);

        connectButton.setSize(buttonWidth, buttonHeight);
        connectButton.setLocation((width - buttonWidth) / 2, height / 2);
        connectButton.setFont(buttonFont);

        backButton.setSize(buttonWidth, buttonHeight);
        backButton.setLocation(width / 6 - buttonWidth / 2, height * 5 / 6);
        backButton.setFont(buttonFont);

        // 渲染按钮
        // 如果IP为空或已连接，连接按钮显示为灰色
        if (ipAddress.isEmpty() || isConnected) {
            g2d.setColor(Color.GRAY);
            g2d.fill(new Rectangle(connectButton.getX(), connectButton.getY(), connectButton.getWidth(), connectButton.getHeight()));
            
            g2d.setColor(Color.WHITE);
            g2d.setFont(buttonFont);
            FontMetrics metrics = g2d.getFontMetrics();
            int textWidth = metrics.stringWidth("Connecter");
            int textHeight = metrics.getHeight();
            g2d.drawString("Connecter", connectButton.getX() + (connectButton.getWidth() - textWidth) / 2,
                connectButton.getY() + (connectButton.getHeight() + textHeight / 2) / 2);
        } else {
            connectButton.render(g2d);
        }
        
        backButton.render(g2d);

        // 如果正在连接，显示连接信息
        if (isConnected) {
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.PLAIN, textFontSize));
            String connectingMsg = "Connexion en cours...";
            FontMetrics msgMetrics = g2d.getFontMetrics();
            int msgWidth = msgMetrics.stringWidth(connectingMsg);
            g2d.drawString(connectingMsg, (width - msgWidth) / 2, height * 2 / 3);
        }

        g2d.dispose();
    }

    @Override
    public void dispose() {
        // 安全移除监听器
        if (keyAdapter != null) {
            sceneManager.getPanel().removeKeyListener(keyAdapter);
        }
        if (mouseAdapter != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapter);
            sceneManager.getPanel().removeMouseMotionListener(mouseAdapter);
        }
    }
}
