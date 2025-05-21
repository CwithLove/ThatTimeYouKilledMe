package SceneManager;

import Modele.Jeu;
import Network.GameClient;
import Network.GameServerManager;
import Network.GameStateUpdateListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * SinglePlayerLobbyScene est la scène du lobby pour le mode solo
 * Permet au joueur de sélectionner la difficulté de l'IA et de se connecter automatiquement avec l'IA comme deuxième joueur
 */
public class SinglePlayerLobbyScene implements Scene, GameStateUpdateListener {

    private SceneManager sceneManager;
    private GameServerManager gameServerManager;
    private Button startGameButton;
    private Button backButton;
    
    // Menu déroulant pour la sélection de la difficulté de l'IA
    private JComboBox<String> difficultyComboBox;
    private String selectedDifficulty = "Facile"; // Difficulté par défaut: facile
    private final String[] DIFFICULTY_OPTIONS = {"Facile", "Moyen", "Difficile"};

    private GameClient hostClient;
    private volatile boolean serverSuccessfullyStarted = false;
    private volatile boolean aiPlayerConnected = false;
    private volatile boolean hostClientConnected = false;

    private MouseAdapter mouseAdapterInternal;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private String hostIP = "127.0.0.1"; // IP locale pour le jeu solo
    private int animationDots = 0;
    private long lastDotTime = 0;
    private String statusMessage = "Initialisation...";

    private boolean transitioningToGameScene = false;

    /**
     * Constructeur
     * @param sceneManager Gestionnaire de scènes
     */
    public SinglePlayerLobbyScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        this.transitioningToGameScene = false;

        // Initialisation du menu déroulant
        difficultyComboBox = new JComboBox<>(DIFFICULTY_OPTIONS);
        difficultyComboBox.setSelectedItem("Facile"); // Sélection par défaut: facile
        difficultyComboBox.addActionListener(e -> {
            selectedDifficulty = (String) difficultyComboBox.getSelectedItem();
            System.out.println("Difficulté changée à: " + selectedDifficulty);
            repaintPanel();
        });

