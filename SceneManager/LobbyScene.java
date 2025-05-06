package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class LobbyScene implements Scene {

    private SceneManager sceneManager;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private boolean isHost;
    private boolean playerTwoConnected = false;
    private Rectangle startButton;
    private Rectangle backButton;
    private String hostIP;
    private int animationDots = 0;
    private long lastDotTime = 0;

    public LobbyScene(SceneManager sceneManager, boolean isHost) {
        this.sceneManager = sceneManager;
        this.isHost = isHost;
        startButton = new Rectangle(600, 500, 150, 50);
        backButton = new Rectangle(50, 500, 150, 40);

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

        // Mouse Listener
        sceneManager.getPanel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) {
                    if (isHost && playerTwoConnected && startButton.contains(e.getPoint())) {
                        Scene gameScene = new GameScene(sceneManager);
                        gameScene.updateLastLogin(1); // 1 pour le mode multi
                        sceneManager.setScene(gameScene);
                        // Normallement envoyer un message de demarrage a J2
                    } else if (backButton.contains(e.getPoint())) {
                        if (isHost) {
                            sceneManager.setScene(new MultiHostScene(sceneManager));
                        } else {
                            sceneManager.setScene(new MultiConnectScene(sceneManager));
                        }
                    }
                }
            }
        });

        if (isHost) {
            // TODO: Ajouter le code de la connexion reseau
            // Simuler
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
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
        g.fillRect(0, 0, 800, 600);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Dessiner le titre
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        g2d.drawString("Salle d'attente", 300, 100);

        // Dessiner les informations du joueur 1
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("Joueur 1 " + (isHost ? "(Hôte)" : "") + ": Connecté", 100, 200);

        // Dessiner les informations du joueur 2 et de l'animation de waiting
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        if (isHost) {
            if (!playerTwoConnected) {
                String dots = "";
                for (int i = 0; i < animationDots; i++) {
                    dots += ".";
                }
                g2d.drawString("Joueur 2: En attente de connexion" + dots, 100, 250);
            } else {
                g2d.drawString("Joueur 2: Connecté", 100, 250);
            }
        } else {
            g2d.drawString("Joueur 2 (Vous): Connecté", 100, 250);
        }

        // Si vous etes le host, afficher l'IP et le bouton de demarrage
        if (isHost) {
            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            g2d.drawString("IP de l'hôte: " + hostIP, 50, 550);

            // Dessiner le bouton de demarrage
            if (playerTwoConnected) {
                g2d.setColor(new Color(0, 180, 0));
            } else {
                g2d.setColor(Color.GRAY);
            }
            g2d.fill(startButton);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 18));
            g2d.drawString("Commencer", startButton.x + 25, startButton.y + 30);
        } else {
            // Si vous etes le client, afficher le message de waiting
            g2d.setFont(new Font("Arial", Font.BOLD, 20));
            g2d.setColor(Color.YELLOW);
            g2d.drawString("Prêt, en attente du démarrage de la partie...", 200, 400);
        }

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
        // Supprimer le listener de la souris
        sceneManager.getPanel().removeMouseListener(sceneManager.getPanel().getMouseListeners()[0]);

        // TODO: Nettoyer les ressources de la connexion reseau
    }

    public void setPlayerTwoConnected(boolean connected) {
        this.playerTwoConnected = connected;
    }

    public void startGame() {
        if (!isHost) {
            sceneManager.setScene(new GameScene(sceneManager));
        }
    }
}
