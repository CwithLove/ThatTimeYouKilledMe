package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;

import Network.GameClient;
import Network.GameServerManager;
import Network.GameStateUpdateListener;
import Modele.Jeu;

public class HostingScene implements Scene, GameStateUpdateListener {
    private SceneManager sceneManager;
    private GameServerManager gameServerManager; // Chỉ được quản lý bởi HostingScene
    private Button startGameButton;
    private Button backButton;

    private GameClient hostClient; // Client của Host, kết nối vào server của chính Host
    private volatile boolean serverSuccessfullyStarted = false;
    private volatile boolean playerTwoConfirmedConnected = false;

    private MouseAdapter mouseAdapterInternal;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private String hostIP = "Obtention de l'IP...";
    private int animationDots = 0;
    private long lastDotTime = 0;
    private String statusMessage = "Initialisation...";

    public HostingScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;

        startGameButton = new Button(300, 400, 200, 50, "Lancer la partie", () -> {
            if (serverSuccessfullyStarted && playerTwoConfirmedConnected) {
                System.out.println("HostingScene: Lancement du GameEngine.");
                if (gameServerManager != null && gameServerManager.areAllPlayersConnected()) {
                    gameServerManager.startGameEngine();

                    try {
                        Thread.sleep(2000); // Attendre 1 seconde pour s'assurer que le moteur de jeu est prêt
                    } catch (InterruptedException e) {
                        System.err.println("HostingScene: Erreur attente avant passage à GameScene: " + e.getMessage());
                    }

                    hostClient = new GameClient("localhost", this);
                    try {
                        hostClient.connect();
                        System.out.println("HostingScene: Client hôte connecté. Passage à GameScene.");
                        SwingUtilities.invokeLater(() -> {
                            GameScene gameScene = new GameScene(sceneManager, hostClient);
                            sceneManager.setScene(gameScene);
                        });
                    } catch (IOException e) {
                        handleHostClientConnectionError(e);
                    }
                } else {
                    statusMessage = "Attente: Tous les joueurs ne sont pas prêts.";
                    repaintPanel();
                }
            }
        });

        backButton = new Button(50, 500, 150, 40, "Retour", this::cleanUpAndGoToMenu);

