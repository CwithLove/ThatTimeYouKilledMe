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
                        sceneManager.setScene(new GameScene(sceneManager)); // 假设多人游戏场景是GameScene
                    } else if (backButton.contains(e.getPoint())) {
                        sceneManager.setScene(new HostOrConnectScene(sceneManager));
                    }
                }
            }
        });
        
        // Ici normallement, nous devons ajouter le code de la connexion réseau
        // pour simplifier de tester, nous attendons 5s.
        new Thread(() -> {
            try {
                Thread.sleep(5000);
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
        g2d.drawString("Salle d'attente", 300, 100);
        
        // Dessiner les informations du joueur 1
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("Joueur 1 (Hôte): Connecté", 100, 200);
        
        // Dessiner les informations du joueur 2 et l'animation d'attente
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        if (!playerTwoConnected) {
            String dots = "";
            for (int i = 0; i < animationDots; i++) {
                dots += ".";
            }
            g2d.drawString("Joueur 2: En attente de connexion" + dots, 100, 250);
        } else {
            g2d.drawString("Joueur 2: Connecté", 100, 250);
        }
        
        // Afficher l'adresse IP du host en bas
        g2d.setFont(new Font("Arial", Font.PLAIN, 18));
        g2d.drawString("IP de l'hôte: " + hostIP, 50, 550);
        
        // Dessiner le bouton de démarrage de la partie
        if (playerTwoConnected) {
            g2d.setColor(new Color(0, 180, 0));
        } else {
            g2d.setColor(Color.GRAY);
        }
        g2d.fill(startButton);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        g2d.drawString("Commencer", startButton.x + 25, startButton.y + 30);
        
        // Dessiner le bouton de retour
        g2d.setColor(new Color(100, 100, 200));
        g2d.fill(backButton);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        g2d.drawString("Retour", backButton.x + 45, backButton.y + 25);
        
        g2d.dispose();
    }

    @Override
    public void dispose() {
        // Remove Mouse Listener
        sceneManager.getPanel().removeMouseListener(sceneManager.getPanel().getMouseListeners()[0]);
    }
    
    // Method va appele quand le joueur 2 est connecte
    public void setPlayerTwoConnected(boolean connected) {
        this.playerTwoConnected = connected;
    }
} 