package SceneManager;

import Modele.Jeu;
import Network.GameClient;
import Network.GameStateUpdateListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * ClientLobbyScene représente la salle d'attente du client (Joueur 2)
 * après s'être connecté au serveur, en attendant que l'hôte (Joueur 1) démarre la partie.
 */
public class ClientLobbyScene implements Scene, GameStateUpdateListener {
    private SceneManager sceneManager;
    private GameClient gameClient;
    private Button backButton;
    private boolean clientWasConnected;
    
    private MouseAdapter mouseAdapterInternal;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    
    private int animationDots = 0;
    private long lastDotTime = 0;
    private String statusMessage = "En attente du démarrage de la partie par l'hôte...";
    private String j1Status = "En attente...";
    private String j2Status = "Connecté";
    
    // Flag
    private boolean transitioningToGameScene = false;
    
    /**
     * Constructeur de ClientLobbyScene
     * 
     * @param sceneManager Le gestionnaire de scènes
     * @param gameClient Le client de jeu déjà connecté au serveur
     */
    public ClientLobbyScene(SceneManager sceneManager, GameClient gameClient) {
        this.sceneManager = sceneManager;
        this.gameClient = gameClient;
        this.clientWasConnected = (gameClient != null && gameClient.isConnected());
        
        // Mise à jour du listener de GameClient pour qu'il pointe vers cette scène
        if (this.gameClient != null) {
            try {
                this.gameClient.setListener(this);
            } catch (Exception e) {
                System.err.println("Erreur lors de la définition du listener: " + e.getMessage());
            }
        }
        
        // Bouton Retour
        backButton = new Button(50, 500, 150, 40, "Retour", () -> {
            // Retour à l'écran de connexion
            if (gameClient != null) {
                gameClient.disconnect();
            }
            sceneManager.setScene(new MultiplayerScene(sceneManager));
        });
    }
    
