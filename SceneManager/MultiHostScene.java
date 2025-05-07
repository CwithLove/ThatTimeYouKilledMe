package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class MultiHostScene implements Scene {
    private SceneManager sceneManager;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private boolean playerTwoConnected = false;
    private Rectangle startButton;
    private Rectangle backButton;
    private String hostIP;
    private int animationDots = 0;
    private long lastDotTime = 0;
    
    // 添加鼠标悬停和点击效果的变量
    private Rectangle hoverButton = null;
    private Rectangle clickButton = null;
    private long clickTime = 0;
    
    public MultiHostScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        startButton = new Rectangle(600, 500, 150, 50);
        backButton = new Rectangle(50, 500, 150, 40);

        
        // Obtenir l'adresse IP du host
        try {
            hostIP = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            hostIP = "Impossible d'obtenir l'adresse IP";
        }
        
        // Mouse Listener
        sceneManager.getPanel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) {
                    if (playerTwoConnected && startButton.contains(e.getPoint())) {
                        clickButton = startButton;
                        clickTime = System.currentTimeMillis();
                        GameScene gameScene = new GameScene(sceneManager, true);
                        gameScene.updateLastLogin(1); // 1 pour le mode multi
                        sceneManager.setScene(gameScene);
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
                    if (playerTwoConnected && startButton.contains(e.getPoint())) {
                        clickButton = startButton;
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
                    if (playerTwoConnected && startButton.contains(e.getPoint())) {
                        hoverButton = startButton;
                    } else if (backButton.contains(e.getPoint())) {
                        hoverButton = backButton;
                    }
                }
            }
        });
        
        // Ici normallement, nous devons ajouter le code de la connexion réseau
        // pour simplifier de tester, nous attendons 5s.
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                playerTwoConnected = true;
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
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        g2d.drawString("Salle d'attente", width/2 - 50, height/7);
        
        // Dessiner les informations du joueur 1
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("Joueur 1 (Hôte): Connecté", width/6, height/5);
        
        // Dessiner les informations du joueur 2 et l'animation d'attente
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        if (!playerTwoConnected) {
            String dots = "";
            for (int i = 0; i < animationDots; i++) {
                dots += ".";
            }
            g2d.drawString("Joueur 2: En attente de connexion" + dots, width/6, height/5 + 100);
        } else {
            g2d.drawString("Joueur 2: Connecté", width/6, height/5+100);
        }
        
        // Afficher l'adresse IP du host en bas
        g2d.setFont(new Font("Arial", Font.PLAIN, 18));
        g2d.drawString("IP de l'hôte: " + hostIP, width/10, height * 55 /60);
        
        // Dessiner le bouton de démarrage de la partie
        if (playerTwoConnected) {
            if (clickButton == startButton) {
                g2d.setColor(new Color(0, 120, 0));
            } else if (hoverButton == startButton) {
                g2d.setColor(new Color(0, 220, 0));
            } else {
                g2d.setColor(new Color(0, 180, 0));
            }
        } else {
            g2d.setColor(Color.GRAY);
        }

        startButton.setLocation(width*6 /8 , height * 5 / 6);
        backButton.setLocation(width/10, height * 5 / 6);

        g2d.fill(startButton);
        
        // 如果是点击状态，绘制一个轻微的阴影效果
        if (clickButton == startButton && playerTwoConnected) {
            g2d.setColor(new Color(0, 0, 0, 50));
            g2d.fillRect(startButton.x + 2, startButton.y + 2, startButton.width - 4, startButton.height - 4);
        }
        
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        g2d.drawString("Commencer", startButton.x + 25, startButton.y + 30);
        
        // Dessiner le bouton de retour
        if (clickButton == backButton) {
            g2d.setColor(new Color(70, 70, 150));
        } else if (hoverButton == backButton) {
            g2d.setColor(new Color(130, 130, 230));
        } else {
    
        g2d.setColor(new Color(100, 100, 200));
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
        
        g2d.dispose();
    }

    @Override
    public void dispose() {
        // Remove Mouse Listener
        sceneManager.getPanel().removeMouseListener(sceneManager.getPanel().getMouseListeners()[0]);
        // 移除鼠标移动监听器
        sceneManager.getPanel().removeMouseMotionListener(sceneManager.getPanel().getMouseMotionListeners()[0]);
    }
    
    // Method va appele quand le joueur 2 est connecte
    public void setPlayerTwoConnected(boolean connected) {
        this.playerTwoConnected = connected;
    }
} 