        // Bouton de démarrage du jeu
        startGameButton = new Button(300, 450, 200, 50, "Commencer", () -> {
            if (serverSuccessfullyStarted && aiPlayerConnected && hostClientConnected) {
                System.out.println("SinglePlayerLobbyScene: Tentative de démarrage du jeu...");
                if (gameServerManager != null && gameServerManager.areAllPlayersConnected()) {
                    startGameButton.setEnabled(false);
                    statusMessage = "Démarrage du jeu...";
                    repaintPanel();

                    SwingWorker<Void, String> gameStarterWorker = new SwingWorker<Void, String>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            try {
                                publish("Initialisation du moteur de jeu...");
                                // Définir la difficulté de l'IA
                                gameServerManager.setAIDifficulty(selectedDifficulty);
                                gameServerManager.startGameEngine();
                                Thread.sleep(800);

                                if (hostClient == null || !hostClient.isConnected()) {
                                    throw new Exception("Client hôte non connecté ou invalide avant la conversion.");
                                }
                                
                                if (hostClient.getGameInstance() == null) {
                                    publish("Attente de l'état initial du client hôte...");
                                    for (int i = 0; i < 10; i++) {
                                        Thread.sleep(200);
                                        if (hostClient.getGameInstance() != null && hostClient.getGameInstance().getJoueurCourant() != null) {
                                            System.out.println("SinglePlayerLobbyScene: État initial du client hôte reçu.");
                                            break;
                                        }
                                    }
                                    if (hostClient.getGameInstance() == null || hostClient.getGameInstance().getJoueurCourant() == null){
                                        System.out.println("SinglePlayerLobbyScene: État initial du client hôte non reçu, continuer vers GameScene.");
                                    }
                                }
                                return null;
                            } catch (Exception e) {
                                System.err.println("SinglePlayerLobbyScene: Erreur dans doInBackground de gameStarterWorker: " + e.getMessage());
                                e.printStackTrace();
                                throw e;
                            }
                        }

                        @Override
                        protected void process(java.util.List<String> chunks) {
                            if (!chunks.isEmpty()) {
                                statusMessage = chunks.get(chunks.size() - 1);
                                repaintPanel();
                            }
                        }

                        @Override
                        protected void done() {
                            try {
                                get();
                                if (hostClient != null && hostClient.isConnected()) {
                                    System.out.println("SinglePlayerLobbyScene: Prêt à passer à GameScene. ID du client hôte: " + hostClient.getMyPlayerId());
                                    setTransitioningToGameScene(true);

                                    SwingUtilities.invokeLater(() -> {
                                        try {
                                            GameScene gameScene = new GameScene(sceneManager, hostClient, gameServerManager, true);
                                            System.out.println("SinglePlayerLobbyScene: GameScene créée, gameServerManager transféré.");
                                            SinglePlayerLobbyScene.this.gameServerManager = null;
                                            sceneManager.setScene(gameScene);
                                            System.out.println("SinglePlayerLobbyScene: Passage à GameScene terminé.");
                                        } catch (Exception e_scene) {
                                            System.err.println("SinglePlayerLobbyScene: Erreur grave lors de la création/passage à GameScene: " + e_scene.getMessage());
                                            e_scene.printStackTrace();
                                            statusMessage = "Erreur grave: " + e_scene.getMessage();
                                            JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur grave", JOptionPane.ERROR_MESSAGE);
                                            startGameButton.setEnabled(true);
                                            setTransitioningToGameScene(false);
                                            repaintPanel();
                                        }
                                    });
                                } else {
                                    throw new Exception("Connexion au client hôte avant passage à GameScene interrompue ou non initialisée.");
                                }
                            } catch (Exception e) {
                                System.err.println("SinglePlayerLobbyScene: Erreur dans done() de gameStarterWorker: " + e.getMessage());
                                statusMessage = "Erreur de démarrage du jeu: " + e.getMessage();
                                JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur de démarrage", JOptionPane.ERROR_MESSAGE);
                                startGameButton.setEnabled(true);
                                repaintPanel();
                            }
                        }
                    };
                    gameStarterWorker.execute();
                } else {
                    statusMessage = "Attente: IA joueur non prêt.";
                    startGameButton.setEnabled(true);
                    repaintPanel();
                }
            } else {
                System.out.println("SinglePlayerLobbyScene: Conditions de démarrage non remplies (serveur: " + serverSuccessfullyStarted +
                               ", IA joueur: " + aiPlayerConnected + ", client hôte: " + hostClientConnected +")");
                startGameButton.setEnabled(true);
            }
        });

        // Bouton de retour
        backButton = new Button(50, 500, 150, 40, "Retour", this::cleanUpAndGoToMenu);

        try {
            hostIP = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            hostIP = "IP inconnue";
            System.err.println("SinglePlayerLobbyScene: Erreur lors de la récupération de l'IP: " + e.getMessage());
        }
    }
    
    /**
     * Constructeur avec réutilisation des connexions existantes
     * @param sceneManager Gestionnaire de scènes
     * @param existingClient Client de jeu existant
     * @param existingServer Serveur de jeu existant
     */
    public SinglePlayerLobbyScene(SceneManager sceneManager, GameClient existingClient, GameServerManager existingServer) {
        // Initialisation standard
        this(sceneManager);
        
        System.out.println("SinglePlayerLobbyScene: Constructeur avec réutilisation des connexions existantes");
        
        // Réutiliser les connexions existantes
        if (existingClient != null && existingClient.isConnected()) {
            this.hostClient = existingClient;
            this.hostClient.setListener(this);
            this.hostClientConnected = true;
            System.out.println("SinglePlayerLobbyScene: Client hôte existant réutilisé (ID: " + 
                              (this.hostClient != null ? this.hostClient.getMyPlayerId() : "inconnu") + ")");
        }
        
        if (existingServer != null && existingServer.isServerRunning()) {
            this.gameServerManager = existingServer;
            this.serverSuccessfullyStarted = true;
            System.out.println("SinglePlayerLobbyScene: Serveur existant réutilisé");
            
            // Vérifier si l'IA est déjà connectée
            if (this.gameServerManager.areAllPlayersConnected()) {
                this.aiPlayerConnected = true;
                System.out.println("SinglePlayerLobbyScene: IA déjà connectée");
            } else {
                System.out.println("SinglePlayerLobbyScene: L'IA n'est pas encore connectée");
            }
        }
        
        // Mettre à jour l'état de l'interface
        statusMessage = "Connexions réutilisées. Prêt à jouer.";
    }

    /**
     * Gestion des erreurs de connexion du client hôte
     * @param e Exception d'E/S
     */
    private void handleHostClientConnectionError(IOException e) {
        System.err.println("SinglePlayerLobbyScene: Erreur de connexion du client hôte: " + e.getMessage());
        e.printStackTrace();
        statusMessage = "Erreur client hôte: " + e.getMessage();
        JOptionPane.showMessageDialog(sceneManager.getPanel(),
                "Erreur de connexion du client hôte: " + e.getMessage(),
                "Erreur hôte", JOptionPane.ERROR_MESSAGE);
        repaintPanel();
        if (startGameButton != null) startGameButton.setEnabled(true);
    }

    /**
     * Nettoie les ressources et retourne au menu principal
     */
    private void cleanUpAndGoToMenu() {
        System.out.println("SinglePlayerLobbyScene: cleanUpAndGoToMenu appelé. transitioningToGameScene = " + transitioningToGameScene);
        if (!transitioningToGameScene && gameServerManager != null) {
            System.out.println("SinglePlayerLobbyScene: Arrêt du GameServerManager.");
            gameServerManager.stopServer();
            gameServerManager = null;
        } else if (transitioningToGameScene) {
             System.out.println("SinglePlayerLobbyScene: GameServerManager sera géré par GameScene.");
        }

        if (hostClient != null) {
            System.out.println("SinglePlayerLobbyScene: Déconnexion du hostClient.");
            hostClient.disconnect();
            hostClient = null;
        }
        sceneManager.setScene(new MenuScene(sceneManager));
    }

    /**
     * Initialisation de la scène
     */
    @Override
    public void init() {
        startTime = System.currentTimeMillis();
        alpha = 0f;
        fadeComplete = false;
        transitioningToGameScene = false;
        
        // 维持状态，但不重置连接状态
        boolean existingResourcesAvailable = hostClient != null && hostClient.isConnected() && 
                                            gameServerManager != null && gameServerManager.isServerRunning();
        
        if (!existingResourcesAvailable) {
            // 只有当没有现有连接时才重置状态
            aiPlayerConnected = false;
            serverSuccessfullyStarted = false;
            hostClientConnected = false;
            statusMessage = "Démarrage du serveur de jeu...";
        } else {
            // 使用现有连接时的状态消息
            statusMessage = "Utilisation des connexions existantes...";
            System.out.println("SinglePlayerLobbyScene: Utilisation des connexions existantes dans init()");
        }
        
        if(startGameButton != null) startGameButton.setEnabled(true);

        // 只有当没有现有连接时才启动网络初始化
        if (!existingResourcesAvailable) {
            SwingWorker<Void, String> initWorker = new SwingWorker<Void, String>() {
                @Override
                protected Void doInBackground() throws Exception {
                    publish("Préparation de l'environnement...");
                    
                    // Recherche et nettoyage de toutes les ressources réseau existantes
                    boolean resourcesCleanupNeeded = false;
                    
                    // 1. Vérifier si un GameServerManager existe et est actif
                    if (gameServerManager != null && gameServerManager.isServerRunning()){
                        System.out.println("SinglePlayerLobbyScene: Serveur existant détecté, tentative d'arrêt.");
                        publish("Arrêt du serveur existant...");
                        gameServerManager.stopServer();
                        gameServerManager = null;
                        resourcesCleanupNeeded = true;
                    }
                    
                    // 2. Rechercher tous les threads serveur actifs
                    for (Thread t : Thread.getAllStackTraces().keySet()) {
                        if ((t.getName().contains("GameServerThread") || t.getName().contains("Server")) && t.isAlive()) {
                            System.out.println("SinglePlayerLobbyScene: Thread serveur actif détecté: " + t.getName());
                            publish("Nettoyage d'un thread serveur actif: " + t.getName());
                            try {
                                t.interrupt();
                                resourcesCleanupNeeded = true;
                            } catch (Exception e) {
                                System.out.println("SinglePlayerLobbyScene: Erreur lors de l'interruption du thread: " + e.getMessage());
                            }
                        }
                    }
                    
                    // 3. Si des ressources ont été nettoyées, attendre plus longtemps pour la libération des ports
                    if (resourcesCleanupNeeded) {
                        publish("Attente de libération des ressources réseau...");
                        // Attendre plus longtemps pour être sûr que le port est libéré
                        for (int i = 0; i < 5; i++) {
                            Thread.sleep(500);
                            publish("Attente de libération des ports... (" + (i+1) + "/5)");
                        }
                        
                        // 4. Tenter de forcer la libération des ressources système
                        System.gc();
                        Thread.sleep(500);
                    }
                    
                    // Démarrer un nouveau serveur avec un numéro de port légèrement différent pour éviter les conflits
                    publish("Démarrage du serveur...");
                    
                    // 5. Créer un nouveau GameServerManager en mode IA
                    try {
                        // Créer un nouveau gestionnaire de serveur
                        gameServerManager = new GameServerManager(SinglePlayerLobbyScene.this, true);
                        gameServerManager.startServer();
                        
                        serverSuccessfullyStarted = true;
                        publish("Serveur en cours d'exécution sur IP: " + hostIP + ". Connexion au client hôte...");
                        System.out.println("SinglePlayerLobbyScene: Serveur démarré.");

                        Thread.sleep(300);

                        // Connexion du client hôte
                        hostClient = new GameClient("127.0.0.1", SinglePlayerLobbyScene.this);
                        // Assurez-vous que le client utilise le port correct du serveur
                        if (gameServerManager != null) {
                            int serverPort = gameServerManager.getCurrentPort();
                            hostClient.setServerPort(serverPort);
                            System.out.println("SinglePlayerLobbyScene: Configuration du client pour utiliser le port " + serverPort);
                        }
                        hostClient.connect();
                        hostClientConnected = true;
                        System.out.println("SinglePlayerLobbyScene: Client hôte (ID: " + hostClient.getMyPlayerId() + ") connecté. Attente du joueur IA...");
                        publish("Client hôte (joueur 1) connecté! Connexion au joueur IA...");
                        
                        // Connexion au joueur IA
                        publish("Connexion au joueur IA...");
                        gameServerManager.connectAIPlayer(selectedDifficulty);
                        aiPlayerConnected = true;
                        publish("Joueur IA connecté! Prêt à commencer.");

                    } catch (IOException e) {
                        serverSuccessfullyStarted = false;
                        hostClientConnected = false;
                        aiPlayerConnected = false;
                        System.err.println("SinglePlayerLobbyScene: Erreur grave lors de l'initialisation (serveur/client hôte): " + e.getMessage());
                        e.printStackTrace();
                        publish("Erreur d'initialisation: " + e.getMessage());
                        
                        // Nouvelle tentative avec délai supplémentaire si c'est une erreur d'adresse déjà utilisée
                        if (e.getMessage() != null && e.getMessage().contains("Address already in use")) {
                            publish("Port déjà utilisé. Attente supplémentaire pour libération...");
                            Thread.sleep(3000);
                            System.gc();
                            
                            try {
                                publish("Nouvelle tentative de démarrage du serveur...");
                                gameServerManager = new GameServerManager(SinglePlayerLobbyScene.this, true);
                                gameServerManager.startServer();
                                
                                serverSuccessfullyStarted = true;
                                publish("Serveur redémarré avec succès! Connexion au client hôte...");
                                
                                Thread.sleep(300);
                                
                                hostClient = new GameClient("127.0.0.1", SinglePlayerLobbyScene.this);
                                // Assurez-vous que le client utilise le port correct du serveur
                                if (gameServerManager != null) {
                                    int serverPort = gameServerManager.getCurrentPort();
                                    hostClient.setServerPort(serverPort);
                                    System.out.println("SinglePlayerLobbyScene: Configuration du client pour utiliser le port " + serverPort);
                                }
                                hostClient.connect();
                                hostClientConnected = true;
                                
                                publish("Client hôte (joueur 1) connecté! Connexion au joueur IA...");
                                gameServerManager.connectAIPlayer(selectedDifficulty);
                                aiPlayerConnected = true;
                                publish("Joueur IA connecté! Prêt à commencer.");
                            } catch (IOException retryE) {
                                throw new IOException("Échec de la seconde tentative: " + retryE.getMessage(), retryE);
                            }
                        } else {
                            throw e;
                        }
                    }
                    return null;
                }

                @Override
                protected void process(java.util.List<String> chunks) {
                    if (!chunks.isEmpty()) {
                        statusMessage = chunks.get(chunks.size() - 1);
                        repaintPanel();
                    }
                }

                @Override
                protected void done() {
                    try {
                        get();
                        if (!serverSuccessfullyStarted) {
                            statusMessage = "Erreur de démarrage du serveur.";
                            JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur serveur", JOptionPane.ERROR_MESSAGE);
                        } else if (!hostClientConnected) {
                            statusMessage = "Serveur démarré, mais connexion au client hôte échouée.";
                             JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur client hôte", JOptionPane.ERROR_MESSAGE);
                        } else if (!aiPlayerConnected) {
                            statusMessage = "Client hôte connecté, mais connexion au joueur IA échouée.";
                            JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur joueur IA", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception e) {
                        statusMessage = "Erreur d'initialisation: " + e.getMessage();
                         JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur d'initialisation", JOptionPane.ERROR_MESSAGE);
                    }
                    repaintPanel();
                }
            };
            initWorker.execute();
        } else {
            // 如果使用现有连接，确保AI难度设置正确
            if (gameServerManager != null) {
                gameServerManager.setAIDifficulty(selectedDifficulty);
                System.out.println("SinglePlayerLobbyScene: Difficulté de l'IA mise à jour: " + selectedDifficulty);
            }
            
            // 不需要再做初始化，直接设置状态为准备就绪
            statusMessage = "Connexions réutilisées. Prêt à jouer.";
            repaintPanel();
        }
        
        setupMouseListeners();
        lastDotTime = startTime;
        repaintPanel();
    }

    /**
     * Callback de connexion du joueur IA
     */
    public void onAIPlayerConnected() {
        SwingUtilities.invokeLater(() -> {
            if (!aiPlayerConnected) {
                aiPlayerConnected = true;
                statusMessage = "Joueur IA connecté! Prêt à commencer.";
                System.out.println("SinglePlayerLobbyScene: Connexion du joueur IA confirmée.");
                repaintPanel();
            }
        });
    }

    /**
     * Callback de déconnexion du joueur IA
     */
    public void onAIPlayerDisconnected() {
        SwingUtilities.invokeLater(() -> {
            aiPlayerConnected = false;
            statusMessage = "Joueur IA déconnecté. Tentative de reconnexion...";
            System.out.println("SinglePlayerLobbyScene: Joueur IA déconnecté.");
            startGameButton.setEnabled(false);
            
            // Tentative de reconnexion de l'IA
            if (gameServerManager != null && serverSuccessfullyStarted) {
                gameServerManager.connectAIPlayer(selectedDifficulty);
            }
            
            repaintPanel();
        });
    }

    /**
     * Mise à jour de la logique de la scène
     */
    @Override
    public void update() {
        if (!fadeComplete) {
            long elapsed = System.currentTimeMillis() - startTime;
            alpha = Math.min(1.0f, elapsed / 1000.0f);
            if (alpha >= 1.0f) fadeComplete = true;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDotTime > 500) {
            animationDots = (animationDots + 1) % 4;
            lastDotTime = currentTime;
        }

        if (fadeComplete && sceneManager.getPanel() != null) {
            Point mousePos = sceneManager.getPanel().getMousePosition();
            if (mousePos != null) {
                boolean canStartGame = serverSuccessfullyStarted && aiPlayerConnected && hostClientConnected && startGameButton.isEnabled();
                if (canStartGame) {
                    startGameButton.update(mousePos);
                } else {
                    if (startGameButton.contains(mousePos)) startGameButton.update(new Point(-1, -1));
                }
                backButton.update(mousePos);
            }
        }
    }

    /**
     * Rendu de la scène
     */
    @Override
    public void render(Graphics g, int width, int height) {
        g.setColor(new Color(40, 40, 80));
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Titre
        g2d.setColor(Color.WHITE);
        int titleFontSize = Math.min(width, height) / 18;
        g2d.setFont(new Font("Arial", Font.BOLD, titleFontSize));
        String titleText = "Mode Solo - Combat contre IA";
        FontMetrics titleMetricsFont = g2d.getFontMetrics();
        int titleTextWidth = titleMetricsFont.stringWidth(titleText);
        g2d.drawString(titleText, (width - titleTextWidth) / 2, height / 7);

        // Titre de sélection de la difficulté
        int infoFontSize = Math.min(width, height) / 28;
        g2d.setFont(new Font("Arial", Font.BOLD, infoFontSize));
        g2d.setColor(Color.LIGHT_GRAY);
        String difficultyText = "Sélectionner la difficulté:";
        FontMetrics diffMetrics = g2d.getFontMetrics();
        int diffTextWidth = diffMetrics.stringWidth(difficultyText);
        g2d.drawString(difficultyText, (width - diffTextWidth) / 2, height / 4 + 30);

        // Zone des joueurs
        int zoneWidth = width / 2 - 20;
        int zoneHeight = height / 3;
        int zoneY = height / 5;

        // Zone du joueur 1 (gauche)
        drawPlayerZone(g2d, 10, zoneY, zoneWidth, zoneHeight, true, hostClientConnected, 
                       hostClient != null ? String.valueOf(hostClient.getMyPlayerId()) : "?", infoFontSize);

        // Zone du joueur IA (droite)
        drawPlayerZone(g2d, width/2 + 10, zoneY, zoneWidth, zoneHeight, false, aiPlayerConnected, 
                       "IA", infoFontSize);

        // Message d'état
        if (statusMessage != null && !statusMessage.isEmpty()) {
            g2d.setFont(new Font("Arial", Font.ITALIC, Math.max(12, infoFontSize * 3 / 4)));
            FontMetrics statusMetrics = g2d.getFontMetrics();
            int statusWidth = statusMetrics.stringWidth(statusMessage);
            g2d.drawString(statusMessage, (width - statusWidth) / 2, height * 2 / 3 + 40);
        }

        // Paramètres du bouton
        int btnWidth = Math.max(180, width / 4);
        int btnHeight = Math.max(45, height / 13);
        int btnFontSize = Math.min(width, height) / 35;
        Font commonBtnFont = new Font("Arial", Font.BOLD, btnFontSize);

        // Placement du menu déroulant de sélection de difficulté
        if (sceneManager.getPanel() != null && !sceneManager.getPanel().isAncestorOf(difficultyComboBox)) {
            sceneManager.getPanel().add(difficultyComboBox);
        }
        int comboBoxWidth = 150;
        int comboBoxHeight = 30;
        difficultyComboBox.setBounds((width - comboBoxWidth) / 2, height / 4 + 40, comboBoxWidth, comboBoxHeight);
        difficultyComboBox.setFont(new Font("Arial", Font.PLAIN, Math.max(12, btnFontSize * 2/3)));
        difficultyComboBox.setVisible(fadeComplete); // Seulement visible après la fin de la transition

        // Bouton de démarrage du jeu
        startGameButton.setSize(btnWidth, btnHeight);
        startGameButton.setLocation(width / 2 - btnWidth / 2, height * 4 / 5);
        startGameButton.setFont(commonBtnFont);

        // Bouton de retour
        backButton.setSize(Math.max(120, btnWidth * 3/4), Math.max(35,btnHeight * 3/4));
        backButton.setLocation(40, height - Math.max(35,btnHeight * 3/4) - 25);
        backButton.setFont(new Font("Arial", Font.PLAIN, Math.max(12,btnFontSize * 3/4)));

        // Activation/désactivation du bouton de démarrage du jeu
        startGameButton.setEnabled(serverSuccessfullyStarted && aiPlayerConnected && hostClientConnected && !transitioningToGameScene);
        
        // Rendu du bouton
        startGameButton.render(g2d);
        backButton.render(g2d);
        
        g2d.dispose();
    }
    
    /**
     * 绘制玩家区域
     */
    private void drawPlayerZone(Graphics2D g2d, int x, int y, int width, int height, 
                             boolean isHost, boolean isConnected, String playerId, int fontSize) {
        g2d.setColor(new Color(60, 60, 100, 180));
        g2d.fillRoundRect(x, y, width, height, 20, 20);
        g2d.setColor(isHost ? new Color(100, 150, 255) : new Color(255, 150, 100));
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRoundRect(x, y, width, height, 20, 20);
        
        String playerTitle = isHost ? "Joueur" : "IA";
        g2d.setFont(new Font("Arial", Font.BOLD, fontSize * 3 / 2));
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int titleWidth = titleMetrics.stringWidth(playerTitle);
        g2d.drawString(playerTitle, x + (width - titleWidth) / 2, y + fontSize * 2);
        
        String statusText;
        if (isConnected) {
            statusText = isHost ? "Joueur: Connecté" : "IA (" + selectedDifficulty + "): Connecté";
            g2d.setColor(Color.GREEN);
        } else if (isHost && serverSuccessfullyStarted) {
            statusText = "Joueur: En cours de connexion...";
            g2d.setColor(Color.YELLOW);
        } else if (!isHost && serverSuccessfullyStarted && hostClientConnected) {
            String dots = "";
            for (int i = 0; i < animationDots; i++) dots += ".";
            statusText = "Attente de connexion IA" + dots;
            g2d.setColor(Color.YELLOW);
        } else {
            statusText = isHost ? "Joueur: (Serveur non prêt)" : "IA: En attente";
            g2d.setColor(Color.ORANGE);
        }
        
        g2d.setFont(new Font("Arial", Font.PLAIN, fontSize));
        FontMetrics statusMetrics = g2d.getFontMetrics();
        int statusWidth = statusMetrics.stringWidth(statusText);
        g2d.drawString(statusText, x + (width - statusWidth) / 2, y + fontSize * 4);
        
        if (isConnected) {
            g2d.setColor(Color.WHITE);
            String idText = "ID: " + playerId;
            FontMetrics idMetrics = g2d.getFontMetrics();
            int idWidth = idMetrics.stringWidth(idText);
            g2d.drawString(idText, x + (width - idWidth) / 2, y + fontSize * 6);
        }
    }

    /**
     * Configuration des écouteurs de souris
     */
    private void setupMouseListeners() {
        clearMouseListeners();
        mouseAdapterInternal = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!fadeComplete || transitioningToGameScene) return;
                Point mousePoint = e.getPoint();
                if (startGameButton.contains(mousePoint)) {
                    startGameButton.onClick();
                } else if (backButton.contains(mousePoint)) {
                    backButton.onClick();
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (!fadeComplete || transitioningToGameScene) return;
                Point mousePoint = e.getPoint();
                if (startGameButton.isEnabled() && startGameButton.contains(mousePoint)) startGameButton.setClicked(true);
                else if (backButton.isEnabled() && backButton.contains(mousePoint)) backButton.setClicked(true);
                repaintPanel();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if(transitioningToGameScene) return;
                startGameButton.setClicked(false);
                backButton.setClicked(false);
                repaintPanel();
            }
        };
        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().addMouseMotionListener(mouseAdapterInternal);
        }
    }

    /**
     * Suppression des écouteurs de souris
     */
    private void clearMouseListeners() {
        if (sceneManager.getPanel() != null && mouseAdapterInternal != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().removeMouseMotionListener(mouseAdapterInternal);
        }
    }

    /**
     * Rafraîchissement du panneau
     */
    private void repaintPanel() {
        if (sceneManager != null && sceneManager.getPanel() != null) {
            sceneManager.getPanel().repaint();
        }
    }

    /**
     * Définit si la transition vers la scène de jeu est en cours
     */
    public void setTransitioningToGameScene(boolean transitioning) {
        this.transitioningToGameScene = transitioning;
        System.out.println("SinglePlayerLobbyScene: transitioningToGameScene défini à " + transitioning);
    }

    /**
     * Libération des ressources
     */
    @Override
    public void dispose() {
        clearMouseListeners();
        System.out.println("SinglePlayerLobbyScene: Dispose appelé. transitioningToGameScene = " + transitioningToGameScene);

        // Suppression du menu déroulant de sélection de difficulté
        if (sceneManager.getPanel() != null && difficultyComboBox != null) {
            sceneManager.getPanel().remove(difficultyComboBox);
        }

        if (!transitioningToGameScene) {
            System.out.println("SinglePlayerLobbyScene: Pas de passage à GameScene pour la nettoyage.");
            if (hostClient != null) {
                hostClient.disconnect();
                hostClient = null;
            }
            if (gameServerManager != null) {
                gameServerManager.stopServer();
                gameServerManager = null;
            }
        } else {
            System.out.println("SinglePlayerLobbyScene: Passage à GameScene, gameServerManager et hostClient ne sont pas nettoyés ici.");
        }
        System.out.println("SinglePlayerLobbyScene: Dispose terminé.");
    }

    // Implémentation de l'interface GameStateUpdateListener

    @Override
    public void onGameStateUpdate(Jeu newGameState) {
        if (hostClient != null && hostClient.getGameInstance() == newGameState) {
             System.out.println("SinglePlayerLobbyScene (listener): État de jeu reçu pour le client hôte (ID: " + hostClient.getMyPlayerId() + ")");
        }
    }

    @Override
    public void onGameMessage(String messageType, String messageContent) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("SinglePlayerLobbyScene (listener): Message client hôte - Type: " + messageType + ", Contenu: " + messageContent);
            if ("ERROR".equalsIgnoreCase(messageType) || "DISCONNECTED".equalsIgnoreCase(messageType)) {
                statusMessage = "Client hôte: " + messageType + " - " + messageContent;
                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                        "Problème de connexion interne de l'hôte: " + messageContent,
                        "Erreur client hôte", JOptionPane.ERROR_MESSAGE);
                repaintPanel();
            }
        });
    }
} 