    @Override
    public void init() {
        startTime = System.currentTimeMillis();
        alpha = 0f;
        fadeComplete = false;
        lastDotTime = startTime;
        animationDots = 0;
        
        if (gameClient != null) {
            this.clientWasConnected = gameClient.isConnected() || clientWasConnected;
        }
        
        // Tenter une reconnexion immediate si le client était connecté mais ne l'est plus
        if (gameClient != null && !gameClient.isConnected() && clientWasConnected) {
            try {
                System.out.println("ClientLobbyScene: Tentative de reconnexion immédiate...");
                if (gameClient.reconnect()) {
                    System.out.println("ClientLobbyScene: Reconnexion réussie!");
                    clientWasConnected = true; // Mise à jour de l'état
                } else {
                    System.out.println("ClientLobbyScene: Échec de la reconnexion immédiate.");
                }
            } catch (Exception e) {
                System.err.println("ClientLobbyScene: Erreur lors de la tentative de reconnexion: " + e.getMessage());
            }
        }
        
        // Revient au lobby depuis GameScene, on set le listener vers cette scène
        if (gameClient != null) {
            try {
                gameClient.setListener(this);
                System.out.println("ClientLobbyScene: Listener mis à jour.");
            } catch (Exception e) {
                System.err.println("ClientLobbyScene: Erreur lors de la mise à jour du listener: " + e.getMessage());
            }
        }
        
        // Mise à jour de l'interface selon l'état de connexion
        if (gameClient != null && (gameClient.isConnected() || clientWasConnected)) {
            j1Status = "En ligne";
            j2Status = "Vous êtes connecté (ID: " + (gameClient.isConnected() ? gameClient.getMyPlayerId() : "...") + ")";
            statusMessage = "En attente du démarrage de la partie par l'hôte...";
            
            if (!gameClient.isConnected() && clientWasConnected) {
                statusMessage = "Tentative de reconnexion au serveur...";
            }
        } else {
            // Si pour une raison quelconque le client n'est pas connecté, afficher un message d'erreur
            j1Status = "Non connecté";
            j2Status = "Erreur de connexion";
            statusMessage = "Problème de connexion au serveur. Veuillez réessayer.";
            
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                        "La connexion au serveur a été perdue. Veuillez réessayer.",
                        "Erreur de Connexion", JOptionPane.ERROR_MESSAGE);
                sceneManager.setScene(new ConnectHostScene(sceneManager));
            });
        }
        
        setupMouseListeners();
        repaintPanel();
    }
    
    @Override
    public void update() {
        if (!fadeComplete) {
            long elapsed = System.currentTimeMillis() - startTime;
            alpha = Math.min(1.0f, elapsed / 1000.0f);
            if (alpha >= 1.0f) {
                fadeComplete = true;
            }
        }
        
        // Vérifier l'état de connexion et tenter une reconnexion si nécessaire
        if (gameClient != null && !gameClient.isConnected() && clientWasConnected) {
            // Essayer de se reconnecter toutes les 5 secondes (pour éviter des tentatives trop fréquentes)
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastDotTime > 5000) {
                try {
                    System.out.println("ClientLobbyScene: Nouvelle tentative de reconnexion...");
                    
                    // Utiliser la méthode reconnect du GameClient
                    boolean reconnected = gameClient.reconnect();
                    
                    // Mise à jour de l'interface selon le résultat
                    if (reconnected) {
                        System.out.println("ClientLobbyScene: Reconnexion réussie avec ID: " + gameClient.getMyPlayerId());
                        statusMessage = "En attente du démarrage de la partie par l'hôte...";
                        j1Status = "En ligne";
                        j2Status = "Vous êtes connecté (ID: " + gameClient.getMyPlayerId() + ")";
                    } else {
                        statusMessage = "Tentative de reconnexion au serveur...";
                    }
                    repaintPanel();
                } catch (Exception e) {
                    System.err.println("ClientLobbyScene: Erreur lors de la tentative de reconnexion: " + e.getMessage());
                }
            }
        }
        
        // Animation des points de suspension
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDotTime > 500) { // Met à jour l'animation des points toutes les 0.5s
            animationDots = (animationDots + 1) % 4; // 0 à 3 points
            lastDotTime = currentTime;
            repaintPanel();
        }
        
        // Mise à jour de l'état des boutons
        if (fadeComplete && sceneManager.getPanel() != null) {
            Point mousePos = sceneManager.getPanel().getMousePosition();
            if (mousePos != null) {
                backButton.update(mousePos);
            }
        }
    }
    
    @Override
    public void render(Graphics g, int width, int height) {
        // Dessin de l'arrière-plan
        g.setColor(new Color(40, 40, 80)); // Bleu-gris foncé
        g.fillRect(0, 0, width, height);
        
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        
        // Titre
        g2d.setColor(Color.WHITE);
        int titleFontSize = Math.min(width, height) / 18;
        g2d.setFont(new Font("Arial", Font.BOLD, titleFontSize));
        String titleText = "Lobby de Jeu";
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int titleWidth = titleMetrics.stringWidth(titleText);
        g2d.drawString(titleText, (width - titleWidth) / 2, height / 7);
        
        // Taille de police pour les informations
        int infoFontSize = Math.min(width, height) / 28;
        
        // Division de l'écran en deux zones pour J1 et J2
        int zoneWidth = width / 2 - 20; // Un peu moins que la moitié pour avoir de l'espace entre
        int zoneHeight = height / 3;
        int zoneY = height / 3;
        
        // Zone J1 (gauche)
        drawPlayerZone(g2d, 10, zoneY, zoneWidth, zoneHeight, true, j1Status, infoFontSize);
        
        // Zone J2 (droite)
        drawPlayerZone(g2d, width/2 + 10, zoneY, zoneWidth, zoneHeight, false, j2Status, infoFontSize);
        
        // Message d'attente
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.ITALIC, infoFontSize));
        
        // Ajout de points de suspension animés
        String dots = "";
        for (int i = 0; i < animationDots; i++) {
            dots += ".";
        }
        String waitingText = statusMessage + dots;
        
        FontMetrics waitingMetrics = g2d.getFontMetrics();
        int waitingWidth = waitingMetrics.stringWidth(waitingText);
        g2d.drawString(waitingText, (width - waitingWidth) / 2, height * 2 / 3 + 30);
        
        // Dessin du bouton Retour
        backButton.setSize(150, 40);
        backButton.setLocation(50, height - 60);
        backButton.setFont(new Font("Arial", Font.PLAIN, Math.max(12, infoFontSize * 3 / 4)));
        
        backButton.render(g2d);
        
        g2d.dispose();
    }
    
    /**
     * Dessine une zone de joueur avec un cadre et les informations
     */
    private void drawPlayerZone(Graphics2D g2d, int x, int y, int width, int height, 
                             boolean isJ1, String statusText, int fontSize) {
        // Cadre de la zone joueur
        g2d.setColor(new Color(60, 60, 100, 180));
        g2d.fillRoundRect(x, y, width, height, 20, 20);
        g2d.setColor(isJ1 ? new Color(100, 150, 255) : new Color(255, 150, 100));
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRoundRect(x, y, width, height, 20, 20);
        
        // Titre de la zone (J1 ou J2)
        String playerTitle = isJ1 ? "J1" : "J2";
        g2d.setFont(new Font("Arial", Font.BOLD, fontSize * 3 / 2));
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int titleWidth = titleMetrics.stringWidth(playerTitle);
        g2d.drawString(playerTitle, x + (width - titleWidth) / 2, y + fontSize * 2);
        
        // Texte de statut
        g2d.setColor(statusText.contains("connecté") || statusText.contains("ligne") ? 
                    Color.GREEN : Color.YELLOW);
        g2d.setFont(new Font("Arial", Font.PLAIN, fontSize));
        FontMetrics statusMetrics = g2d.getFontMetrics();
        int statusWidth = statusMetrics.stringWidth(statusText);
        g2d.drawString(statusText, x + (width - statusWidth) / 2, y + fontSize * 4);
        
        // Texte d'info supplémentaire
        g2d.setColor(Color.WHITE);
        String infoText = isJ1 ? "L'hôte lance la partie" : "En attente de l'hôte";
        FontMetrics infoMetrics = g2d.getFontMetrics();
        int infoWidth = infoMetrics.stringWidth(infoText);
        g2d.drawString(infoText, x + (width - infoWidth) / 2, y + fontSize * 6);
    }
    
    private void setupMouseListeners() {
        clearMouseListeners();
        mouseAdapterInternal = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!fadeComplete) return;
                Point mousePoint = e.getPoint();
                
                if (backButton.contains(mousePoint)) {
                    backButton.onClick();
                }
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                if (!fadeComplete) return;
                Point mousePoint = e.getPoint();
                
                if (backButton.contains(mousePoint)) {
                    backButton.setClicked(true);
                    repaintPanel();
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                backButton.setClicked(false);
                repaintPanel();
            }
        };
        
        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
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
    
    // 设置转换标志的方法
    private void setTransitioningToGameScene(boolean transitioning) {
        this.transitioningToGameScene = transitioning;
        System.out.println("ClientLobbyScene: transitioningToGameScene mis à " + transitioning);
    }
    
    @Override
    public void dispose() {
        clearMouseListeners();
        
        // Ne pas déconnecter gameClient si on passe à GameScene
        // Sinon, le déconnecter
        if (gameClient != null && (sceneManager.getCurrentScene() == null || 
                                 !sceneManager.getCurrentScene().getClass().getSimpleName().equals("GameScene")) && 
                                 !transitioningToGameScene) {
            System.out.println("ClientLobbyScene: Déconnexion du client...");
            try {
                gameClient.disconnect();
            } catch (Exception e) {
                System.err.println("Erreur lors de la déconnexion: " + e.getMessage());
            }
            gameClient = null;
        } else {
            System.out.println("ClientLobbyScene: Conservation de la connexion pour GameScene");
        }
    }
    
    // Implémentation de GameStateUpdateListener
    
    @Override
    public void onGameStateUpdate(Jeu newGameState) {
        if (newGameState != null) {
            System.out.println("ClientLobbyScene: État du jeu reçu, le jeu est en cours de démarrage!");
            
            // Si on reçoit une mise à jour d'état du jeu, cela signifie que le jeu a commencé
            SwingUtilities.invokeLater(() -> {
                // Vérifier si le client est connecté, sinon tenter une reconnexion
                if (gameClient != null) {
                    if (gameClient.isConnected()) {
                        System.out.println("ClientLobbyScene: Transition vers GameScene avec client connecté");
                        
                        // Marquer que nous sommes en train de passer à GameScene pour éviter
                        // la déconnexion dans dispose()
                        setTransitioningToGameScene(true);
                        
                        // Conserver la référence au client pour éviter la collecte pendant la transition
                        final GameClient clientToTransfer = this.gameClient;
                        
                        // Créer la GameScene avec le client existant
                        GameScene gameScene = new GameScene(sceneManager, clientToTransfer);
                        sceneManager.setScene(gameScene);
                    } else if (clientWasConnected) {
                        System.out.println("ClientLobbyScene: Tentative de reconnexion avant transition vers GameScene");
                        try {
                            if (gameClient.reconnect()) {
                                System.out.println("ClientLobbyScene: Reconnexion réussie, transition vers GameScene");
                                
                                // Marquer que nous sommes en train de passer à GameScene
                                setTransitioningToGameScene(true);
                                
                                // Conserver la référence au client
                                final GameClient clientToTransfer = this.gameClient;
                                
                                GameScene gameScene = new GameScene(sceneManager, clientToTransfer);
                                sceneManager.setScene(gameScene);
                            } else {
                                System.err.println("ClientLobbyScene: Échec de reconnexion avant transition vers GameScene");
                                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                                    "Impossible de rejoindre la partie: la connexion a été perdue et la reconnexion a échoué.",
                                    "Erreur de Connexion", JOptionPane.ERROR_MESSAGE);
                            }
                        } catch (Exception e) {
                            System.err.println("ClientLobbyScene: Erreur lors de la tentative de reconnexion avant GameScene: " + e.getMessage());
                            JOptionPane.showMessageDialog(sceneManager.getPanel(),
                                "Erreur lors de la tentative de reconnexion: " + e.getMessage(),
                                "Erreur de Connexion", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }
            });
        }
    }
    
    @Override
    public void onGameMessage(String messageType, String messageContent) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("ClientLobbyScene: Message reçu - Type: " + messageType + ", Contenu: " + messageContent);
            
            if ("ERROR".equals(messageType) || "DISCONNECTED".equals(messageType)) {
                statusMessage = "Erreur: " + messageContent;
                
                // Afficher un message d'erreur et retourner à l'écran de connexion
                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                                             "Déconnecté du serveur: " + messageContent,
                                             "Erreur de Connexion", JOptionPane.ERROR_MESSAGE);
                
                if (gameClient != null) {
                    gameClient.disconnect();
                    gameClient = null;
                }
                
                sceneManager.setScene(new ConnectHostScene(sceneManager));
            }
            
            repaintPanel();
        });
    }
} 