        try {
            hostIP = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            hostIP = "IP Inconnue";
            System.err.println("HostingScene: Erreur obtention IP: " + e.getMessage());
        }
    }

    private void handleHostClientConnectionError(IOException e) {
        System.err.println("HostingScene: Erreur connexion client hôte: " + e.getMessage());
        e.printStackTrace();
        statusMessage = "Erreur client hôte: " + e.getMessage();
        JOptionPane.showMessageDialog(sceneManager.getPanel(),
                "Erreur de connexion pour le client hôte : " + e.getMessage(),
                "Erreur Hôte", JOptionPane.ERROR_MESSAGE);
        repaintPanel();
    }

    private void cleanUpAndGoToMenu() {
        if (gameServerManager != null) {
            gameServerManager.stopServer(); // Dừng server khi rời khỏi HostingScene
            gameServerManager = null;
        }
        if (hostClient != null) {
            hostClient.disconnect();
            hostClient = null;
        }
        sceneManager.setScene(new MultiplayerScene(sceneManager));
    }

    @Override
    public void init() {
        startTime = System.currentTimeMillis();
        alpha = 0f;
        fadeComplete = false;
        playerTwoConfirmedConnected = false;
        serverSuccessfullyStarted = false;
        statusMessage = "Démarrage du serveur hôte...";

        setupMouseListeners();

        this.gameServerManager = new GameServerManager(this);
        try {
            gameServerManager.startServer();
            serverSuccessfullyStarted = true;
            statusMessage = "Serveur démarré sur IP: " + hostIP + ". En attente du Joueur 2...";
            System.out.println("HostingScene: Serveur démarré.");
        } catch (IOException e) {
            serverSuccessfullyStarted = false;
            statusMessage = "Erreur démarrage serveur: " + e.getMessage();
            System.err.println("HostingScene: Erreur démarrage serveur: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(sceneManager.getPanel(),
                                "Impossible de démarrer le serveur : " + e.getMessage(),
                                "Erreur Serveur", JOptionPane.ERROR_MESSAGE);
        }
        lastDotTime = startTime;
        repaintPanel();
    }

    public void onPlayerTwoConnected() { // Callback từ GameServerManager
        SwingUtilities.invokeLater(() -> {
            if (!playerTwoConfirmedConnected) {
                playerTwoConfirmedConnected = true;
                statusMessage = "Joueur 2 connecté! L'hôte peut lancer la partie.";
                System.out.println("HostingScene: Joueur 2 confirmé connecté.");
                repaintPanel();
            }
        });
    }

    @Override
    public void update() {
        if (!fadeComplete) {
            long elapsed = System.currentTimeMillis() - startTime;
            alpha = Math.min(1f, elapsed / 1000f);
            if (alpha >= 1f) fadeComplete = true;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDotTime > 500) {
            animationDots = (animationDots + 1) % 4;
            lastDotTime = currentTime;
        }

        if (fadeComplete && sceneManager.getPanel() != null) {
             Point mousePos = sceneManager.getPanel().getMousePosition();
             if (mousePos != null) {
                 if (serverSuccessfullyStarted && playerTwoConfirmedConnected) {
                     startGameButton.update(mousePos);
                 } else {
                     if(startGameButton.contains(mousePos)) startGameButton.update(new Point(-1,-1));
                 }
                 backButton.update(mousePos);
                 repaintPanel(); // Repaint khi có di chuyển chuột để hover button chính xác
             }
        }
    }

    @Override
    public void render(Graphics g, int width, int height) {
        g.setColor(new Color(40, 40, 80));
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g2d.setColor(Color.WHITE);
        int titleFontSize = Math.min(width, height) / 18;
        g2d.setFont(new Font("Arial", Font.BOLD, titleFontSize));
        String titleText = "Salle d'attente de l'Hôte";
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int textWidth = titleMetrics.stringWidth(titleText);
        g2d.drawString(titleText, (width - textWidth) / 2, height / 7);

        int infoFontSize = Math.min(width, height) / 28;
        g2d.setFont(new Font("Arial", Font.PLAIN, infoFontSize));

        g2d.drawString("Votre IP (pour Joueur 2): " + hostIP, width / 6, height / 4);

        String player2StatusText;
        if (playerTwoConfirmedConnected) {
            player2StatusText = "Joueur 2: Connecté !";
            g2d.setColor(Color.GREEN);
        } else if (serverSuccessfullyStarted) {
            String dots = "";
            for (int i = 0; i < animationDots; i++) dots += ".";
            player2StatusText = "Joueur 2: En attente de connexion" + dots;
            g2d.setColor(Color.YELLOW);
        } else {
            player2StatusText = "Joueur 2: (Serveur non prêt)";
            g2d.setColor(Color.ORANGE);
        }
        g2d.drawString(player2StatusText, width / 6, height / 4 + infoFontSize + 15);
        g2d.setColor(Color.WHITE);

        if (statusMessage != null && !statusMessage.isEmpty()) {
            g2d.setFont(new Font("Arial", Font.ITALIC, infoFontSize * 3/4));
            FontMetrics statusMetrics = g2d.getFontMetrics();
            int statusWidth = statusMetrics.stringWidth(statusMessage);
            g2d.drawString(statusMessage, (width - statusWidth) / 2, height / 2);
        }

        int btnWidth = width / 5;
        int btnHeight = height / 14;
        int btnFontSize = Math.min(width, height) / 35;
        Font commonBtnFont = new Font("Arial", Font.BOLD, btnFontSize);

        startGameButton.setSize(btnWidth, btnHeight);
        startGameButton.setLocation(width / 2 - btnWidth / 2, height * 3 / 5);
        startGameButton.setFont(commonBtnFont);

        backButton.setSize(btnWidth * 3/4, btnHeight * 3/4);
        backButton.setLocation(50, height - (btnHeight*3/4) - 30);
        backButton.setFont(new Font("Arial", Font.PLAIN, btnFontSize * 3/4));

        if (serverSuccessfullyStarted && playerTwoConfirmedConnected) {
            startGameButton.render(g2d);
        } else {
            Color originalNormal = startGameButton.normalColor;
            startGameButton.setNormalColor(Color.DARK_GRAY);
            startGameButton.render(g2d);
            startGameButton.setNormalColor(originalNormal);
        }
        backButton.render(g2d);
        g2d.dispose();
    }

    private void setupMouseListeners() {
        clearMouseListeners();
        mouseAdapterInternal = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!fadeComplete) return;
                Point mousePoint = e.getPoint();
                if (startGameButton.contains(mousePoint) && serverSuccessfullyStarted && playerTwoConfirmedConnected) {
                    startGameButton.onClick();
                } else if (backButton.contains(mousePoint)) {
                    backButton.onClick();
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (!fadeComplete) return;
                Point mousePoint = e.getPoint();
                if (startGameButton.contains(mousePoint) && serverSuccessfullyStarted && playerTwoConfirmedConnected) {
                    startGameButton.setClicked(true);
                } else if (backButton.contains(mousePoint)) {
                    backButton.setClicked(true);
                }
                repaintPanel();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                startGameButton.setClicked(false);
                backButton.setClicked(false);
                repaintPanel();
            }
        };
        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
            // Add MouseMotionListener if needed for Button.update in mouseMoved
             sceneManager.getPanel().addMouseMotionListener(mouseAdapterInternal);
        }
    }

    private void clearMouseListeners() {
        if (sceneManager.getPanel() != null && mouseAdapterInternal != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().removeMouseMotionListener(mouseAdapterInternal);
        }
    }

    private void repaintPanel() {
        if (sceneManager != null && sceneManager.getPanel() != null) {
            sceneManager.getPanel().repaint();
        }
    }

    @Override
    public void dispose() {
        clearMouseListeners();
        // gameServerManager và hostClient được dừng trong cleanUpAndGoToMenu
        // hoặc khi HostingScene bị thay thế bởi GameScene.
        // Đảm bảo gameServerManager được stop nếu scene này bị dispose bất ngờ.
        if (gameServerManager != null) {
            gameServerManager.stopServer();
        }
         if (hostClient != null) {
            hostClient.disconnect();
        }
        System.out.println("HostingScene disposée.");
    }

    // GameStateUpdateListener (cho client của host)
    @Override
    public void onGameStateUpdate(Jeu newGameState) {
        // HostingScene không trực tiếp hiển thị game, GameScene sẽ làm điều đó.
        // Phương thức này được gọi khi client của host nhận được trạng thái game.
        System.out.println("HostingScene (listener): État du jeu reçu par le client hôte.");
    }

    @Override
    public void onGameMessage(String messageType, String messageContent) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("HostingScene (listener): Message Client Hôte - " + messageType + ": " + messageContent);
            if ("ERROR".equalsIgnoreCase(messageType) || "DISCONNECTED".equalsIgnoreCase(messageType)) {
                statusMessage = "Client Hôte: " + messageType + " - " + messageContent;
                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                    "Problème avec le client hôte interne: " + messageContent,
                    "Erreur Client Hôte", JOptionPane.ERROR_MESSAGE);
                repaintPanel();
                // Có thể cần xử lý thêm, ví dụ quay về MultiplayerScene
            }
        });
    }
}