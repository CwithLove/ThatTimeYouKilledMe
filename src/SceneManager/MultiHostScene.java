package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class MultiHostScene implements Scene {
    private MouseAdapter mouseAdapter;
    private SceneManager sceneManager;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private boolean playerTwoConnected = false;
    private Button startButton;
    private Button backButton;
    private String hostIP;
    private int animationDots = 0;
    private long lastDotTime = 0;

    public MultiHostScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        
        // 创建按钮并设置点击事件
        startButton = new Button(600, 500, 150, 50, "Commencer", () -> {
            if(playerTwoConnected) {
                GameScene gameScene = new GameScene(sceneManager, true);
                // gameScene.updateLastLogin(1); // 1 pour le mode multi
                sceneManager.setScene(gameScene);
            }
        });
        
        backButton = new Button(50, 500, 150, 40, "Retour", () -> {
            sceneManager.setScene(new HostOrConnectScene(sceneManager));
        });

        // Obtenir l'adresse IP du host
        try {
            hostIP = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            hostIP = "Impossible d'obtenir l'adresse IP";
        }

        // 创建统一的MouseAdapter来处理所有鼠标事件
        mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    if (playerTwoConnected && startButton.contains(mousePoint)) {
                        startButton.onClick();
                    } else if (backButton.contains(mousePoint)) {
                        backButton.onClick();
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    if (playerTwoConnected && startButton.contains(mousePoint)) {
                        startButton.setClicked(true);
                    } else if (backButton.contains(mousePoint)) {
                        backButton.setClicked(true);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                startButton.setClicked(false);
                backButton.setClicked(false);
            }
            
            @Override
            public void mouseMoved(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    startButton.update(mousePoint);
                    backButton.update(mousePoint);
                }
            }
        };

        // 注册鼠标监听器
        sceneManager.getPanel().addMouseListener(mouseAdapter);
        sceneManager.getPanel().addMouseMotionListener(mouseAdapter);

        // Ici normallement, nous devons ajouter le code de la connexion réseau
        // pour simplifier de tester, nous attendons 5s.
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                playerTwoConnected = true;
                startButton.setNormalColor(Color.GREEN);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void init() {
        startTime = System.currentTimeMillis();
        lastDotTime = startTime;
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

        // Animation d'attente 
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDotTime > 500) {  // Mis a jour toutes les 0.5s
            animationDots = (animationDots + 1) % 4;  // Boucle 0-3
            lastDotTime = currentTime;
        }
    }

    @Override
    public void render(Graphics g, int width, int height) {
        // Dessiner le fond
        g.setColor(new Color(40, 40, 80));
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Dessiner le titre
        g2d.setColor(Color.WHITE);
        int titleFontSize = Math.min(width, height) / 20;
        g2d.setFont(new Font("Arial", Font.BOLD, titleFontSize));
        String title = "Salle d'attente";
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int titleWidth = titleMetrics.stringWidth(title);
        g2d.drawString(title, (width - titleWidth) / 2, height / 7);

        // Dessiner les informations du joueur 1
        int infoFontSize = Math.min(width, height) / 30;
        g2d.setFont(new Font("Arial", Font.BOLD, infoFontSize));
        g2d.drawString("Joueur 1 (Hôte): Connecté", width / 6, height / 5);

        // Dessiner les informations du joueur 2 et l'animation d'attente
        g2d.setFont(new Font("Arial", Font.BOLD, infoFontSize));
        if (!playerTwoConnected) {
            String dots = "";
            for (int i = 0; i < animationDots; i++) {
                dots += ".";
            }
            g2d.drawString("Joueur 2: En attente de connexion" + dots, width / 6, height / 5 + height / 10);
        } else {
            g2d.drawString("Joueur 2: Connecté", width / 6, height / 5 + height / 10);
        }

        // Afficher l'adresse IP du host en bas
        int ipFontSize = Math.min(width, height) / 40;
        g2d.setFont(new Font("Arial", Font.PLAIN, ipFontSize));
        g2d.drawString("IP de l'hôte: " + hostIP, width / 10, height * 55 / 60);

        // 动态调整按钮大小
        int buttonWidth = width / 6;
        int buttonHeight = height / 16;
        int buttonFontSize = Math.min(width, height) / 40;
        Font buttonFont = new Font("Arial", Font.BOLD, buttonFontSize);

        // 设置开始按钮的大小和位置
        startButton.setSize(buttonWidth, buttonHeight);
        startButton.setLocation(width * 3 / 4 - buttonWidth / 2, height * 5 / 6);
        startButton.setFont(buttonFont);

        // 设置返回按钮的大小和位置
        backButton.setSize(buttonWidth, buttonHeight);
        backButton.setLocation(width / 6 - buttonWidth / 2, height * 5 / 6);
        backButton.setFont(buttonFont);

        // 渲染按钮
        // 如果玩家2未连接，开始按钮显示为灰色
        if (!playerTwoConnected) {
            g2d.setColor(Color.GRAY);
            g2d.fill(new Rectangle(startButton.getX(), startButton.getY(), startButton.getWidth(), startButton.getHeight()));
            
            g2d.setColor(Color.WHITE);
            g2d.setFont(buttonFont);
            FontMetrics metrics = g2d.getFontMetrics();
            int textWidth = metrics.stringWidth("Commencer");
            int textHeight = metrics.getHeight();
            g2d.drawString("Commencer", startButton.getX() + (startButton.getWidth() - textWidth) / 2, 
                startButton.getY() + (startButton.getHeight() + textHeight / 2) / 2);
        } else {
            startButton.render(g2d);
        }
        
        backButton.render(g2d);

        g2d.dispose();
    }

    @Override
    public void dispose() {
        // 安全移除鼠标监听器
        if (mouseAdapter != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapter);
            sceneManager.getPanel().removeMouseMotionListener(mouseAdapter);
        }
    }

    public void setPlayerTwoConnected(boolean connected) {
        this.playerTwoConnected = connected;
    }
}
