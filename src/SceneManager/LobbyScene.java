package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class LobbyScene implements Scene {

    private SceneManager sceneManager;
    private MouseAdapter mouseAdapter;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private boolean isHost;
    private boolean playerTwoConnected = false;
    private Button startButton;
    private Button backButton;
    private String hostIP;
    private int animationDots = 0;
    private long lastDotTime = 0;

    // 添加鼠标悬停和点击效果的变量
    private Rectangle hoverButton = null;
    private Rectangle clickButton = null;
    private long clickTime = 0;

    public LobbyScene(SceneManager sceneManager, boolean isHost) {
        this.sceneManager = sceneManager;
        this.isHost = isHost;
        
        // 创建按钮并设置点击事件
        startButton = new Button(600, 500, 150, 50, "Commencer", () -> {
            if (isHost && playerTwoConnected) {
                GameScene gameScene = new GameScene(sceneManager, true);
                gameScene.updateLastLogin(1); // 1 pour le mode multi
                sceneManager.setScene(gameScene);
                // Normallement envoyer un message de demarrage a J2
            }
        });
        
        backButton = new Button(50, 500, 150, 40, "Retour", () -> {
            if (isHost) {
                sceneManager.setScene(new MultiHostScene(sceneManager));
            } else {
                sceneManager.setScene(new MultiConnectScene(sceneManager));
            }
        });

        // Host gagne l'IP, J2 recoit l'IP
        if (isHost) {
            try {
                hostIP = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                hostIP = "Impossible d'obtenir l'adresse IP";
            }
        } else {
            // J2 recoit l'IP
            hostIP = "Connecté à l'hôte";
            // J2 est connecte
            playerTwoConnected = true;
        }

        // 创建统一的MouseAdapter来处理所有鼠标事件
        mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    if (isHost && playerTwoConnected && startButton.contains(mousePoint)) {
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
                    if (isHost && playerTwoConnected && startButton.contains(mousePoint)) {
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

        if (isHost) {
            // TODO: Ajouter le code de la connexion reseau
            // Simuler
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    playerTwoConnected = true;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
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

        // Mis a jour de l'animation des points
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDotTime > 500) {  // 500ms
            animationDots = (animationDots + 1) % 4;  // Boucle entre 0 et 3
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
        g2d.drawString(title, (width - titleWidth) / 2, height / 6);

        // Dessiner les informations du joueur 1
        int infoFontSize = Math.min(width, height) / 30;
        g2d.setFont(new Font("Arial", Font.BOLD, infoFontSize));
        g2d.drawString("Joueur 1 " + (isHost ? "(Hôte)" : "") + ": Connecté", width / 5, height / 6 + height / 10);

        // Dessiner les informations du joueur 2 et de l'animation de waiting
        g2d.setFont(new Font("Arial", Font.BOLD, infoFontSize));
        if (isHost) {
            if (!playerTwoConnected) {
                String dots = "";
                for (int i = 0; i < animationDots; i++) {
                    dots += ".";
                }
                g2d.drawString("Joueur 2: En attente de connexion" + dots, width / 5, height / 6 + height / 5);
            } else {
                g2d.drawString("Joueur 2: Connecté", width / 5, height / 6 + height / 5);
            }
        } else {
            g2d.drawString("Joueur 2 (Vous): Connecté", width / 5, height / 6 + height / 5);
        }

        // 动态调整按钮大小
        int buttonWidth = width / 6;
        int buttonHeight = height / 16;
        int buttonFontSize = Math.min(width, height) / 40;
        Font buttonFont = new Font("Arial", Font.BOLD, buttonFontSize);

        // 设置按钮位置和大小
        startButton.setSize(buttonWidth, buttonHeight);
        startButton.setLocation(width * 3 / 4 - buttonWidth / 2, height * 5 / 6);
        startButton.setFont(buttonFont);

        backButton.setSize(buttonWidth, buttonHeight);
        backButton.setLocation(width / 6 - buttonWidth / 2, height * 5 / 6);
        backButton.setFont(buttonFont);

        // Si vous etes le host, afficher l'IP et le bouton de demarrage
        if (isHost) {
            int ipFontSize = Math.min(width, height) / 40;
            g2d.setFont(new Font("Arial", Font.PLAIN, ipFontSize));
            g2d.drawString("IP de l'hôte: " + hostIP, width / 10, height * 55 / 60);

            // 根据playerTwoConnected状态渲染开始按钮
            if (!playerTwoConnected) {
                g2d.setColor(Color.GRAY);
                g2d.fill(new Rectangle(startButton.getX(), startButton.getY(), 
                                      startButton.getWidth(), startButton.getHeight()));
                
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
        } else {
            // Si vous etes le client, afficher le message de waiting
            int waitFontSize = Math.min(width, height) / 35;
            g2d.setFont(new Font("Arial", Font.BOLD, waitFontSize));
            g2d.setColor(Color.YELLOW);
            String waitText = "Prêt, en attente du démarrage de la partie...";
            FontMetrics waitMetrics = g2d.getFontMetrics();
            int waitTextWidth = waitMetrics.stringWidth(waitText);
            g2d.drawString(waitText, (width - waitTextWidth) / 2, height * 2 / 3);
        }

        // 渲染返回按钮
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

        // TODO: Nettoyer les ressources de la connexion reseau
    }

    public void setPlayerTwoConnected(boolean connected) {
        this.playerTwoConnected = connected;
    }

    public void startGame() {
        if (!isHost) {
            GameScene gameScene = new GameScene(sceneManager, false);
            gameScene.updateLastLogin(1);
            sceneManager.setScene(gameScene);
        }
    }
}
