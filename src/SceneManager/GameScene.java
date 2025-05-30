package SceneManager;

import Modele.Coup;
import Modele.Jeu;
import Modele.Piece;
import Modele.Plateau;
import Network.AIClient;
import Network.GameClient;
import Network.GameServerManager;
import Network.GameStateUpdateListener;
import SceneManager.ResultScene;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class GameScene implements Scene, GameStateUpdateListener, GameServerManager.PlayerDisconnectionListener {

    private SceneManager sceneManager;
    private Jeu jeu; // Même état de jeu que dans le serveur

    private String police = "Utopia";

    // Ressources pour le fond et les images
    private BufferedImage backgroundImage;
    private BufferedImage crackPresentImage;
    private BufferedImage crackFutureImage;
    private BufferedImage lemielAvatarImage;
    private BufferedImage zarekAvatarImage;
    private long lastFrameUpdateTime = 0;
    private BufferedImage[][] zarekAnimation;
    private BufferedImage[][] lemielAnimation;
    private int frame = 0; // Pour l'animation
    int n = 0;

    // État de sélection de l'interface utilisateur
    private Point selectedPiecePosition = null;
    private Plateau.TypePlateau selectedPlateauType = null;
    private Coup.TypeCoup nextActionType = null; // On attend un clic pour DÉPLACER

    // Boutons UI
    private Button backButton; // Bouton pour revenir au menu principal
    private Button undoButton; // Bouton pour annuler une action
    private Button switchToAiButton; // Bouton pour se mettre en IA
    private Button redoButton;
    private Button choosePlateauButton; // Bouton pour choisir un plateau

    // Réseau et Mode de Jeu
    private GameClient gameClient;
    private String serverIpToConnectOnDemand; // IP pour que le client se connecte (si ce n'est pas l'hôte/solo)
    private boolean isOperatingInSinglePlayerMode; // True si c'est le mode Solo avec auto-hébergement

    private String statusMessage = "Initialisation...";
    private volatile boolean gameHasEnded = false; // volatile car peut être mis à jour depuis un autre thread
    // (onGameMessage)
    private volatile boolean isLoading = false; // Pour afficher l'état de chargement
    private int etapeCoup = 0; // Stocke directement la valeur de etapeCoup dans GameScene
    private volatile boolean handlingDisconnection = false; // Empêche onPlayerDisconnected d'être appelé plusieurs fois

    Point mousePoint;
    boolean transparent = false;
    boolean clone = false;
    Plateau.TypePlateau activeur = null;
    Point caseActivatrice = null;

    ArrayList<Point> casesPasse = new ArrayList<>();
    ArrayList<Point> casesPresent = new ArrayList<>();
    ArrayList<Point> casesFutur = new ArrayList<>();

    // Serveur et IA locaux pour le mode solo
    // Static pour garantir qu'il n'y a qu'une seule instance si GameScene est
    // recréée rapidement (même si dispose devrait gérer cela)
    private static GameServerManager localSinglePlayerServerManager;
    private static Thread localAIClientThread;
    private static AIClient aiClientInstance; // Conserve l'instance de l'IA pour pouvoir la déconnecter
    private int levelAI = 0;
    private boolean controlledByAI = false; // Indique si l'IA contrôle le jeu
    
    private boolean playerGoesFirst = true;

    private MouseAdapter mouseAdapterInternal;
    // MouseMotionListener est intégré dans MouseAdapter si mouseAdapterInternal
    // hérite de MouseAdapter et implémente MouseMotionListener
    // Ou créer une variable séparée pour MouseMotionListener

    private MouseAdapter mouseAdapterFeedForward;

    // Ajout pour la gestion du serveur en mode multijoueur
    private GameServerManager hostServerManager; // Instance du serveur reprise de HostingScene

    private boolean redoable = false; // Indique si on est en mode redo

    // Stocke les plateaux sélectionnés par les joueurs
    private Plateau.TypePlateau joueur1SelectedPlateau = Plateau.TypePlateau.PAST;
    private Plateau.TypePlateau joueur2SelectedPlateau = Plateau.TypePlateau.FUTURE;
    private Plateau.TypePlateau activePlateau = null;

    private int presentStartX;
    private int pastStartX;
    private int futureStartX;
    private int offsetY;

    private int tileWidth;
    private int boardSize;

    private Plateau plateauMouse;
    private int caseMouseX;
    private int caseMouseY;
    private int modeSolo = 1;

    private Button saveButton; // Bouton pour sauvegarder la partie


    // Constructeur pour le mode Solo (auto-hébergement du serveur et de l'IA)
    public GameScene(SceneManager sceneManager, boolean isSinglePlayer, int difficultyLevel) {
        this.sceneManager = sceneManager;
        this.isOperatingInSinglePlayerMode = isSinglePlayer;
        this.levelAI = difficultyLevel; // Niveau de difficulté de l'IA
        if (isSinglePlayer) {
            this.serverIpToConnectOnDemand = "127.0.0.1"; // Le client UI se connecte au serveur local
            this.statusMessage = "Mode Solo : Préparation...";
        } else {
            // Ce constructeur ne doit être appelé qu'avec isSinglePlayer = true
            throw new IllegalArgumentException(
                    "Pour le mode multijoueur client, utilisez le constructeur avec l'IP du serveur.");
        }
        loadResources();
        commonUIInit();
    }

    // Constructeur pour le mode Solo avec choix de l'ordre de jeu
    public GameScene(SceneManager sceneManager, boolean isSinglePlayer, int difficultyLevel, boolean playerGoesFirst) {
        this.sceneManager = sceneManager;
        this.isOperatingInSinglePlayerMode = isSinglePlayer;
        this.levelAI = difficultyLevel; // Niveau de difficulté de l'IA
        this.playerGoesFirst = playerGoesFirst;
        if (isSinglePlayer) {
            this.serverIpToConnectOnDemand = "127.0.0.1"; // Le client UI se connecte au serveur local
            this.statusMessage = "Mode Solo : Préparation...";
            // Note: L'ordre de jeu sera géré lors de la création du serveur local
            // Pour l'instant, nous stockons cette information pour l'utiliser plus tard
            System.out.println("GameScene: Mode solo initialisé - Joueur commence en premier: " + playerGoesFirst);
        } else {
            // Ce constructeur ne doit être appelé qu'avec isSinglePlayer = true
            throw new IllegalArgumentException(
                    "Pour le mode multijoueur client, utilisez le constructeur avec l'IP du serveur.");
        }
        loadResources();
        commonUIInit();
    }

    // Constructeur pour le client se connectant à un hôte en mode multijoueur
    public GameScene(SceneManager sceneManager, String serverIpToConnect) {
        this.sceneManager = sceneManager;
        this.isOperatingInSinglePlayerMode = false;
        this.serverIpToConnectOnDemand = serverIpToConnect;
        this.statusMessage = "Mode Multi: Connexion à l'hôte...";
        loadResources();
        commonUIInit();
    }

    // Constructeur pour l'hôte en mode multijoueur (avec un GameClient déjà
    // connecté depuis HostingScene)
    public GameScene(SceneManager sceneManager, GameClient alreadyConnectedHostClient,
            GameServerManager serverManager, boolean isSinglePlayerMode) {
        this.sceneManager = sceneManager;
        this.isOperatingInSinglePlayerMode = isSinglePlayerMode;
        this.gameClient = alreadyConnectedHostClient; // Utilise le client déjà connecté
        this.hostServerManager = serverManager; // Reprend la gestion du serveur

        // Définir cette classe comme listener de déconnexion pour le serveur
        if (this.hostServerManager != null) {
            this.hostServerManager.setDisconnectionListener(this);
            System.out.println("GameScene: Enregistré comme listener de déconnexion pour le serveur");
        }

        if (this.gameClient == null) {
            this.statusMessage = "Erreur fatale: Client hôte non fourni à GameScene.";
            System.err.println(this.statusMessage);
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                        "Erreur fatale lors de l'initialisation du jeu: Client hôte manquant.",
                        "Erreur d'Initialisation", JOptionPane.ERROR_MESSAGE);
                cleanUpAndGoToMenu();
            });
        } else if (!this.gameClient.isConnected()) {
            // Essayer de se reconnecter au lieu de retourner immédiatement au menu principal
            this.statusMessage = "Client déconnecté. Tentative de reconnexion...";
            System.err.println(this.statusMessage);

            try {
                // Essayer de se reconnecter
                boolean reconnected = this.gameClient.reconnect();
                if (reconnected) {
                    System.out.println("GameScene: Reconnexion réussie avec client ID: " + this.gameClient.getMyPlayerId());
                    this.gameClient.setListener(this); // Réinitialiser le listener
                } else {
                    System.err.println("GameScene: Échec de la reconnexion");
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(sceneManager.getPanel(),
                                "Impossible de se reconnecter au serveur. Retour au menu principal.",
                                "Erreur de Connexion", JOptionPane.ERROR_MESSAGE);
                        cleanUpAndGoToMenu();
                    });
                    return;
                }
            } catch (Exception e) {
                System.err.println("GameScene: Erreur lors de la tentative de reconnexion: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(sceneManager.getPanel(),
                            "Erreur lors de la tentative de reconnexion: " + e.getMessage(),
                            "Erreur de Connexion", JOptionPane.ERROR_MESSAGE);
                    cleanUpAndGoToMenu();
                });
                return;
            }
        }

        System.out.println("GameScene: Initialisation avec client hôte (ID: "
                + (this.gameClient != null && this.gameClient.isConnected() ? this.gameClient.getMyPlayerId() : "non connecté")
                + (this.hostServerManager != null ? ", avec serveur hôte" : ", sans serveur hôte"));

        // Définir le listener et obtenir l'état du jeu uniquement si le client est déjà connecté
        if (this.gameClient != null && this.gameClient.isConnected()) {
            this.gameClient.setListener(this); // Définit cette scène comme écouteur
            this.jeu = this.gameClient.getGameInstance(); // Obtient l'état initial du jeu

            if (this.jeu == null) {
                System.out.println("GameScene: Instance de jeu est null, attente de mise à jour du serveur");
                this.statusMessage = "En attente de l'état du jeu...";
            } else {
                System.out.println("GameScene: Instance de jeu reçue (Joueur courant: "
                        + (this.jeu.getJoueurCourant() != null ? this.jeu.getJoueurCourant().getId() : "non défini")
                        + ")");
                updateStatusFromCurrentGame(false); // Met à jour le message de statut initial
            }
        } else {
            // Si le client n'est pas connecté, définir un état approprié
            this.statusMessage = "Configuration GameScene invalide.";
            System.err.println(this.statusMessage);

            // Utiliser SwingUtilities pour garantir que les opérations UI sont exécutées sur l'EDT
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                        "Erreur: " + this.statusMessage,
                        "Erreur d'Initialisation", JOptionPane.ERROR_MESSAGE);
                cleanUpAndGoToMenu();
            });
            return;
        }

        loadResources();
        commonUIInit();
    }

    // Charger les ressources graphiques
    private void loadResources() {
        try {
            backgroundImage = ImageIO.read(new File("res/Background/Background.png"));
            crackPresentImage = ImageIO.read(new File("res/Plateau/Crack_Present.png"));
            crackFutureImage = ImageIO.read(new File("res/Plateau/Crack_Future.png"));
            lemielAvatarImage = ImageIO.read(new File("res/Character/Lemiel/Lemiel_Avatar.png"));
            zarekAvatarImage = ImageIO.read(new File("res/Character/Zarek/Zarek_Avatar.png"));

            // Charger les images d'animation de Zarek et Lemiel
            zarekAnimation = new BufferedImage[2][8]; // Idling and Aura
            lemielAnimation = new BufferedImage[2][8]; // Idling and Aura

            // Charger les images d'animation de Zarek
            for (int i = 0; i < 8; i++) {
                zarekAnimation[0][i] = ImageIO
                        .read(new File("res/Character/Zarek/Idle/Zarek_Idle_" + (i + 1) + ".png"));
                zarekAnimation[1][i] = ImageIO.read(new File("res/Character/Zarek/Aura/Aura-" + (i + 1) + ".png"));

                lemielAnimation[0][i] = ImageIO
                        .read(new File("res/Character/Lemiel/Idle/Lemiel_Idle_" + (i + 1) + ".png"));
                lemielAnimation[1][i] = ImageIO.read(new File("res/Character/Lemiel/Aura/Aura-" + (i + 1) + ".png"));
                // zarekImage = zarekAnimationIdle[0]; // Initialiser avec la première image
            }
        } catch (IOException e) {
            System.err.println("Error loading resources: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Modifie le constructeur original pour maintenir la compatibilité
    public GameScene(SceneManager sceneManager, GameClient alreadyConnectedHostClient) {
        this(sceneManager, alreadyConnectedHostClient, null, false);
    }

    private void commonUIInit() {
        // La position des boutons sera mise à jour dans render()
        backButton = new Button(0, 0, 150, 40, "Retour Menu", this::handleBackButton);
        // Ajouter un bouton pour annuler une action
        int undoX = sceneManager.getPanel().getWidth() / 2 - 50;
        int undoY = sceneManager.getPanel().getHeight() / 12 - 20;
        undoButton = new Button(undoX, undoY, 100, 40, "UNDO", this::handleUndoAction);

        int switchToAiX = undoX;
        int switchToAiY = undoY + 50;
        switchToAiButton = new Button(0, 0, 100, 40, "IA", this::handleSwitchToAiAction);

        int redoX = undoX;
        int redoY = undoY + 50; // Position du bouton REDO en dessous du bouton UNDO
        redoButton = new Button(redoX, redoY, 100, 40, "REDO", this::handleRedoAction);


        // Ajouter un bouton pour choisir un plateau
        choosePlateauButton = new Button(0, 0, 180, 40, "Choisir ce plateau", this::handleChoosePlateauAction);

        saveButton = new Button(0, 0, 150, 40, "Sauvegarder", this::handleSaveGame);
    }

    private void handleRedoAction() {
        gameClient.sendPlayerAction("1:redo:null:null:null");
    }

    private void handleSwitchToAiAction() {
        this.controlledByAI = !this.controlledByAI; // Inverse l'état de contrôle par l'IA
        gameClient.switchToAIMode();
    }

    private void handleBackButton() {
        int confirmation = JOptionPane.YES_OPTION;
        if (!gameHasEnded && (gameClient != null && gameClient.isConnected())) {
            confirmation = JOptionPane.showConfirmDialog(
                    sceneManager.getPanel(),
                    "Quitter la partie en cours ?",
                    "Confirmation de sortie",
                    JOptionPane.YES_NO_OPTION);
        }
        if (confirmation == JOptionPane.YES_OPTION) {
            cleanUpAndGoToMenu();
        }
    }



    private void cleanUpAndGoToMenu() {


        // Demander d'abord au joueur s'il souhaite se reconnecter, si c'est le joueur 2
        if (gameClient != null && !gameClient.isConnected() && gameClient.getMyPlayerId() == 2) {
            int choice = JOptionPane.showConfirmDialog(
                    sceneManager.getPanel(),
                    "La connexion au serveur a été perdue. Voulez-vous essayer de vous reconnecter?",
                    "Erreur de Connexion",
                    JOptionPane.YES_NO_OPTION);

            if (choice == JOptionPane.YES_OPTION) {
                try {
                    // Essayer de se reconnecter
                    if (gameClient.reconnect()) {
                        System.out.println("GameScene: Reconnexion réussie avec client ID: " + gameClient.getMyPlayerId());
                        this.gameClient.setListener(this); // Réinitialiser le listener
                        this.jeu = this.gameClient.getGameInstance(); // Obtenir l'état du jeu
                        if (this.jeu != null) {
                            updateStatusFromCurrentGame(false);
                            JOptionPane.showMessageDialog(sceneManager.getPanel(),
                                    "Reconnexion réussie! La partie va continuer.",
                                    "Reconnexion", JOptionPane.INFORMATION_MESSAGE);
                            return;
                        } else {
                            System.out.println("GameScene: Reconnexion réussie mais état du jeu est null");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("GameScene: Erreur lors de la tentative de reconnexion: " + e.getMessage());
                }

                // Si la reconnexion échoue, demander à l'utilisateur s'il souhaite revenir à l'écran de connexion
                int retry = JOptionPane.showConfirmDialog(
                        sceneManager.getPanel(),
                        "Échec de la reconnexion. Voulez-vous revenir à l'écran de connexion ?",
                        "Échec de Reconnexion",
                        JOptionPane.YES_NO_OPTION);

                if (retry == JOptionPane.YES_OPTION) {
                    // Déconnecter la connexion existante
                    if (gameClient != null) {
                        gameClient.disconnect();
                        gameClient = null;
                    }
                    // Retourner à l'écran de connexion à l'hôte
                    sceneManager.setScene(new ConnectHostScene(sceneManager));
                    return;
                }
            }
        }

        if (gameClient != null) {
            gameClient.disconnect();
            gameClient = null;
        }

        // Si cette GameScene gère le serveur hôte, l'arrêter
        if (hostServerManager != null) {
            System.out.println("GameScene: Arrêt du serveur hôte avant retour au menu.");
            hostServerManager.stopServer();
            hostServerManager = null;
        }

        // En mode solo, arrête le serveur et l'IA seulement si cette GameScene les a
        // créés
        if (isOperatingInSinglePlayerMode) {
            if (localAIClientThread != null && localAIClientThread.isAlive()) {
                if (aiClientInstance != null) {
                    aiClientInstance.disconnect(); // Demande à l'IA de se déconnecter
                }
                localAIClientThread.interrupt(); // Interrompt le thread de l'IA
                localAIClientThread = null;
                aiClientInstance = null;
                System.out.println("GameScene (Solo): Thread AI arrêté.");
            }
            if (localSinglePlayerServerManager != null && localSinglePlayerServerManager.isServerRunning()) {
                localSinglePlayerServerManager.stopServer();
                // Ne pas définir localSinglePlayerServerManager = null pour pouvoir vérifier
                // isServerRunning plus tard
                System.out.println("GameScene (Solo): Serveur local arrêté.");
            }
        }
        sceneManager.setScene(new MenuScene(sceneManager));
    }

     private void handleSaveGame() {

        String gamedata = gameClient.getGameInstance().getGameStateAsString();
        try {
           System.out.println("NetWorkGameSaveManager : ");
           Files.createDirectories(Paths.get("saves"));
        } catch (IOException e) {
           System.err.println("NetworkGameSaveManager: Unable to create save directory: " + e.getMessage());
           JOptionPane.showMessageDialog(sceneManager.getPanel(),
                "Erreur lors de la création du dossier de sauvegarde : " + e.getMessage(),
                "Erreur de Sauvegarde", JOptionPane.ERROR_MESSAGE);
        return;
        }
        if (gameClient == null || !gameClient.isConnected()) {
            JOptionPane.showMessageDialog(sceneManager.getPanel(),
                    "Impossible de sauvegarder : non connecté au serveur.",
                    "Erreur de Sauvegarde", JOptionPane.ERROR_MESSAGE);
            return;
        }

    // Demander le nom de la sauvegarde à l'utilisateur
    String saveName = JOptionPane.showInputDialog(sceneManager.getPanel(),
            "Entrez un nom pour cette sauvegarde (non nulle):",
            "Sauvegarder le Jeu",
            JOptionPane.PLAIN_MESSAGE);

    if (saveName != null && !saveName.trim().isEmpty()) {
        // Utiliser SwingWorker pour éviter de bloquer l'interface
        SwingWorker<Boolean, Void> saveWorker = new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {


                try {
                    // Créer le nom de fichier avec timestamp pour éviter les conflits
                    String timestamp = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                    String fileName = saveName + "_" + timestamp + ".save";
                    Path savePath = Paths.get("saves", fileName);

                    // Créer les métadonnées de sauvegarde
                    String saveMetadata = String.format("# Save Metadata\n" +
                            "SaveName: %s\n" +
                            "PlayerID: %d\n" +
                            "SaveTime: %s\n" +
                            "GameVersion: 1.0\n" +
                            "# Game Data\n%s",
                            saveName,
                            gameClient.getMyPlayerId(),
                            java.time.LocalDateTime.now().toString(),
                            gamedata);

                    // Écrire le fichier de sauvegarde
                    Files.write(savePath, saveMetadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));

                    Thread.sleep(500); // Petit délai pour que l'utilisateur puisse voir le message

                    return true;
                } catch (IOException e) {
                    System.err.println("Erreur lors de la sauvegarde : " + e.getMessage());
                    throw e;
                }
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        JOptionPane.showMessageDialog(sceneManager.getPanel(),
                                "Partie sauvegardée avec succès !",
                                "Sauvegarde Réussie", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(sceneManager.getPanel(),
                                "Erreur lors de la sauvegarde.",
                                "Erreur de Sauvegarde", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(sceneManager.getPanel(),
                            "Erreur lors de la sauvegarde : " + e.getMessage(),
                            "Erreur de Sauvegarde", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        saveWorker.execute();
      }
    }


    @Override
    public void init() {
        gameHasEnded = false;
        isLoading = true; // Commencez avec l'état de chargement
        resetSelection();

        setupMouseListeners(); // Toujours configurer les écouteurs lors de l'initialisation de la scène

        if (gameClient != null && gameClient.isConnected()) {
            // Cas où l'hôte entre dans GameScene, le client est déjà connecté et transmis
            System.out.println("GameScene: Utilisation du GameClient déjà connecté.");

            // Ajoute un mécanisme de timeout pour éviter d'attendre indéfiniment l'état du
            // jeu
            if (this.jeu == null) {
                System.out.println("GameScene: Attente de l'état initial du jeu avec timeout...");
                new SwingWorker<Boolean, Void>() {
                    @Override
                    protected Boolean doInBackground() throws Exception {
                        // Attend l'état du jeu pendant 5 secondes maximum
                        for (int i = 0; i < 25; i++) {
                            Thread.sleep(200);
                            if (jeu != null) {
                                return true;
                            }
                        }
                        return false;
                    }

                    @Override
                    protected void done() {
                        try {
                            boolean success = get();
                            isLoading = false;

                            if (success) {
                                System.out.println("GameScene: État de jeu reçu avec succès");
                                updateStatusFromCurrentGame(false);
                            } else {
                                System.err.println("GameScene: Timeout en attendant l'état du jeu");
                                statusMessage = "Erreur: Impossible d'obtenir l'état du jeu";
                                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                                        "Timeout en attendant l'état du jeu. Essayez de redémarrer la partie.",
                                        "Erreur de Synchronisation", JOptionPane.ERROR_MESSAGE);
                            }
                            repaintPanel();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }.execute();
            } else {
                isLoading = false; // L'hôte est connecté, pas besoin de chargement prolongé
                updateStatusFromCurrentGame(false);
            }
        } else if (isOperatingInSinglePlayerMode) {
            statusMessage = "Mode Solo: Démarrage du serveur local...";
            startSinglePlayerServerAndClients(); // Cela sera exécuté dans un SwingWorker
        } else if (serverIpToConnectOnDemand != null) { // Client se connectant à une IP
            statusMessage = "Connexion à " + serverIpToConnectOnDemand + "...";
            connectToRemoteServer(); // Cela sera exécuté dans un SwingWorker
        } else {
            statusMessage = "Erreur: Configuration GameScene invalide.";
            System.err.println(statusMessage);
            isLoading = false;
            // On peut ajouter une action pour revenir à MenuScene ici si l'erreur est
            // critique
            SwingUtilities.invokeLater(this::cleanUpAndGoToMenu);
        }
        repaintPanel();
    }

    private void startSinglePlayerServerAndClients() {
        isLoading = true;
        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                publish("Démarrage du serveur solo...");

                if (localSinglePlayerServerManager != null && localSinglePlayerServerManager.isServerRunning()) {
                    System.out.println("GameScene (Solo Worker): Serveur local solo déjà actif, tentative de réinitialisation.");
                    localSinglePlayerServerManager.stopServer();
                }

                localSinglePlayerServerManager = new GameServerManager(null); // callback = null
                // 设置单人模式的先后顺序
                localSinglePlayerServerManager.setTurnOrder(playerGoesFirst);
                localSinglePlayerServerManager.startServer(); // Lancement serveur local 127.0.0.1
                Thread.sleep(300); // Petit délai pour éviter les courses\

                if (modeSolo == 1) {
                    publish("Mode : UI vs IA");

                    gameClient = new GameClient("127.0.0.1", GameScene.this);
                    gameClient.connect();
                    publish("Joueur UI connecté (ID: " + gameClient.getMyPlayerId() + ")");

                    aiClientInstance = new AIClient("127.0.0.1", levelAI);
                    if (localSinglePlayerServerManager != null) {
                        int serverPort = localSinglePlayerServerManager.getCurrentPort();
                        aiClientInstance.setServerPort(serverPort);
                        System.out.println("GameScene (Solo): Configuration de l'AI pour utiliser le port " + serverPort);
                    }
                    aiClientInstance.connect();
                    if (aiClientInstance.isConnected()) {
                        aiClientInstance.startListeningAndPlaying();
                        publish("IA connectée (ID: " + aiClientInstance.getMyPlayerId() + ")");
                    } else {
                        throw new IOException("L'IA n'a pas pu se connecter.");
                    }

                    Thread.sleep(500); // Attente pour que le serveur détecte les connexions
                }

                if (localSinglePlayerServerManager.areAllPlayersConnected()) {
                    publish("Moteur de jeu prêt.");
                } else {
                    publish("En attente des connexions...");
                }

                return true;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String msg : chunks) {
                    statusMessage = msg;
                    System.out.println("GameScene (Solo Worker): " + msg);
                    repaintPanel();
                }
            }

            @Override
            protected void done() {
                isLoading = false;
                try {
                    Boolean success = get();
                    if (success) {
                        if (modeSolo == 1 && (gameClient == null || !gameClient.isConnected())) {
                            statusMessage = "Erreur connexion joueur UI.";
                        }
                    } else {
                        statusMessage = "Échec du démarrage du mode solo.";
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    statusMessage = "Erreur critique : " + e.getMessage();
                    JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur", JOptionPane.ERROR_MESSAGE);
                    cleanUpAndGoToMenu();
                }
                repaintPanel();
            }
        };
        worker.execute();
    }

    private void connectToRemoteServer() {
        isLoading = true;
        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                publish("Connexion à " + serverIpToConnectOnDemand + "...");
                gameClient = new GameClient(serverIpToConnectOnDemand, GameScene.this);
                gameClient.connect();
                return true;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String msg : chunks) {
                    statusMessage = msg;
                    repaintPanel();
                }
            }

            @Override
            protected void done() {
                isLoading = false;
                try {
                    get(); // Obtenir le résultat ou capturer une exception
                    if (gameClient == null || !gameClient.isConnected()) {
                        statusMessage = "Échec de la connexion à l'hôte.";
                        JOptionPane.showMessageDialog(sceneManager.getPanel(),
                                "Impossible de se connecter au serveur : " + serverIpToConnectOnDemand,
                                "Erreur de Connexion", JOptionPane.ERROR_MESSAGE);
                        cleanUpAndGoToMenu();
                    }
                    // Si la connexion réussit, le premier onGameStateUpdate définira correctement
                    // le statusMessage
                } catch (Exception e) {
                    e.printStackTrace();
                    statusMessage = "Erreur de connexion: " + e.getMessage();
                    JOptionPane.showMessageDialog(sceneManager.getPanel(),
                            "Impossible de se connecter au serveur : " + e.getMessage() + "\nIP : "
                            + serverIpToConnectOnDemand,
                            "Erreur de Connexion", JOptionPane.ERROR_MESSAGE);
                    cleanUpAndGoToMenu();
                }
                repaintPanel();
            }
        };
        worker.execute();
    }

    private boolean isMyTurn() {
        if (jeu == null) {
            // System.out.println("isMyTurn: false, jeu est null");
            return false;
        }

        if (jeu.getJoueurCourant() == null || gameClient == null || gameClient.getMyPlayerId() == -1) {
            // System.out.println("isMyTurn: false, jeu.getJoueurCourant(): "
            // + (jeu.getJoueurCourant() == null ? "null" : jeu.getJoueurCourant())
            // + ", gameClient: " + gameClient
            // + ", gameClient.getMyPlayerId(): " + (gameClient != null ?
            // gameClient.getMyPlayerId() : -1));
            return false;
        }

        boolean isMyTurn = jeu.getJoueurCourant().getId() == gameClient.getMyPlayerId();
        // System.out.println("isMyTurn: " + isMyTurn +
        // ", jeu.getJoueurCourant().getId(): " + jeu.getJoueurCourant().getId() +
        // ", gameClient.getMyPlayerId(): " + gameClient.getMyPlayerId());
        return isMyTurn;
    }

    private void handleBoardClick(Point mousePoint) {
        if (jeu == null || gameClient == null || !gameClient.isConnected() || gameHasEnded) {
            statusMessage = "Jeu non prêt ou déconnecté.";
            repaintPanel();
            return;
        }

        // Vérifiez si c'est votre tour
        if (!isMyTurn()) {
            statusMessage = "Ce n'est pas votre tour.";
            repaintPanel();
            return;
        }

        Plateau clickedPlateauObj = getPlateauFromMousePoint(mousePoint);
        Point boardCoords = getBoardCoordinates(mousePoint);

        if (clickedPlateauObj != null && boardCoords != null) { // Clic valide sur le plateau
            int clickedRow = boardCoords.x;
            int clickedCol = boardCoords.y;
            Plateau.TypePlateau clickedPlateauType = clickedPlateauObj.getType();

            // Version simplifiée : quel que soit l'étape, envoyez simplement les
            // coordonnées du clic et le type de plateau au serveur
            // Le serveur déterminera l'action en fonction de l'étape du jeu (etapeCoup)
            String command = "0:null:" + clickedPlateauType.name() + ":" + clickedRow + ":" + clickedCol;

            System.out.println("GameScene: Envoi des informations de clic - Type de plateau : " + clickedPlateauType
                    + ", Position : (" + clickedRow + "," + clickedCol
                    + "), etapeCoup : " + etapeCoup);

            // Si c'est le premier clic, enregistrez-le comme selectedPiecePosition
            // (uniquement pour afficher l'effet de sélection dans l'interface utilisateur)
            if (etapeCoup == 0) {
                selectedPiecePosition = new Point(clickedRow, clickedCol);
                selectedPlateauType = clickedPlateauType;
                statusMessage = "Sélection du pion (" + clickedPlateauType + " " + clickedRow + "," + clickedCol
                        + ")...";
            } else {
                statusMessage = "Action sur plateau " + clickedPlateauType + " à la position (" + clickedRow + ","
                        + clickedCol + ")...";
            }

            gameClient.sendPlayerAction(command);
            repaintPanel();
            // Clic en dehors du plateau
            if (etapeCoup == 3) {
                statusMessage = "Veuillez sélectionner un plateau (PASSÉ, PRÉSENT ou FUTUR).";
            } else if (etapeCoup == 0) {
                statusMessage = "Veuillez sélectionner un pion pour commencer.";
            } else {
                statusMessage = "Veuillez sélectionner une case valide pour votre action.";
            }
            repaintPanel();
        }
    }

    private void handleActionCommand(Point targetPosition) {
        if (jeu == null || gameClient == null || !gameClient.isConnected() || gameHasEnded) {
            statusMessage = "Jeu non prêt ou déconnecté.";
            repaintPanel();
            return;
        }

        if (!isMyTurn()) {
            statusMessage = "Ce n'est pas votre tour.";
            repaintPanel();
            return;
        }

        if (etapeCoup == 0 || etapeCoup == 3) {
            statusMessage = "Veuillez sélectionner directement sur le plateau.";
            repaintPanel();
            return;
        }

        if (selectedPiecePosition == null || selectedPlateauType == null) {
            statusMessage = "Sélectionnez d'abord un pion.";
            repaintPanel();
            return;
        }

        // Format d'envoi : <Annuler ? 1 : 0>:<ProchainPlateau:
        // null>:<PlateauSélectionné>:<x>:<y>
        String command = "0:null:" + selectedPlateauType.name() + ":" + targetPosition.x + ":" + targetPosition.y;
        gameClient.sendPlayerAction(command);
        statusMessage = "Commande envoyée: déplacement vers " + targetPosition.x + "," + targetPosition.y;
        repaintPanel();
    }

    // Ajouter une méthode spéciale pour l'annulation
    public void handleUndoAction() {
        if (jeu == null || gameClient == null || !gameClient.isConnected() || gameHasEnded) {
            return;
        }

        // if (!isMyTurn()) {
            // statusMessage = "Ce n'est pas votre tour.";
            // repaintPanel();
            // return;
        // }

        // Utiliser le champ etapeCoup de GameScene=
        // L'annulation n'a de sens que lorsque etapeCoup est égal à 1 ou 2
        // Format d'envoi : <Annuler ? 1 : 0>:<ProchainPlateau:
        // null>:<PlateauSélectionné>:<x>:<y>
        String command = "1:null:" + "PAST" + ":" + "0" + ":"
                + "0";
        gameClient.sendPlayerAction(command);
        statusMessage = "Demande d'annulation envoyée...";
        resetSelection();
        repaintPanel();

    }
    int framePicker = 0;
    int tictac = 0;

    @Override
    public void update() {
        // Mettre à jour l'animation de Zarek
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFrameUpdateTime > 250) { // Mettre à jour une image toutes les 250 ms
            frame = (frame + 1) % 8;
            if (tictac == 0) {
                framecountJ1 = (framecountJ1 + 1) % 2;
                framecountJ2 = (framecountJ2 + 1) % 2;
            }
            tictac = (tictac + 1) % 3;
            lastFrameUpdateTime = currentTime;
        }

        // Mettre à jour le survol uniquement si le jeu n'est pas en cours de chargement
        // et n'est pas terminé
        if (!isLoading && !gameHasEnded && sceneManager != null && sceneManager.getPanel() != null) {
            Point mousePos = sceneManager.getPanel().getMousePosition();
            if (mousePos != null) {
                // Traite d'abord le bouton retour
                boolean backContained = backButton.contains(mousePos);
                if (backContained) {
                    backButton.update(mousePos);
                } else if (!backContained && mousePos.x != -1) {
                    backButton.update(new Point(-1, -1));
                }

                boolean myTurn = isMyTurn(); // Vérifie si c'est le tour du joueur

                // Utiliser le champ etapeCoup de GameScene
                undoButton.update(mousePos);
                redoButton.update(mousePos);
                switchToAiButton.update(mousePos);
                saveButton.update(mousePos);

                // Le repaint à chaque mouvement de souris est nécessaire pour l'effet de survol
                repaintPanel();
            }
        }
    }

    private float time = 0f;
    private float speed = 0.5f; // Contrôle de la vitesse du mouvement
    private float centerX, centerY; // Centre du mouvement infini
    private float amplitude = 17f; // Taille du mouvement
    private float xinfJ1;
    private float yinfJ1;
    private float xinfJ2;
    private float yinfJ2;

    public void initInfiniteMovement(float centerX, float centerY, float speed) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.speed = speed;
    }

    public void changespeed(float speed) {
        this.speed = speed;
    }

    private void updatePickerPositionJ1(float delta) {
        time += delta * speed;

        // Lemniscate de Bernoulli version simplifiée avec sinus/cosinus
        xinfJ1 = amplitude * (float) Math.sin(time) / (1 + (float) Math.pow(Math.cos(time), 2));
        yinfJ1 = amplitude * (float) Math.sin(time) * (float) Math.cos(time) / (1 + (float) Math.pow(Math.cos(time), 2));

        // Positionner le picker
    }

    private void updatePickerPositionJ2(float delta) {
        time += delta * speed;

        // Lemniscate de Bernoulli version simplifiée avec sinus/cosinus
        xinfJ2 = amplitude * (float) Math.sin(time) / (1 + (float) Math.pow(Math.cos(time), 2));
        yinfJ2 = amplitude * (float) Math.sin(time) * (float) Math.cos(time) / (1 + (float) Math.pow(Math.cos(time), 2));

        // Positionner le picker
    }

    int framecountJ1 = 0;
    int framecountJ2 = 0;
    int frameJ1 = 0, frameJ2 = 0;

    private static final int[][] INFINITY_POINTS = {
        {0, 2}, {1, 1}, {1, 3}, {2, 0}, {2, 4},
        {3, 1}, {3, 3}, {4, 2}, {5, 2}, {6, 1},
        {6, 3}, {7, 0}, {7, 4}, {8, 1}, {8, 3},
        {9, 2}
    };

    public static int[] getInfinityPoint(int index) {
        if (index < 0 || index >= INFINITY_POINTS.length) {
            throw new IllegalArgumentException("Index hors limites (0 à " + (INFINITY_POINTS.length - 1) + ")");
        }
        return INFINITY_POINTS[index];
    }

    @Override
    public void render(Graphics g, int width, int height) {
        int centerX = width / 2;
        int centerY = height / 2;

        // Afficher la valeur actuelle de etapeCoup pour le débogage
        if (jeu != null) {
            // System.out.println("GameScene render: etapeCoup = " + etapeCoup);
        }

        // Mettre à jour la position du bouton "Retour" et "Annuler"
        int undoSizeX = 150 * width / 1920; // Largeur du bouton "Annuler"
        int undoSizeY = 60 * width / 1920;
        int undoPosY = height / 11; // Position Y du bouton "Annuler"
        undoButton.setSize(undoSizeX, undoSizeY);
        undoButton.setFont(new Font(police, Font.BOLD, 20 * width / 1920));
        undoButton.setLocation((width - (150 * width / 1920)) / 2, undoPosY);

        backButton.setSize(150 * width / 1920, 60 * width / 1920);
        backButton.setFont(new Font(police, Font.BOLD, 20 * width / 1920));


        int spacingY = undoSizeY * 10 / 100;
        redoButton.setSize(150 * width / 1920, 60 * width / 1920);
        redoButton.setFont(new Font(police, Font.BOLD, 20 * width / 1920));
        redoButton.setLocation((width - (150 * width / 1920)) / 2, undoPosY + undoSizeY + spacingY);

        int switchToAiButtonX = (width - ((150+320) * width / 1920)) / 2; // À gauche du bouton undo
        int switchToAiButtonY = height / 11 + 20; // Même hauteur que le bouton undo
        switchToAiButton.setSize(150 * width / 1920, 60 * width / 1920);
        switchToAiButton.setFont(new Font(police, Font.BOLD, 20 * width / 1920));
        switchToAiButton.setLocation(switchToAiButtonX, switchToAiButtonY);

        // Créer un Graphics2D pour le rendu
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // // Dessiner l'image de fond
        if (backgroundImage != null) {
            g2d.drawImage(backgroundImage, 0, 0, width, height, null);
        } else {
            // Si le fond n'a pas pu être chargé, utiliser la couleur de fond par défaut
            g2d.setColor(new Color(25, 25, 35));
            g2d.fillRect(0, 0, width, height);
        }

        // Si en cours de chargement, afficher un message de chargement
        if (isLoading) {
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 24));
            FontMetrics metrics = g2d.getFontMetrics();
            String loadingMsg = statusMessage != null ? statusMessage : "Chargement...";
            if (loadingMsg.contains("Démarrage AI") || loadingMsg.contains("Attente que l'AI se connecte")) {
                loadingMsg += "..."; // Ajouter une animation simple avec des points
            }
            int msgWidth = metrics.stringWidth(loadingMsg);
            g2d.drawString(loadingMsg, (width - msgWidth) / 2, height / 2);
            g2d.dispose();
            return; // Ne rien dessiner d'autre si en cours de chargement
        }

        if (jeu != null) {
            // Définir la position du bouton "Choisir un plateau" - plus grand et centré
            int choosePlateauWidth = width * 28 / 100; // 28% de la largeur du panneau
            int choosePlateauHeight = choosePlateauWidth; // 32% de la hauteur du panneau car ratio est 16:9
            choosePlateauButton.setSize(choosePlateauWidth, choosePlateauHeight);

            // Spacine entre les plateaux
            int spacing = width / 20; // Espace entre les plateaux, 5% de la largeur du panneau

            // Definir les tailles de plateau (dynamique en fonction de la taille du
            // panneau)
            boardSize = jeu.getTAILLE();
            tileWidth = choosePlateauHeight / boardSize; // Taille de la tuile basée sur la hauteur du panneau
            int tileHeight = tileWidth * 1; // A changer pour dessiner isométriquement
            int deltaX = 0; // Décalage horizontal pour centrer les plateaux -> Pour ce moment pas
            // d'isométrie

            // Definir la position depart de chaque plateau
            presentStartX = centerX - choosePlateauWidth / 2 + deltaX;
            pastStartX = presentStartX - choosePlateauWidth - spacing;
            futureStartX = presentStartX + choosePlateauWidth + spacing;
            offsetY = centerY - choosePlateauHeight / 2 + tileHeight / 2; // Centrer verticalement les plateaux

            Plateau past = jeu.getPast();
            Plateau present = jeu.getPresent();
            Plateau future = jeu.getFuture();

            //Save
            int saveButtonX = (width + (170 * width / 1920)) / 2 ; // À droite du bouton undo
            int saveButtonY = height / 11 + 20; // Même hauteur que le bouton undo

            saveButton.setSize(150 * width / 1920, 60 * width / 1920);
            saveButton.setFont(new Font(police, Font.BOLD, 20 * width / 1920));
            saveButton.setLocation(width-150 * width / 1920, height-60 * width / 1920);

            // Activer le bouton seulement si c'est le tour du joueur ou si le jeu n'est pas terminé
            saveButton.setEnabled(!gameHasEnded && gameClient != null && gameClient.isConnected());

            if (isMyTurn()) {

                g2d.setFont(new Font(police, Font.BOLD, 25 * width / 1920));
                String selectBoardMessage = "Votre tour !";
                FontMetrics metrics = g2d.getFontMetrics();
                int selectMsgWidth = metrics.stringWidth(selectBoardMessage);

                //rectangle autour du text
                g2d.setColor(new Color(0, 0, 0, 150));
                g2d.fillRoundRect(((width - (selectMsgWidth + 200 * width / 1920)) / 2), (height - 100 * width / 1920), (selectMsgWidth + 200 * width / 1920), 50 * width / 1920, 10, 10);
                g2d.drawRoundRect(((width - (selectMsgWidth + 200 * width / 1920)) / 2), (height - 100 * width / 1920), (selectMsgWidth + 200 * width / 1920), 50 * width / 1920, 10, 10);
                // Centrer le message en bas
                g2d.setColor(Color.YELLOW);
                g2d.drawString(selectBoardMessage, (width - selectMsgWidth) / 2, height - 62 * width / 1920);


                // undoButton.setEnabled(true);
                
                saveButton.setEnabled(true);

            } else {

                // undoButton.setEnabled(false);
                undoButton.setEnabled(true);
                
                saveButton.setEnabled(true);
            }

            if (redoable) {
                redoButton.setEnabled(true);
            } else {
                redoButton.setEnabled(false);
            }

            // // Afficher le message de sélection de plateau
            // String AIControl = null;
            // if (controlledByAI) {
            //     AIControl = "Contrôlé par l'IA";
            // } else {
            //     AIControl = "Contrôlé par vous";
            // }

            // g2d.setFont(new Font(police, Font.BOLD, 36 * width / 1920));
            // FontMetrics font = g2d.getFontMetrics();
            // int AIMsgWidth = font.stringWidth(AIControl);

            // Position
            // int AIMsgX = pastStartX + tileWidth * 2 - AIMsgWidth / 2;
            // int AIMsgY = height - 62 * width / 1920;

            //rectangle autour du text
            // g2d.setColor(new Color(0, 0, 0, 150));
            // g2d.fillRoundRect(((width - (AIMsgWidth + 200 * width / 1920)) / 2), (height - 100 * width / 1920), (AIMsgWidth + 200 * width / 1920), 50 * width / 1920, 10, 10);
            // g2d.drawRoundRect(((width - (AIMsgWidth + 200 * width / 1920)) / 2), (height - 100 * width / 1920), (AIMsgWidth + 200 * width / 1920), 50 * width / 1920, 10, 10);
            // Centrer le message en bas
            // g2d.setColor(Color.YELLOW);
            // g2d.drawString(AIControl, AIMsgX, AIMsgY);

            // Dessiner le nombre de clones 
            drawClones(g2d, gameClient.getMyPlayerId());

            //dessiner le picker
            changespeed(0.6f * width / 1920);
            if (isMyTurn() && gameClient.getMyPlayerId() == 1) {

                updatePickerPositionJ1(0.1f); // Mettre à jour la position du picker

            } else if (isMyTurn() && gameClient.getMyPlayerId() == 2) {
                updatePickerPositionJ2(0.1f); // Mettre à jour la position du picker
            } else {
                xinfJ1 = 0;
                yinfJ1 = 0;
                xinfJ2 = 0;
                yinfJ2 = 0;
            }

            drawPickerJ1(g2d, pastStartX + (int) xinfJ1, presentStartX + (int) xinfJ1, futureStartX + (int) xinfJ1, offsetY + (int) yinfJ1, tileWidth);
            drawPickerJ2(g2d, pastStartX + (int) xinfJ2, presentStartX + (int) xinfJ2, futureStartX + (int) xinfJ2, offsetY + (int) yinfJ2, tileWidth);

            // Dessiner les plateaux
            drawPlateau(g2d, past, pastStartX, offsetY, tileWidth, "PASSÉ", null);
            drawPlateau(g2d, present, presentStartX, offsetY, tileWidth, "PRÉSENT", crackPresentImage);
            drawPlateau(g2d, future, futureStartX, offsetY, tileWidth, "FUTURE", crackFutureImage);

            // À l'étape etapeCoup=3, mettez en surbrillance le plateau sélectionné par
            // l'utilisateur (s'il y en a un)
            if (etapeCoup == 3) {

                // À l'étape etapeCoup=3, assurez-vous que le bouton "Choisir un plateau" est
                // affiché
                if (isMyTurn()) {

                    // Afficher le texte d'invite
                    //g2d.setColor(Color.YELLOW);
                    g2d.setFont(new Font(police, Font.BOLD, 26 * width / 1920));
                    String selectBoardMessage = "Sélectionnez un plateau pour le prochain tour";
                    FontMetrics metrics = g2d.getFontMetrics();
                    int selectMsgWidth = metrics.stringWidth(selectBoardMessage);
                    //rectangle autour du texte
                    g2d.setColor(new Color(0, 0, 0, 150));
                    g2d.fillRoundRect(((width - (selectMsgWidth + 100 * width / 1920)) / 2), offsetY - 110 * width / 1920, selectMsgWidth + 100 * width / 1920, 70 * width / 1920, 10, 10);
                    g2d.drawRoundRect(((width - (selectMsgWidth + 100 * width / 1920)) / 2), offsetY - 110 * width / 1920, selectMsgWidth + 100 * width / 1920, 70 * width / 1920, 10, 10);
                    //g2d.setColor(Color.WHITE);
                    g2d.setColor(Color.YELLOW);
                    g2d.drawString(selectBoardMessage, (width - selectMsgWidth) / 2, offsetY - 65 * width / 1920);
                }
            }

            // Dessiner les trois plateaux, en mettant en évidence le plateau actif
            int myPlayerId = gameClient != null ? gameClient.getMyPlayerId() : -1;
            int currentPlayerId = jeu.getJoueurCourant() != null ? jeu.getJoueurCourant().getId() : -1;

            caseMouseX = -1;
            caseMouseY = -1;

            Plateau p = null;
            Point casePoint = null;
            if (mousePoint != null) {
                p = getPlateauFromMousePoint(mousePoint);
                plateauMouse = p;
                if (p != null) {
                    casePoint = getCaseFromMousePoint(p, mousePoint);
                    if (casePoint != null) {
                        caseMouseY = (int) casePoint.getY();
                        caseMouseX = (int) casePoint.getX();
                    }

                }
            }

            /*if (casePoint != null) {
                System.out.println("Plateau " + p.getType() + " CASE : " + casePoint.getX() + ", " + casePoint.getY());
            }*/
            if (etapeCoup == 3 && isMyTurn()) {
                // Feedforward des plateaux
                g2d.setColor(new Color(0x8DE2DE));
                //g2d.setColor(Color.WHITE);

                Stroke originalStroke = g2d.getStroke();
                g2d.setStroke(new BasicStroke(6f * width / 1920f)); // Épaisseur de la bordure

                if (gameClient.getMyPlayerId() == 1) {
                    activePlateau = joueur1SelectedPlateau;
                } else {
                    activePlateau = joueur2SelectedPlateau;
                }

                if (p != null && p.getType() != activePlateau) {
                    switch (p.getType()) {
                        case PAST ->
                            g2d.drawRoundRect(pastStartX - 2, offsetY - 2, tileWidth * past.getSize() + 4,
                                    tileWidth * past.getSize() + 4, 5, 5);
                        case PRESENT ->
                            g2d.drawRoundRect(presentStartX - 2, offsetY - 2, tileWidth * present.getSize() + 4,
                                    tileWidth * present.getSize() + 4, 5, 5);
                        case FUTURE ->
                            g2d.drawRoundRect(futureStartX - 2, offsetY - 2, tileWidth * future.getSize() + 4,
                                    tileWidth * future.getSize() + 4, 5, 5);
                    }
                }
                g2d.setStroke(originalStroke);

                // Dessiner le texte d'invite
                // g2d.setColor(Color.YELLOW);
                // g2d.setFont(new Font("Arial", Font.BOLD, 18));
                // String selectBoardMessage = "Sélectionnez un plateau pour le prochain tour";
                // FontMetrics metrics = g2d.getFontMetrics();
                // int selectMsgWidth = metrics.stringWidth(selectBoardMessage);
                // g2d.drawString(selectBoardMessage, (width - selectMsgWidth) / 2, offsetY - 20);

            }

            // Message de statut
            //Message status of
            // g2d.setColor(Color.CYAN);
            // g2d.setFont(new Font("Consolas", Font.BOLD, 18));
            // if (statusMessage != null && !statusMessage.isEmpty()) {
            //     FontMetrics metrics = g2d.getFontMetrics();
            //     int msgWidth = metrics.stringWidth(statusMessage);
            //     g2d.drawString(statusMessage, (width - msgWidth) / 2, 40);
            // }
            // Rendre les boutons

            backButton.render(g2d);

            // Afficher le bouton Annuler seulement si c'est mon tour et que etapeCoup n'est
            // pas égal à 3
            undoButton.render(g2d);
            if(myPlayerId == 1){
                saveButton.render(g2d);
            }
            switchToAiButton.render(g2d);
            redoButton.render(g2d);

        } else { // jeu est null (état initial non encore reçu)
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 22));
            FontMetrics metrics = g2d.getFontMetrics();
            String currentStatus = (statusMessage != null && !statusMessage.isEmpty()) ? statusMessage
                    : "Chargement des données du jeu...";
            int msgWidth = metrics.stringWidth(currentStatus);
            g2d.drawString(currentStatus, (width - msgWidth) / 2, height / 2);
        }

        // Rendu des boutons
        backButton.render(g2d);

        g2d.dispose();
    }

    private void drawClones(Graphics2D g, int gameClientId) {
        int width = sceneManager.getPanel().getWidth();
        int centerX = width / 2;
        int height = sceneManager.getPanel().getHeight();

        int choosePlateauWidth = width * 28 / 100; // 28% de la largeur du panneau
        int choosePlateauHeight = choosePlateauWidth; // 32% de la hauteur du panneau car ratio est 16:9
        choosePlateauButton.setSize(choosePlateauWidth, choosePlateauHeight);

        // Spacine entre les plateaux
        int topMargin = height * 2 / 25; // Espace pour statusMessage, 8% de la hauteur du panneau
        int sideMargin = width * 3 / 100; // Espace pour les bords, 3% de la largeur du panneau

        int actualLemielClones = jeu.getJoueur1().getNbClones();
        int actualZarekClones = jeu.getJoueur2().getNbClones();

        String J1text = null, J2text = null;
        switch (gameClientId) {
            case 1 -> {
                J1text = "VOUS ";
                J2text = "RIVAL ";
            }
            case 2 -> {
                J1text = "RIVAL ";
                J2text = "VOUS ";
            }
            default -> {
                // Do nothing
            }
        }

        int rectWidth = width * 26 / 100; // 28% de la largeur du panneau
        int rectHeight = height * 10 / 100; // 10% de la hauteur du panneau
        int lemielCloneX = sideMargin;
        int zarekCloneX = sceneManager.getPanel().getWidth() - choosePlateauWidth - sideMargin / 2;
        int cloneY = topMargin;

        // Draw rectangle background
        g.setColor(new Color(40, 40, 80, 200));
        g.fillRoundRect(lemielCloneX, cloneY, rectWidth, rectHeight, 12, 12);
        g.fillRoundRect(zarekCloneX, cloneY, rectWidth, rectHeight, 12, 12);

        // Draw border
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(lemielCloneX, cloneY, rectWidth, rectHeight, 12, 12);
        g.drawRoundRect(zarekCloneX, cloneY, rectWidth, rectHeight, 12, 12);

        // Dessiner le texte "YOU:"
        g.setFont(new Font(police, Font.BOLD, 18)); // Il est préférable de choisir une taille de police adaptée à rectHeight
        FontMetrics fm = g.getFontMetrics();
        int textAscent = fm.getAscent(); // Hauteur du sommet de la police à partir de la ligne de base
        int textHeight = fm.getHeight(); // Hauteur totale de la police

        int J1textStringWidth = fm.stringWidth(J1text); // Largeur de la chaîne J1text 
        int J2textStringWidth = fm.stringWidth(J2text); // Largeur de la chaîne J2text 

        int textPaddingX = width / 100; // Petite marge depuis le bord gauche du rectangle
        int J1textX = lemielCloneX + textPaddingX;
        int J2textX = zarekCloneX + textPaddingX;
        // Centrer le texte verticalement dans rectHeight
        int textY = cloneY + (rectHeight - textHeight) / 2 + textAscent;

        //g.setColor(Color.CYAN);
        g.drawString(J1text, J1textX, textY);
        g.drawString(J2text, J2textX, textY);

        // Calculer la taille et la position des images de clones
        int imagePaddingFromText = width * 1 / 100; // Petite marge après le texte "YOU:"
        int startImagesJ1X = J1textX + J1textStringWidth + imagePaddingFromText; // Position X de départ pour dessiner la première image
        int startImagesJ2X = J2textX + J2textStringWidth + imagePaddingFromText; // Position X de départ pour dessiner la première image

        // La hauteur de chaque image de clone est égale à la hauteur du rectangle qui la contient
        int singleImageDisplayHeight = rectHeight - (height * 1 / 100); // Réduit un peu pour avoir du padding en haut/bas dans le rectangle
        int singleImageDisplayWidth = 0;
        if (lemielAnimation[0][0].getHeight() > 0) { // Éviter la division par zéro
            singleImageDisplayWidth = singleImageDisplayHeight * lemielAnimation[0][0].getWidth() / lemielAnimation[0][0].getHeight();
        } // Le meme pour Zarek alors on utilise la meme valeur

        int imageSpacing = width * 1 / 100; // Espacement entre les images de clones (ajustable)

        // Dessiner les images de clones de Lemiel
        for (int i = 0; i < actualLemielClones; i++) {
            int currentImageX = startImagesJ1X + i * (singleImageDisplayWidth + imageSpacing);
            // Centrer l'image verticalement dans rectY et rectHeight
            int currentImageY = cloneY + (rectHeight - singleImageDisplayHeight) / 2;
            if (clone == true && gameClient.getMyPlayerId() == 1 && i == actualLemielClones - 1) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            }
            g.drawImage(lemielAnimation[0][0], currentImageX, currentImageY, singleImageDisplayWidth, singleImageDisplayHeight, null);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }

        // Dessiner les images de clones de Zarek
        for (int i = 0; i < actualZarekClones; i++) {
            int currentImageX = startImagesJ2X + i * (singleImageDisplayWidth + imageSpacing);
            // Centrer l'image verticalement dans rectY et rectHeight
            int currentImageY = cloneY + (rectHeight - singleImageDisplayHeight) / 2;
            if (clone == true && gameClient.getMyPlayerId() == 2 && i == actualLemielClones - 1) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            }
            g.drawImage(zarekAnimation[0][0], currentImageX, currentImageY, singleImageDisplayWidth, singleImageDisplayHeight, null);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }

    }

    private void drawPickerJ1(Graphics2D g, int pastStartX, int presentStartX, int futureStartX,
            int offsetY, int tileWidth) {
        float sizef = tileWidth / ((float) 1.9);
        int size = (int) sizef; // Taille du picker du prochain plateau => 7% de height
        int spacing = tileWidth / 4; // Espace entre les boutons du picker => 4% de width
        int pickerY = (int) (offsetY - spacing - size); // Position Y du picker, juste en dessous des plateaux

        int[] xPos = {
            pastStartX + tileWidth * 2 - (int) size / 2, // Position pour le plateau passé
            presentStartX + tileWidth * 2 - (int) size / 2, // Position pour le plateau présent
            futureStartX + tileWidth * 2 - (int) size / 2 // Position pour le plateau futur
        };

        Shape oldClip = g.getClip();

        // Pour le plateau courant, dessiner un oval de sélection
        g.setColor(new Color(0, 0, 0, 128));

        // Dessiner l'avatar
        switch (jeu.getJoueur1().getProchainPlateau()) {
            case PAST:
                if (lemielAvatarImage != null) {
                    g.setColor(new Color(0, 0, 0, 128));
                    g.fillOval(xPos[0], pickerY, size, size); // Fond noir
                    g.setColor(new Color(240, 217, 134));
                    g.drawOval(xPos[0], pickerY, size, size);
                    g.drawImage(lemielAvatarImage, xPos[0], pickerY, size, size, null);
                }
                break;

            case PRESENT:
                if (lemielAvatarImage != null) {
                    g.setColor(new Color(0, 0, 0, 128));
                    g.fillOval(xPos[1], pickerY, size, size);
                    g.setColor(new Color(240, 217, 134));
                    g.drawOval(xPos[1], pickerY, size, size);
                    g.drawImage(lemielAvatarImage, xPos[1], pickerY, size, size, null);
                }
                break;

            case FUTURE:
                if (lemielAvatarImage != null) {
                    g.setColor(new Color(0, 0, 0, 128));
                    g.fillOval(xPos[2], pickerY, size, size);
                    g.setColor(new Color(240, 217, 134));
                    g.drawOval(xPos[2], pickerY, size, size);
                    g.drawImage(lemielAvatarImage, xPos[2], pickerY, size, size, null);
                }
                break;
        }

        g.setClip(oldClip); // Restaurer le clip précédent
    }

    private void drawPickerJ2(Graphics2D g, int pastStartX, int presentStartX, int futureStartX,
            int offsetY, int tileWidth) {
        float sizef = tileWidth / ((float) 1.9);
        int size = (int) sizef; // Taille du picker du prochain plateau => 7% de height
        int spacing = tileWidth / 4; // Espace entre les boutons du picker => 4% de width
        int pickerY = offsetY + tileWidth * jeu.getTAILLE() + spacing; // Position Y du picker, juste en dessous des
        // plateaux

        int[] xPos = {
            pastStartX + tileWidth * 2 - size / 2, // Position pour le plateau passé
            presentStartX + tileWidth * 2 - size / 2, // Position pour le plateau présent
            futureStartX + tileWidth * 2 - size / 2 // Position pour le plateau futur
        };

        Shape oldClip = g.getClip();

        // Pour le plateau courant, dessiner un oval de sélection
        // Dessiner l'avatar
        switch (jeu.getJoueur2().getProchainPlateau()) {
            case PAST:
                if (zarekAvatarImage != null) {
                    g.setColor(new Color(255, 255, 255, 128));
                    g.fillOval(xPos[0], pickerY, size, size);
                    g.setColor(new Color(0, 0, 0));
                    g.drawOval(xPos[0], pickerY, size, size);
                    g.drawImage(zarekAvatarImage, xPos[0], pickerY, size, size, null);
                }
                break;

            case PRESENT:
                if (zarekAvatarImage != null) {
                    g.setColor(new Color(255, 255, 255, 128));
                    g.fillOval(xPos[1], pickerY, size, size);
                    g.setColor(new Color(0, 0, 0));
                    g.drawOval(xPos[1], pickerY, size, size);
                    g.drawImage(zarekAvatarImage, xPos[1], pickerY, size, size, null);
                }
                break;

            case FUTURE:
                if (zarekAvatarImage != null) {
                    g.setColor(new Color(255, 255, 255, 128));
                    g.fillOval(xPos[2], pickerY, size, size);
                    g.setColor(new Color(0, 0, 0));
                    g.drawOval(xPos[2], pickerY, size, size);
                    g.drawImage(zarekAvatarImage, xPos[2], pickerY, size, size, null);
                }
                break;
        }

        g.setClip(oldClip); // Restaurer le clip précédent
    }

    private void drawPlateau(Graphics2D g, Plateau plateau, int x, int y, int tileWidth, String title,
            BufferedImage crackImage) {
        int boardSize = plateau.getSize();
        int boardPixelSize = boardSize * tileWidth;
        int tileHeight = tileWidth * 1; // A changer pour dessiner isométriquement

        // Bordure
        g.setColor(new Color(80, 80, 80));
        //g.setStroke(new BasicStroke(boardPixelSize/100));
        g.drawRect(x - 1, y - 1, boardPixelSize + 1, boardPixelSize + 1);

        for (int row = 0; row < boardSize; row++) {
            for (int col = 0; col < boardSize; col++) {
                Piece p = plateau.getPiece(row, col);
                // Set up les couleurs de fond des cases
                Color white = null, black = null;
                int vfonce = 0x66D7D1, vclaire = 0x8DE2DE;
                switch (plateau.getType()) {
                    case PAST -> {
                        if (casesPasse.contains(new Point(row, col))) {
                            white = new Color(vclaire);
                            black = new Color(vclaire);
                            break;
                        }
                        white = new Color(230, 220, 200);
                        black = new Color(120, 100, 90);
                    }
                    case PRESENT -> {
                        if (casesPresent.contains(new Point(row, col))) {
                            white = new Color(vclaire);
                            black = new Color(vclaire);
                            break;
                        }
                        white = new Color(232, 222, 196);
                        black = new Color(115, 95, 100);
                    }
                    case FUTURE -> {
                        if (casesFutur.contains(new Point(row, col))) {
                            // white = new Color(100, 255, 90);
                            // black = new Color(60, 240, 50);
                            white = new Color(vclaire);
                            black = new Color(vclaire);
                            break;
                        }
                        white = new Color(232, 216, 202);
                        black = new Color(130, 95, 85);
                    }
                }

                if (row % 2 == 0 && col % 2 == 0 || row % 2 == 1 && col % 2 == 1) {
                    g.setColor(white); // Couleur blanche pour les cases paires
                } else {
                    g.setColor(black); // Couleur noire pour les cases impaires
                }

                // Sur
                if (p != null && p == jeu.getPieceCourante()) {
                    // Dessiner une couleur semi-transparente pour indiquer la sélection
                    Color highlight = new Color(0xE9B44C);
                    //Color highlight = new Color(255, 215, 0); // Jaune doré, alpha 60/255 (plus transparent)
                    g.setColor(highlight);
                    g.fillRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);
                }

                // Dessiner la case
                g.fillRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);

                g.setStroke(new BasicStroke(1f)); // Réinitialiser l'épaisseur des lignes

                // Affichage feedforward CLONE et JUMP
                int imageHeight = tileWidth; // Taille de l'image du personnage
                int imageWidth = imageHeight * zarekAnimation[0][0].getWidth() / zarekAnimation[0][0].getHeight();

                int pieceX = x + col * tileWidth + (tileWidth - imageWidth) / 2;
                int pieceY = y + row * tileWidth + (tileWidth - imageHeight) / 2;

                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));

                //System.out.println("activeur : " + activeur);
                //n++;
                switch (plateau.getType()) {
                    case PAST -> {
                        Point point = new Point(row, col);
                        if (casesPasse.contains(point) && isMyTurn()) {
                            //System.out.println("SALUT");
                            /*if (plateauMouse != null)
                                System.out.println("plateauMouse : " + plateauMouse.getType());
                            if (caseMouseX >= 0 && caseMouseY >= 0) {
                                System.out.println("caseMouseX : " + caseMouseX);
                                System.out.println("caseMouseY : " + caseMouseY);
                                System.out.println(n);
                            }*/
                            if (plateauMouse != null && plateauMouse.getType() == plateau.getType()
                                    && caseMouseX >= 0 && caseMouseY >= 0 && col == caseMouseX && row == caseMouseY) {
                                if (gameClient.getMyPlayerId() == 1) {
                                    g.drawImage(lemielAnimation[0][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    g.drawImage(lemielAnimation[1][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    activeur = Plateau.TypePlateau.PAST;
                                    caseActivatrice = point;
                                    //System.out.println("TRANSPARENT PASSE !!!");
                                    //System.out.println(n);
                                    transparent = true;
                                    if (selectedPlateauType == Plateau.TypePlateau.PRESENT) {
                                        clone = true;
                                        //System.out.println("CLONE !!!");
                                    }
                                } else if (gameClient.getMyPlayerId() == 2) {
                                    g.drawImage(zarekAnimation[0][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    g.drawImage(zarekAnimation[1][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    activeur = Plateau.TypePlateau.PAST;
                                    caseActivatrice = point;
                                    transparent = true;
                                    if (selectedPlateauType == Plateau.TypePlateau.PRESENT) {
                                        clone = true;
                                    }
                                }
                            } else if (activeur == Plateau.TypePlateau.PAST && caseActivatrice.equals(point) && transparent == true) {
                                transparent = false;
                                clone = false;
                                //System.out.println(" PLUS TRANSPARENT PASSE !!!");
                                System.out.println(n);
                                //System.out.println("PLUS CLONE !!!");
                            }
                        }
                    }
                    case PRESENT -> {
                        Point point = new Point(row, col);
                        if (casesPresent.contains(point) && isMyTurn()) {
                            //System.out.println("SALUT");
                            if (plateauMouse != null && plateauMouse.getType() == plateau.getType()
                                    && caseMouseX >= 0 && caseMouseY >= 0 && col == caseMouseX && row == caseMouseY) {
                                if (gameClient.getMyPlayerId() == 1) {
                                    g.drawImage(lemielAnimation[0][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    g.drawImage(lemielAnimation[1][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    transparent = true;
                                    caseActivatrice = point;
                                    // System.out.println("TRANSPARENT PASSE !!!");
                                    if (selectedPlateauType == Plateau.TypePlateau.FUTURE) {
                                        clone = true;
                                        // System.out.println("CLONE !!!");
                                    }
                                    //System.out.println("TRANSPARENT PRESENT !!!");
                                    activeur = Plateau.TypePlateau.PRESENT;
                                } else if (gameClient.getMyPlayerId() == 2) {
                                    g.drawImage(zarekAnimation[0][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    g.drawImage(zarekAnimation[1][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    transparent = true;

                                    caseActivatrice = point;
                                    if (selectedPlateauType == Plateau.TypePlateau.FUTURE) {
                                        clone = true;
                                    }
                                    activeur = Plateau.TypePlateau.PRESENT;
                                }
                            } else if (activeur == Plateau.TypePlateau.PRESENT && caseActivatrice.equals(point) && transparent == true) {
                                transparent = false;
                                clone = false;
                                // System.out.println("PLUS CLONE !!!");
                                //System.out.println(" PLUS TRANSPARENT PRESENT !!!");
                            }
                        }

                    }
                    case FUTURE -> {
                        Point point = new Point(row, col);
                        if (casesFutur.contains(new Point(row, col)) && isMyTurn()) {
                            if (plateauMouse != null && plateauMouse.getType() == plateau.getType()
                                    && caseMouseX >= 0 && caseMouseY >= 0 && col == caseMouseX && row == caseMouseY) {
                                if (gameClient.getMyPlayerId() == 1) {
                                    g.drawImage(lemielAnimation[0][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    g.drawImage(lemielAnimation[1][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    transparent = true;
                                    activeur = Plateau.TypePlateau.FUTURE;
                                    caseActivatrice = point;
                                } else if (gameClient.getMyPlayerId() == 2) {
                                    g.drawImage(zarekAnimation[0][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    g.drawImage(zarekAnimation[1][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    transparent = true;
                                    activeur = Plateau.TypePlateau.FUTURE;
                                    caseActivatrice = point;
                                }
                            } else if (activeur == Plateau.TypePlateau.FUTURE && caseActivatrice.equals(point) && transparent == true) {
                                transparent = false;
                            }
                        }
                    }
                }

                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            }
        }

        for (int row = 0; row < boardSize; row++) {
            for (int col = 0; col < boardSize; col++) {
                Piece piece = plateau.getPiece(row, col);
                if (piece != null && piece.getOwner() != null) {
                    int imageHeight = tileWidth; // Taille de l'image du personnage
                    int imageWidth = imageHeight * zarekAnimation[0][0].getWidth() / zarekAnimation[0][0].getHeight(); // Ratio de l'image

                    // Calculer les coordonnées pour centrer l'image dans la case
                    int pieceX = x + col * tileWidth + (tileWidth - imageWidth) / 2;
                    int pieceY = y + row * tileWidth + (tileWidth - imageHeight) / 2;

                    switch (piece.getOwner().getId()) {
                        case 1 -> {
                            // Joueur 1 (Lemiel)
                            if (gameClient.getMyPlayerId() == 1 && isMyTurn() && joueur1SelectedPlateau == plateau.getType() && plateauMouse != null
                                    && joueur1SelectedPlateau == plateauMouse.getType() && caseMouseX >= 0 && caseMouseY >= 0 && col == caseMouseX && row == caseMouseY
                                    && etapeCoup == 0) {
                                g.drawImage(lemielAnimation[0][frame], (int) (pieceX - ((imageWidth * 9 / 8) - imageWidth) / 2), (int) (pieceY - ((imageHeight * 9 / 8) - imageHeight) / 2), (int) imageWidth * 9 / 8, (int) imageHeight * 9 / 8, null);
                                g.drawImage(lemielAnimation[1][frame], (int) (pieceX - ((imageWidth * 9 / 8) - imageWidth) / 2), (int) (pieceY - ((imageHeight * 9 / 8) - imageHeight) / 2), imageWidth * 9 / 8, imageHeight * 9 / 8, null);
                            } else {
                                /*System.out.println(selectedPlateauType);
                                System.out.println(selectedPiecePosition);
                                System.out.println(transparent);*/
                                if (selectedPlateauType != null && selectedPiecePosition != null && selectedPlateauType == plateau.getType()
                                        && selectedPiecePosition.getX() == row && selectedPiecePosition.getY() == col && transparent && clone == false) {
                                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
                                    g.drawImage(lemielAnimation[0][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    g.drawImage(lemielAnimation[1][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                                } else {
                                    g.drawImage(lemielAnimation[0][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    g.drawImage(lemielAnimation[1][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                }
                            }
                        }
                        case 2 -> {
                            // Joueur 2 (Zarek)
                            if (gameClient.getMyPlayerId() == 2 && isMyTurn() && joueur2SelectedPlateau == plateau.getType() && plateauMouse != null
                                    && joueur2SelectedPlateau == plateauMouse.getType() && caseMouseX >= 0 && caseMouseY >= 0 && col == caseMouseX && row == caseMouseY
                                    && etapeCoup == 0) {
                                g.drawImage(zarekAnimation[0][frame], (int) (pieceX - ((imageWidth * 9 / 8) - imageWidth) / 2), (int) (pieceY - ((imageHeight * 9 / 8) - imageHeight) / 2), imageWidth * 9 / 8, imageHeight * 9 / 8, null);
                                g.drawImage(zarekAnimation[1][frame], (int) (pieceX - ((imageWidth * 9 / 8) - imageWidth) / 2), (int) (pieceY - ((imageHeight * 9 / 8) - imageHeight) / 2), imageWidth * 9 / 8, imageHeight * 9 / 8, null);
                            } else {
                                if (selectedPlateauType != null && selectedPiecePosition != null && selectedPlateauType == plateau.getType()
                                        && selectedPiecePosition.getX() == row && selectedPiecePosition.getY() == col && transparent && clone == false) {
                                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
                                    g.drawImage(zarekAnimation[0][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    g.drawImage(zarekAnimation[1][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                                } else {
                                    g.drawImage(zarekAnimation[0][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                    g.drawImage(zarekAnimation[1][frame], pieceX, pieceY, imageWidth, imageHeight, null);
                                }
                            }
                        }
                        default -> {
                            g.setColor(Color.GRAY); // Couleur par défaut si l'ID n'est pas reconnu
                        }
                    }
                }
            }
        }
    }

    private void setupMouseListeners() {
        clearMouseListeners(); // Supprimer les anciens écouteurs avant d'en ajouter de nouveaux

        mouseAdapterInternal = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isLoading || gameHasEnded || gameClient == null || !gameClient.isConnected()) {
                    return;
                }

                Point mousePoint = e.getPoint();

                if (backButton.contains(mousePoint)) {
                    backButton.onClick();
                    return;
                }

                if (undoButton.contains(mousePoint)) {
                    undoButton.onClick();
                    return;
                }

                if (redoButton.contains(mousePoint)) {
                    redoButton.onClick();
                    return;
                }

                if (switchToAiButton.contains(mousePoint)) {
                    switchToAiButton.onClick();
                    return;
                }

                if(saveButton.contains(mousePoint)){
                    saveButton.onClick();
                    return;
                }
                handleBoardClick(mousePoint); // Gérer le clic sur le plateau de jeu
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isLoading || gameHasEnded) {
                    return;
                }
                Point mousePoint = e.getPoint();

                boolean needsRepaint = false;

                if (backButton.contains(mousePoint)) {
                    backButton.setClicked(true);
                    needsRepaint = true;
                }

                if (switchToAiButton.contains(mousePoint)) {
                    switchToAiButton.setClicked(true);
                    needsRepaint = true;
                }

                if(saveButton.contains(mousePoint)){
                    saveButton.setClicked(true);
                    needsRepaint = true;
                }

                if (undoButton.contains(mousePoint)) {
                        undoButton.setClicked(true);
                        needsRepaint = true;
                    }

                if (redoButton.contains(mousePoint)) {
                    redoButton.setClicked(true);
                    needsRepaint = true;
                }

                // Ajouter la gestion des nouveaux boutons
                if (isMyTurn()) {
                    // Les trois boutons de sélection de plateau
                    if (etapeCoup == 3) {

                        Plateau p;
                        p = getPlateauFromMousePoint(mousePoint);
                        if (p != null && p.getType() != activePlateau) {
                            if (p.getType() == Plateau.TypePlateau.PAST) {
                                handleChoosePastAction();
                            } else if (p.getType() == Plateau.TypePlateau.PRESENT) {
                                handleChoosePresentAction();
                            } else {
                                handleChooseFutureAction();
                            }
                        }
                    }
                }

                if (needsRepaint) {
                    repaintPanel();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isLoading || gameHasEnded) {
                    return;
                }

                // Quand la souris est relâchée, réinitialiser simplement tous les boutons
                backButton.setClicked(false);
                undoButton.setClicked(false);
                redoButton.setClicked(false);
                switchToAiButton.setClicked(false);
                saveButton.setClicked(false);
                // choosePlateauButton.setClicked(false);
                repaintPanel();
            }
        };

        mouseAdapterFeedForward = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mousePoint = e.getPoint();
            }
        };

        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().addMouseMotionListener(mouseAdapterFeedForward);
        }
    }

    private void clearMouseListeners() {
        if (sceneManager.getPanel() != null) {
            if (mouseAdapterInternal != null) {
                sceneManager.getPanel().removeMouseListener(mouseAdapterInternal);
            }
            if (mouseAdapterFeedForward != null) {
                sceneManager.getPanel().removeMouseListener(mouseAdapterFeedForward);
            }
        }
    }

    private Plateau getPlateauFromMousePoint(Point mousePoint) {
        if (jeu == null || sceneManager == null || sceneManager.getPanel() == null) {
            return null;
        }

        int width = sceneManager.getPanel().getWidth();
        int height = sceneManager.getPanel().getHeight();
        int dynamicButtonY = height - 70; // Synchronisé avec render
        if (dynamicButtonY < 450) {
            dynamicButtonY = 450;
        }

        // Calculer les centres
        int centerX = width / 2;
        int centerY = height / 2;

        // Définir les tailles de plateau (dynamique en fonction de la taille du
        // panneau)
        int choosePlateauWidth = width * 28 / 100; // 28% de la largeur de l'écran
        int choosePlateauHeight = choosePlateauWidth; // 32% de l'hauteur de l'écran

        // Spacing entre les plateaux
        int spacing = width / 20; // Espace entre les plateaux, 10% de la largeur du panneau

        // Définir les tailles de plateau (dynamique en fonction de la taille du
        // panneau)
        int boardSize = jeu.getTAILLE();
        int tileWidth = choosePlateauWidth / boardSize; // Largeur d'une case
        int tileHeight = tileWidth * 1; // Hauteur d'une case (carrée pour l'instant)
        int deltaX = 0; // Décalage horizontal pour centrer les plateaux -> Pour ce moment pas
        // d'isométrie

        // Definir la position de départ de chaque plateau
        int offsetY = centerY - choosePlateauHeight / 2 + tileHeight / 2; // Centrer verticalement
        int presentStartX = centerX - choosePlateauWidth / 2 + deltaX; // Centrer horizontalement
        int pastStartX = presentStartX - choosePlateauWidth - spacing; // Plateau passé à gauche
        int futureStartX = presentStartX + choosePlateauWidth + spacing; // Plateau futur à droite

        // Vérifier si le clic est dans la zone des plateaux
        if (mousePoint.y >= offsetY && mousePoint.y < offsetY + choosePlateauHeight) {
            if (mousePoint.x >= pastStartX && mousePoint.x < pastStartX + choosePlateauWidth) {
                return jeu.getPast();
            }
            if (mousePoint.x >= presentStartX && mousePoint.x < presentStartX + choosePlateauHeight) {
                return jeu.getPresent();
            }
            if (mousePoint.x >= futureStartX && mousePoint.x < futureStartX + choosePlateauHeight) {
                return jeu.getFuture();
            }
        }
        return null;
    }

    private Point getBoardCoordinates(Point mousePoint) {
        Plateau clickedPlateau = getPlateauFromMousePoint(mousePoint);
        if (clickedPlateau == null) {
            return null;
        }

        if (jeu == null || sceneManager == null || sceneManager.getPanel() == null) {
            return null;
        }

        // Obtenir la taille du panneau
        int width = sceneManager.getPanel().getWidth();
        int height = sceneManager.getPanel().getHeight();
        int dynamicButtonY = height - 70; // Synchronisé avec render
        if (dynamicButtonY < 450) {
            dynamicButtonY = 450;
        }

        // Calculer les centres
        int centerX = width / 2;
        int centerY = height / 2;

        // Définir les tailles de plateau (dynamique en fonction de la taille du
        // panneau)
        int choosePlateauWidth = width * 28 / 100; // 28% de la largeur de l'écran
        int choosePlateauHeight = choosePlateauWidth; // 32% de l'hauteur de l'écran

        // Spacing entre les plateaux
        int spacing = width / 20; // Espace entre les plateaux, 10% de la largeur du panneau

        // Définir les tailles de plateau (dynamique en fonction de la taille du
        // panneau)
        int boardSize = jeu.getTAILLE();
        int tileWidth = choosePlateauWidth / boardSize; // Largeur d'une case
        int tileHeight = tileWidth * 1; // Hauteur d'une case (carrée pour l'instant)
        int deltaX = 0; // Décalage horizontal pour centrer les plateaux -> Pour ce moment pas
        // d'isométrie

        // Definir la position de départ de chaque plateau
        int offsetY = centerY - choosePlateauHeight / 2 + tileHeight / 2; // Centrer verticalement
        int presentStartX = centerX - choosePlateauWidth / 2 + deltaX; // Centrer horizontalement
        int pastStartX = presentStartX - choosePlateauWidth - spacing; // Plateau passé à gauche
        int futureStartX = presentStartX + choosePlateauWidth + spacing; // Plateau futur à droite

        // Déterminer le point de départ en fonction du type de plateau
        int boardXStart;
        switch (clickedPlateau.getType()) {
            case PAST:
                boardXStart = pastStartX;
                break;
            case PRESENT:
                boardXStart = presentStartX;
                break;
            case FUTURE:
                boardXStart = futureStartX;
                break;
            default:
                return null;
        }

        // Calculer la position de la case en fonction du clic de la souris
        int col = (mousePoint.x - boardXStart) / tileWidth;
        int row = (mousePoint.y - offsetY) / tileWidth;

        // Vérifier si les coordonnées sont valides
        if (row >= 0 && row < boardSize && col >= 0 && col < boardSize) {
            return new Point(row, col);
        }
        return null;
    }

    private void repaintPanel() {
        if (sceneManager != null && sceneManager.getPanel() != null) {
            sceneManager.getPanel().repaint();
        }
    }

    private void resetSelection() {
        selectedPiecePosition = null;
        selectedPlateauType = null;
        nextActionType = null;
    }

    private void resetSelectionAfterAction() {
        resetSelection();
        // statusMessage est généralement mis à jour par la logique appelant cette
        // fonction ou par onGameStateUpdate
    }

    private void updateStatusFromCurrentGame(boolean fromServerUpdate) {
        if (this.jeu != null && this.jeu.getJoueurCourant() != null && gameClient != null) {
            if (this.jeu.getJoueurCourant().getId() == gameClient.getMyPlayerId()) {
                String playerName = this.jeu.getJoueurCourant().getId() == 1 ? "Lemiel" : "Zarek";

                // Mettre à jour le message d'état en fonction de la valeur de etapeCoup
                if (etapeCoup == 3) {
                    // Lorsque etapeCoup=3, afficher l'invite de sélection du plateau
                    this.statusMessage = "Sélectionnez un plateau pour le prochain tour (PASSÉ, PRÉSENT ou FUTUR)";
                } else if (etapeCoup == 0) {
                    this.statusMessage = "C'est VOTRE tour (" + playerName + "). Sélectionnez un pion pour commencer.";
                } else if (etapeCoup == 1) {
                    this.statusMessage = "Premier mouvement: déplacez votre pion ou effectuez une action spéciale.";
                } else if (etapeCoup == 2) {
                    this.statusMessage = "Second mouvement: déplacez à nouveau votre pion ou effectuez une action spéciale.";
                } else {
                    this.statusMessage = "C'est VOTRE tour (" + playerName + " - Joueur " + gameClient.getMyPlayerId()
                            + ")";
                }
            } else {
                String opponentName = this.jeu.getJoueurCourant().getId() == 1 ? "Lemiel" : "Zarek";
                this.statusMessage = "Tour de l'adversaire : " + opponentName + " (ID "
                        + this.jeu.getJoueurCourant().getId() + ")";
            }
        } else if (isLoading) {
            // Si en cours de chargement, conserver le statusMessage du SwingWorker
        } else if (gameClient != null && gameClient.isConnected()) {
            this.statusMessage = "En attente de l'état du jeu depuis le serveur...";
        } else {
            this.statusMessage = "Non connecté ou jeu non initialisé.";
        }
    }

    // --- Implémentation de GameStateUpdateListener ---
    @Override
    public void onGameStateUpdate(Jeu newGameState) {
        System.out.println("GameScene : Mise à jour de l'état du jeu reçue");

        if (this.jeu == null && newGameState != null) {
            System.out.println("GameScene : Premier état du jeu reçu");
        }

        this.jeu = newGameState;
        isLoading = false;

        // Extraire etapeCoup de newGameState
        if (newGameState != null) {
            int newEtapeCoup = newGameState.getEtapeCoup();
            System.out.println(
                    "GameScene : etapeCoup from server = " + newEtapeCoup + ", current etapeCoup = " + this.etapeCoup);

            // Mettre à jour seulement si la nouvelle valeur de etapeCoup est différente de
            // la valeur actuelle
            if (this.etapeCoup != newEtapeCoup) {
                this.etapeCoup = newEtapeCoup;
                System.out.println("GameScene : Mise à jour de etapeCoup: " + this.etapeCoup);
            }

            // Mettre à jour les informations du plateau sélectionné par le joueur
            if (newGameState.getJoueur1() != null && newGameState.getJoueur1().getProchainPlateau() != null) {
                this.joueur1SelectedPlateau = newGameState.getJoueur1().getProchainPlateau();
                System.out.println("GameScene : Joueur 1 a sélectionné le plateau: " + this.joueur1SelectedPlateau);
            }

            if (newGameState.getJoueur2() != null && newGameState.getJoueur2().getProchainPlateau() != null) {
                this.joueur2SelectedPlateau = newGameState.getJoueur2().getProchainPlateau();
                System.out.println("GameScene : Joueur 2 a sélectionné le plateau: " + this.joueur2SelectedPlateau);
            }

            // Définir activePlateau en fonction du joueur actuel
            if (newGameState.getJoueurCourant() != null) {
                if (newGameState.getJoueurCourant().getId() == 1) {
                    this.activePlateau = this.joueur1SelectedPlateau;
                } else {
                    this.activePlateau = this.joueur2SelectedPlateau;
                }
                System.out.println("GameScene : Plateau actif mis à jour: " + this.activePlateau);
            }

            // Si l'activePlateau requis est défini dans l'état du jeu, l'utiliser
            if (newGameState.getPlateauCourant() != null) {
                this.activePlateau = newGameState.getPlateauCourant().getType();
                System.out.println("GameScene : Plateau actif défini à partir du jeu: " + this.activePlateau);
            }
        }

        if (newGameState != null && newGameState.getJoueurCourant() != null) {
            System.out.println("GameScene : Joueur actuel - ID : " + newGameState.getJoueurCourant().getId());
        }

        updateStatusFromCurrentGame(true);
        gameHasEnded = false;

        // Modification clé : conserver les informations de la pièce sélectionnée
        // lorsque etapeCoup est 1 ou 2
        if (this.etapeCoup != 1 && this.etapeCoup != 2) {
            // Réinitialiser la sélection uniquement en dehors des phases de mouvement
            resetSelectionAfterAction();
        }

        // Assurer la mise à jour de l'interface
        SwingUtilities.invokeLater(this::repaintPanel);
    }

    @Override
    public void onGameMessage(String messageType, String messageContent) {
        // Gérer les messages reçus de GameClient, déjà sur le thread EDT
        isLoading = false; // La réception d'un message du serveur indique que le chargement initial est terminé

        System.out.println("GameScene: Message reçu: " + messageType + " -> " + messageContent);

        switch (messageType) {
            case "SERVER_SHUTDOWN":
                // Le serveur est en train de se fermer
                statusMessage = "Le serveur a été fermé: " + messageContent;

                // En mode solo, ignorer les messages SERVER_SHUTDOWN lors de la transition vers ResultScene
                // car ils sont envoyés pendant le nettoyage normal des ressources
                if (isOperatingInSinglePlayerMode && gameHasEnded) {
                    System.out.println("GameScene: Ignorant le message SERVER_SHUTDOWN en mode solo pendant la transition");
                    break;
                }

                // Afficher un message et retourner au menu principal
                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                        "Le serveur a été fermé: " + messageContent,
                        "Serveur Fermé", JOptionPane.INFORMATION_MESSAGE);

                // Marquer que le jeu est terminé pour éviter d'autres actions
                gameHasEnded = true;

                // Retourner au menu principal
                cleanUpAndGoToMenu();
                break;

            case "PIECE":
                // Gérer le message de succès de la sélection de pièce
                casesPasse.clear();
                casesPresent.clear();
                casesFutur.clear();
                // Format : x:y;mouvementsPossibles
                // Format de mouvementsPossibles : TYPE_COUP:x:y;TYPE_COUP:x:y;...
                String[] parts = messageContent.split(";", 2); // Diviser en 2 parties au maximum : coordonnées et
                // mouvements possibles

                if (parts.length > 0) {
                    try {
                        // Analyser la partie coordonnées
                        String[] coords = parts[0].split(":");
                        if (coords.length < 2) {
                            statusMessage = "Erreur de format dans la réponse du serveur.";
                            break;
                        }

                        int x = Integer.parseInt(coords[0]);
                        int y = Integer.parseInt(coords[1]);

                        // Analyser la partie des mouvements possibles
                        String possibleMovesStr = parts.length > 1 ? parts[1] : "";

                        List<List<String>> result = new ArrayList<>();

                        // Supprimer le dernier point-virgule si présent
                        if (possibleMovesStr.endsWith(";")) {
                            possibleMovesStr = possibleMovesStr.substring(0, possibleMovesStr.length() - 1);
                        }

                        // Séparer les groupes par ";"
                        String[] groupes = possibleMovesStr.split(";");

                        for (String groupe : groupes) {
                            // Séparer les éléments du groupe par ":"
                            String[] elements = groupe.split(":");
                            result.add(Arrays.asList(elements));
                        }

                        for (int i = 0; i < result.size(); i++) {
                            System.out.println("Case possible " + (i + 1) + " : " + result.get(i));

                            // System.err.println(result.get(i).get(0));
                            if (result.get(i).get(0).equals("PAST")) {
                                // System.out.println("Salut");
                                casesPasse.add(new Point(Integer.parseInt(result.get(i).get(1)),
                                        Integer.parseInt(result.get(i).get(2))));
                            } else if (result.get(i).get(0).equals("PRESENT")) {
                                casesPresent.add(new Point(Integer.parseInt(result.get(i).get(1)),
                                        Integer.parseInt(result.get(i).get(2))));
                            } else {
                                casesFutur.add(new Point(Integer.parseInt(result.get(i).get(1)),
                                        Integer.parseInt(result.get(i).get(2))));
                            }
                        }

                        System.out.println(casesPasse.size());

                        for (int i = 0; i < casesPasse.size(); i++) {
                            System.out.println("casesPasse : " + casesPasse.get(i));
                        }

                        System.out.println("GameScene: Pièce sélectionnée à " + x + "," + y
                                + " avec mouvements possibles: " + possibleMovesStr);

                        // La réception du message PIECE indique l'entrée dans etapeCoup=1
                        this.etapeCoup = 1; // Deja a 1

                        // Mettre à jour l'affichage de l'interface utilisateur pour les mouvements
                        // possibles
                        statusMessage = "Pion sélectionné (" + x + "," + y + ") sur plateau " + selectedPlateauType
                                + ". Choisissez une destination sur ce plateau.";

                        // Mettre à jour la position de la pièce sélectionnée
                        selectedPiecePosition = new Point(x, y);

                        // S'assurer que selectedPlateauType n'est pas perdu ici
                        System.out.println("GameScene: Plateau sélectionné sauvegardé: " + selectedPlateauType);

                        // Mettre à jour activePlateau de manière synchronisée pour s'assurer qu'il
                        // reflète le plateau actuellement utilisé
                        this.activePlateau = selectedPlateauType;
                        System.out.println("GameScene: activePlateau mis à jour à : " + this.activePlateau);
                    } catch (NumberFormatException e) {
                        statusMessage = "Erreur de format dans la réponse du serveur.";
                    }
                } else {
                    statusMessage = "Message PIECE vide ou incorrect.";
                }
                break;

            case "DESELECT":
                // Nettoyer les listes de cases possibles
                System.out.println("GameScene: Sélection annulée, nettoyage des cases possibles.");
                casesPasse.clear();
                casesPresent.clear();
                casesFutur.clear();

                // Réinitialiser l'état du jeu
                this.etapeCoup = jeu.getEtapeCoup(); // Réinitialiser l'étape de coup
                System.out.println("GameScene: Réinitialisation de etapeCoup à " + this.etapeCoup);

                // Réinitialiser la sélection
                resetSelection();
                break;

            case "COUP":
                casesPasse.clear();
                casesPresent.clear();
                casesFutur.clear();
                transparent = false;
                clone = false;
                // System.out.println("PLUS CLONE !!!");
                // Gérer le message de succès du mouvement
                // Format : TYPE_COUP:succes:newX:newY:newPlateauType
                String[] coupParts = messageContent.split(":");
                if (coupParts.length >= 3) { // Modifier pour avoir au moins 3 parties, car nous devons ajouter les
                    // nouvelles informations de coordonnées
                    String typeCoup = coupParts[0];

                    // Vérifier si le serveur a retourné la nouvelle position de la pièce et le
                    // nouveau plateau
                    if (coupParts.length >= 5) {
                        try {
                            int newX = Integer.parseInt(coupParts[1]);
                            int newY = Integer.parseInt(coupParts[2]);
                            Plateau.TypePlateau newPlateauType = Plateau.TypePlateau.valueOf(coupParts[3]);

                            System.out.println("GameScene: Mise à jour de la position de la pièce - de "
                                    + (selectedPiecePosition != null
                                            ? selectedPiecePosition.x + "," + selectedPiecePosition.y
                                            : "null")
                                    + " à " + newX + "," + newY
                                    + ", plateau de " + selectedPlateauType + " à " + newPlateauType);

                            // Mettre à jour selectedPiecePosition et selectedPlateauType
                            selectedPiecePosition = new Point(newX, newY);
                            selectedPlateauType = newPlateauType;
                            this.activePlateau = newPlateauType;
                        } catch (Exception e) {
                            System.err.println("GameScene: Échec de l'analyse de la nouvelle position de la pièce: "
                                    + e.getMessage());
                        }
                    }

                    // Vérifier s'il s'agit d'une opération JUMP ou CLONE, mettre à jour
                    // activePlateau
                    if ("JUMP".equals(typeCoup)) {
                        // Calculer le nouvel activePlateau en fonction du selectedPlateauType actuel
                        if (selectedPlateauType == Plateau.TypePlateau.PAST) {
                            this.activePlateau = Plateau.TypePlateau.PRESENT;
                            System.out
                                    .println("GameScene: Opération JUMP, activePlateau mis à jour de PASSÉ à PRÉSENT");
                        } else if (selectedPlateauType == Plateau.TypePlateau.PRESENT) {
                            this.activePlateau = Plateau.TypePlateau.FUTURE;
                            System.out
                                    .println("GameScene: Opération JUMP, activePlateau mis à jour de PRÉSENT à FUTUR");
                        }
                        // Mettre à jour selectedPlateauType pour suivre le plateau actuel
                        selectedPlateauType = this.activePlateau;
                    } else if ("CLONE".equals(typeCoup)) {
                        // Calculer le nouvel activePlateau en fonction du selectedPlateauType actuel
                        if (selectedPlateauType == Plateau.TypePlateau.PRESENT) {
                            this.activePlateau = Plateau.TypePlateau.PAST;
                            System.out
                                    .println("GameScene: Opération CLONE, activePlateau mis à jour de PRÉSENT à PASSÉ");
                        } else if (selectedPlateauType == Plateau.TypePlateau.FUTURE) {
                            this.activePlateau = Plateau.TypePlateau.PRESENT;
                            System.out
                                    .println("GameScene: Opération CLONE, activePlateau mis à jour de FUTUR à PRÉSENT");
                        }
                        // Mettre à jour selectedPlateauType pour suivre le plateau actuel
                        selectedPlateauType = this.activePlateau;
                    }

                    // Réception du message COUP, décider de la prochaine étape en fonction de
                    // l'etapeCoup actuel
                    if (this.etapeCoup == 1) {
                        this.etapeCoup = 2;
                        statusMessage = "Premier déplacement effectué. Choisissez votre seconde action.";
                    } else if (this.etapeCoup == 2) {
                        this.etapeCoup = 3;
                        statusMessage = "Coup " + typeCoup
                                + " effectué avec succès. Sélectionnez le plateau pour le prochain tour.";
                    } else {
                        statusMessage = "Coup " + typeCoup + " effectué avec succès.";
                    }
                }
                break;

            case "PLATEAU":
                // Gérer le message de succès de la sélection du plateau
                // Format : TYPE_PLATEAU:succes
                String[] plateauParts = messageContent.split(":");
                if (plateauParts.length >= 1) {
                    String typePlateau = plateauParts[0];

                    // La réception du message PLATEAU indique la fin du tour
                    this.etapeCoup = 0;

                    statusMessage = "Plateau " + typePlateau + " sélectionné pour le prochain tour.";
                }
                break;

            case "REDOABLE":
                // Gérer le message de succès de l'annulation
                // Format : REDOABLE:succes
                this.etapeCoup = 0; // Réinitialiser l'étape de coup après une annulation réussie
                statusMessage = "Annulation réussie. Vous pouvez maintenant jouer à nouveau.";
                redoable = !redoable; // Inverser l'état de redoable
                break;

            case "WIN":
            case "LOSE":
                gameHasEnded = true;
                String victoryTitle = ("WIN".equals(messageType) || "GAGNE".equals(messageType)) ? "FÉLICITATIONS !" : "DOMMAGE !";

                int winnerid = 0;
                // Modifier le contenu affiché
                String victoryContent = messageContent;
                if (messageContent.contains("1")) {
                    winnerid = 1;
                    victoryContent = messageContent.replace("1", "Lemiel");
                } else if (messageContent.contains("2")) {
                    winnerid = 2;
                    victoryContent = messageContent.replace("2", "Zarek");
                }
                statusMessage = victoryContent;

                // En mode solo, nettoyer les ressources avant de créer ResultScene
                if (isOperatingInSinglePlayerMode) {
                    // Déconnecter l'IA d'abord
                    if (aiClientInstance != null) {
                        System.out.println("GameScene: Déconnexion de l'IA avant transition vers ResultScene");
                        aiClientInstance.disconnect();
                        aiClientInstance = null;
                    }

                    // Déconnecter le gameClient pour éviter les messages de déconnexion ultérieurs
                    if (gameClient != null) {
                        System.out.println("GameScene: Déconnexion du client UI avant arrêt du serveur");
                        gameClient.disconnect();
                    }

                    // Arrêter le serveur local
                    if (localSinglePlayerServerManager != null && localSinglePlayerServerManager.isServerRunning()) {
                        System.out.println("GameScene: Arrêt du serveur local avant transition vers ResultScene");
                        localSinglePlayerServerManager.stopServer();
                    }
                }

                if (gameClient != null) {
                    System.out.println("GameScene: Création d'une nouvelle instance de ResultScene pour le mode " + (isOperatingInSinglePlayerMode ? "solo" : "multijoueur"));
                    ResultScene resultScene = new ResultScene(sceneManager, winnerid, victoryContent,
                            gameClient, hostServerManager, isOperatingInSinglePlayerMode);
                    gameClient = null;
                    hostServerManager = null;
                    sceneManager.setScene(resultScene);
                } else {
                    ResultScene resultScene = new ResultScene(sceneManager, winnerid, victoryContent);
                    sceneManager.setScene(resultScene);
                }
                break;

            case "ADVERSAIRE":
                // Gérer les erreurs du serveur ou les informations de tour
                // Modifier l'ID du joueur dans le message par le nom du personnage
                String modifiedContent = messageContent;
                if (messageContent.contains("Joueur 1")) {
                    modifiedContent = messageContent.replace("Joueur 1", "Lemiel");
                } else if (messageContent.contains("Joueur 2")) {
                    modifiedContent = messageContent.replace("Joueur 2", "Zarek");
                }

                // S'il s'agit d'une réponse à une opération d'annulation, réinitialiser
                // etapeCoup à 0
                if (messageContent.contains("annulation") || messageContent.contains("Undo")) {
                    this.etapeCoup = 0;
                    System.out.println("GameScene: Retour à etapeCoup 0 après Undo");
                }

                statusMessage = modifiedContent;
                break;

            case "GAGNE":
            case "PERDU":

            case "ERROR":
                statusMessage = "Erreur: " + messageContent;
                JOptionPane.showMessageDialog(sceneManager.getPanel(), messageContent, "ERREUR",
                        JOptionPane.ERROR_MESSAGE);
                break;

            case "DISCONNECTED":
                gameHasEnded = true;
                statusMessage = "Déconnecté: " + messageContent;
                JOptionPane.showMessageDialog(sceneManager.getPanel(), messageContent, "DÉCONNECTÉ",
                        JOptionPane.WARNING_MESSAGE);
                cleanUpAndGoToMenu();
                break;

            default:
                statusMessage = messageType + ": " + messageContent;
                break;
        }

        repaintPanel();
    }

    /**
     * Méthode pour retourner au lobby tout en conservant la connexion
     */
    private void returnToLobby() {
        System.out.println("GameScene: Retour au lobby...");
        isLoading = false;

        // Réinitialiser l'état du jeu
        resetSelection();

        // Sauvegarder les références existantes de GameClient et GameServerManager pour les transmettre à la nouvelle scène
        final GameClient clientToTransfer = this.gameClient;
        final GameServerManager serverToTransfer = this.hostServerManager;

        // Empêcher dispose() de déconnecter le client ou d'arrêter le serveur pendant la transition de scène
        this.gameClient = null;
        this.hostServerManager = null;

        // Déterminer si c'est J1 ou J2 pour savoir vers quel LobbyScene retourner
        if (clientToTransfer != null) {
            System.out.println("GameScene: Conservation de la connexion pour retourner au lobby, Player ID: "
                    + (clientToTransfer.isConnected() ? clientToTransfer.getMyPlayerId() : "non connecté"));

            if (clientToTransfer.getMyPlayerId() == 1) {
                // Joueur 1 retourne à HostingScene
                SwingUtilities.invokeLater(() -> {
                    try {
                        System.out.println("GameScene: Création d'une nouvelle HostingScene pour J1");
                        HostingScene hostingScene = new HostingScene(sceneManager);

                        // S'assurer que le serveur n'est pas arrêté
                        if (serverToTransfer != null && serverToTransfer.isServerRunning()) {
                            System.out.println("GameScene: Transfert du serveur à HostingScene");
                            hostingScene.setExistingClient(clientToTransfer, serverToTransfer);
                        } else {
                            System.out.println("GameScene: Avertissement - Serveur null ou non actif, connexion unique du client");
                            // si le serveur n'est pas actif, on ne le passe pas
                            hostingScene.setExistingClient(clientToTransfer, null);
                        }

                        sceneManager.setScene(hostingScene);
                        System.out.println("GameScene: J1 transféré vers HostingScene");
                    } catch (Exception e) {
                        System.err.println("GameScene: Erreur lors du retour au lobby pour J1: " + e.getMessage());
                        e.printStackTrace();
                        // si une erreur survient, on tente de se déconnecter et de retourner au menu principal
                        if (clientToTransfer != null) {
                            clientToTransfer.disconnect();
                        }
                        if (serverToTransfer != null) {
                            serverToTransfer.stopServer();
                        }
                        SwingUtilities.invokeLater(() -> sceneManager.setScene(new MenuScene(sceneManager)));
                    }
                });
            } else {
                // Joueur 2 retourne à ClientLobbyScene
                SwingUtilities.invokeLater(() -> {
                    try {
                        System.out.println("GameScene: Création d'une nouvelle ClientLobbyScene pour J2");
                        ClientLobbyScene lobbyScene = new ClientLobbyScene(sceneManager, clientToTransfer);
                        sceneManager.setScene(lobbyScene);
                        System.out.println("GameScene: J2 transféré vers ClientLobbyScene");
                    } catch (Exception e) {
                        System.err.println("GameScene: Erreur lors du retour au lobby pour J2: " + e.getMessage());
                        e.printStackTrace();
                        // si une erreur survient, on tente de se déconnecter et de retourner au menu principal
                        if (clientToTransfer != null) {
                            clientToTransfer.disconnect();
                        }
                        SwingUtilities.invokeLater(() -> sceneManager.setScene(new MenuScene(sceneManager)));
                    }
                });
            }
        } else {
            // Si aucune connexion n'est active, on retourne au menu principal
            System.out.println("GameScene: Pas de client à transférer, retour au menu principal");
            cleanUpAndGoToMenu();
        }
    }

    @Override
    public void dispose() {
        System.out.println("GameScene: Dispose appelée.");
        isLoading = false;
        clearMouseListeners();

        // Si et seulement si gameClient n'est pas null, alors on peut déconnecter le client
        // Si retourne au lobby, gameClient est null
        if (gameClient != null) {
            // Check si on va au lobby , si oui, on ne déconnecte pas le client si non, on le déconnecte
            boolean goingToLobby = false;
            if (sceneManager.getCurrentScene() != null) {
                String currentSceneName = sceneManager.getCurrentScene().getClass().getSimpleName();
                goingToLobby = "ClientLobbyScene".equals(currentSceneName) || "HostingScene".equals(currentSceneName);
            }

            if (!goingToLobby) {
                System.out.println("GameScene: Déconnexion du client car on ne retourne pas au lobby.");
                gameClient.disconnect();
            } else {
                System.out.println("GameScene: Conservation de la connexion pour le lobby.");
            }
            gameClient = null;
        } else {
            System.out.println("GameScene: Client déjà géré ou null.");
        }

        // Si cette GameScene gère le serveur hôte, l'arrêter seulement si on ne va pas au lobby
        if (hostServerManager != null) {
            boolean goingToLobby = false;
            if (sceneManager.getCurrentScene() != null) {
                String currentSceneName = sceneManager.getCurrentScene().getClass().getSimpleName();
                goingToLobby = "HostingScene".equals(currentSceneName);
            }

            if (!goingToLobby) {
                System.out.println("GameScene: Arrêt du serveur hôte géré par GameScene.");
                hostServerManager.stopServer();
            } else {
                System.out.println("GameScene: Conservation du serveur pour le lobby.");
            }
            hostServerManager = null;
        } else {
            System.out.println("GameScene: Serveur déjà géré ou null.");
        }

        if (isOperatingInSinglePlayerMode) { // Nettoyage du serveur et IA en mode solo
            if (aiClientInstance != null) {
                aiClientInstance.disconnect();
                aiClientInstance = null;
            }
            if (localAIClientThread != null && localAIClientThread.isAlive()) {
                localAIClientThread.interrupt();
                localAIClientThread = null;
            }
            if (localSinglePlayerServerManager != null && localSinglePlayerServerManager.isServerRunning()) {
                localSinglePlayerServerManager.stopServer();
            }
            System.out.println("GameScene (Solo): Ressources serveur/AI nettoyées.");
        }
    }

    // Ajouter une méthode pour gérer la sélection du plateau
    private void handleChoosePlateauAction() {
        if (jeu == null || gameClient == null || !gameClient.isConnected() || gameHasEnded) {
            return;
        }

        if (!isMyTurn()) {
            statusMessage = "Ce n'est pas votre tour.";
            repaintPanel();
            return;
        }

        // Valide uniquement lorsque etapeCoup est égal à 3
        if (etapeCoup == 3) {
            // Obtenir le plateau actuellement sélectionné
            Plateau.TypePlateau plateauToSelect = null;

            // S'il n'y a pas de sélection spécifique, utiliser le plateau par défaut
            // (PASSÉ)
            if (selectedPlateauType == null) {
                plateauToSelect = Plateau.TypePlateau.PAST;
                System.out.println("GameScene: Pas de plateau sélectionné, utilisation de PASSÉ par défaut");
            } else {
                plateauToSelect = selectedPlateauType;
                System.out.println("GameScene: Plateau sélectionné: " + plateauToSelect);
            }

            // Envoyer la commande de sélection du plateau
            String command = "0:" + plateauToSelect.name() + ":"
                    + (selectedPlateauType != null ? selectedPlateauType.name() : "PAST") + ":0:0";
            System.out.println("GameScene: Envoi de la commande: " + command);
            gameClient.sendPlayerAction(command);
            statusMessage = "Sélection du plateau " + plateauToSelect + " pour le prochain tour...";
            repaintPanel();
        } else {
            System.out.println("GameScene: Impossible de sélectionner un plateau à l'étape: " + etapeCoup);
            statusMessage = "Vous ne pouvez pas sélectionner un plateau maintenant (étape: " + etapeCoup + ")";
            repaintPanel();
        }
    }

    // Ajouter trois méthodes de sélection de plateau
    private void handleChoosePastAction() {
        if (etapeCoup == 3 && isMyTurn()) {
            selectedPlateauType = Plateau.TypePlateau.PAST;
            String command = "0:" + selectedPlateauType.name() + ":" + selectedPlateauType.name() + ":0:0";
            gameClient.sendPlayerAction(command);
            statusMessage = "Plateau PASSÉ sélectionné pour le prochain tour.";
            System.out.println("GameScene: Sélection du plateau PASSÉ pour le prochain tour");
        }
    }

    private void handleChoosePresentAction() {
        if (etapeCoup == 3 && isMyTurn()) {
            selectedPlateauType = Plateau.TypePlateau.PRESENT;
            String command = "0:" + selectedPlateauType.name() + ":" + selectedPlateauType.name() + ":0:0";
            gameClient.sendPlayerAction(command);
            statusMessage = "Plateau PRÉSENT sélectionné pour le prochain tour.";
            System.out.println("GameScene: Sélection du plateau PRÉSENT pour le prochain tour");
        }
    }

    private void handleChooseFutureAction() {
        if (etapeCoup == 3 && isMyTurn()) {
            selectedPlateauType = Plateau.TypePlateau.FUTURE;
            String command = "0:" + selectedPlateauType.name() + ":" + selectedPlateauType.name() + ":0:0";
            gameClient.sendPlayerAction(command);
            statusMessage = "Plateau FUTUR sélectionné pour le prochain tour.";
            System.out.println("GameScene: Sélection du plateau FUTUR pour le prochain tour");
        }
    }

    @Override
    public void onPlayerDisconnected() {
        // Si le joueur s'est déconnecté, on affiche un message et on retourne au menu principal
        if (handlingDisconnection) {
            return;
        }
        handlingDisconnection = true;
        SwingUtilities.invokeLater(() -> {
            // Afficher un message de déconnexion
            JOptionPane.showMessageDialog(sceneManager.getPanel(),
                    "Le joueur s'est déconnecté, le jeu est terminé",
                    "DÉCONNECTÉ",
                    JOptionPane.WARNING_MESSAGE);

            // retourner au menu principal
            cleanUpAndGoToMenu();
        });
    }

    private Point getCaseFromMousePoint(Plateau plateau, Point point) {
        int x, y, startX;
        Point p;
        if (plateau.getType() == Plateau.TypePlateau.PAST) {
            startX = pastStartX;
        } else if (plateau.getType() == Plateau.TypePlateau.PRESENT) {
            startX = presentStartX;
        } else {
            startX = futureStartX;
        }

        for (int i = 0; i < boardSize; i++) {
            x = startX + i * tileWidth;
            for (int j = 0; j < boardSize; j++) {
                y = offsetY + j * tileWidth;
                //System.out.println("x :" + x + " y : " + y + " mousePoint : (" + mousePoint.getX() + ", " + mousePoint.getY() + " )");
                if (x < point.getX() && x + tileWidth > point.getX() && y < point.getY() && y + tileWidth > point.getY()) {
                    p = new Point(i, j);
                    return p;
                }
            }
        }
        return null;
    }



    /*private void animationTranslation() {
        timerAnim = new Timer(20, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                repaintPanel();
                x++;
            }
        });
        timerAnim.start();
    }*/

    /**
 * Load game state from save data string
 *
 * @param gameDataString Game data string in format:
 *        etapeCoup:0;JC:1;C1:4;C2:3;P1:PRESENT;P2:PRESENT;P:0000000000000002;PR:1100000000020002;F:1000000000020000;PC:null
 * @return true if loading successful, false if failed
 */
public boolean loadFromSaveData(String gameDataString) {
    try {
        System.out.println("GameScene: Starting to load save data: " + gameDataString);

        if (gameDataString == null || gameDataString.trim().isEmpty()) {
            System.err.println("GameScene: Save data is empty");
            return false;
        }

        // Parse save data
        String[] dataParts = gameDataString.split(";");

        if (dataParts.length < 8) {
            System.err.println("GameScene: Save data format incomplete, requires at least 8 fields");
            return false;
        }

        // Create a map to store parsed data
        java.util.Map<String, String> gameData = new java.util.HashMap<>();

        for (String part : dataParts) {
            String[] keyValue = part.split(":", 2);
            if (keyValue.length == 2) {
                gameData.put(keyValue[0], keyValue[1]);
            }
        }

        // Validate required fields
        String[] requiredFields = {"etapeCoup", "JC", "C1", "C2", "P1", "P2", "P", "PR", "F"};
        for (String field : requiredFields) {
            if (!gameData.containsKey(field)) {
                System.err.println("GameScene: Missing required field: " + field);
                return false;
            }
        }

        // In single player mode, load game state through server
        if (isOperatingInSinglePlayerMode) {
            return loadSinglePlayerGameFromData(gameData);
        } else {
            // In multiplayer mode, send load command to server
            return loadMultiPlayerGameFromData(gameDataString);
        }

    } catch (Exception e) {
        System.err.println("GameScene: Error loading save data: " + e.getMessage());
        e.printStackTrace();
        return false;
    }
}

/**
 * Load game data in single player mode
 */
private boolean loadSinglePlayerGameFromData(java.util.Map<String, String> gameData) {
    try {
        System.out.println("Game Scene : start to reload game");
        // If server and AI are already running, stop them first
        if (localSinglePlayerServerManager != null && localSinglePlayerServerManager.isServerRunning()) {
            System.out.println("GameScene: Stopping existing single player server to load save data");

            // Disconnect AI
            if (aiClientInstance != null) {
                aiClientInstance.disconnect();
                aiClientInstance = null;
            }

            // Disconnect game client
            if (gameClient != null) {
                gameClient.disconnect();
                gameClient = null;
            }

            // Stop server
            localSinglePlayerServerManager.stopServer();
            Thread.sleep(500); // Wait for server to fully stop
        }

        // Restart server
        localSinglePlayerServerManager = new GameServerManager(null);
        localSinglePlayerServerManager.startServer();
        Thread.sleep(300);

        // Reconnect game client
        gameClient = new GameClient("127.0.0.1", this);
        gameClient.connect();

        if (!gameClient.isConnected()) {
            System.err.println("GameScene: Unable to reconnect game client");
            return false;
        }

        // Reconnect AI
        aiClientInstance = new AIClient("127.0.0.1", levelAI);
        if (localSinglePlayerServerManager != null) {
            int serverPort = localSinglePlayerServerManager.getCurrentPort();
            aiClientInstance.setServerPort(serverPort);
        }
        aiClientInstance.connect();

        if (!aiClientInstance.isConnected()) {
            System.err.println("GameScene: Unable to reconnect AI client");
            return false;
        }

        aiClientInstance.startListeningAndPlaying();

        Thread.sleep(500); // Wait for connections to stabilize

        // Send load command to server
        String loadCommand = "LOAD_GAME:" + reconstructGameDataString(gameData);
        gameClient.sendPlayerAction(loadCommand);

        System.out.println("GameScene: Load command sent to single player server");
        return true;

    } catch (Exception e) {
        System.err.println("GameScene: Single player mode loading failed: " + e.getMessage());
        e.printStackTrace();
        return false;
    }
}

/**
 * Load game data in multiplayer mode
 */
private boolean loadMultiPlayerGameFromData(String gameDataString) {
    try {
        if (gameClient == null || !gameClient.isConnected()) {
            System.err.println("GameScene: Client not connected in multiplayer mode");
            return false;
        }

        // Only host (player 1) can load games
        if (gameClient.getMyPlayerId() != 1) {
            System.err.println("GameScene: Only host can load games");
            return false;
        }

        // Send load command to server
        String loadCommand = "LOAD_GAME:" + gameDataString;
        gameClient.sendPlayerAction(loadCommand);

        System.out.println("GameScene: Load command sent to multiplayer server");
        return true;

    } catch (Exception e) {
        System.err.println("GameScene: Multiplayer mode loading failed: " + e.getMessage());
        e.printStackTrace();
        return false;
    }
}

/**
 * Reconstruct game data string
 */
private String reconstructGameDataString(java.util.Map<String, String> gameData) {
    StringBuilder sb = new StringBuilder();

    // Reconstruct string in original format
    String[] orderedKeys = {"etapeCoup", "JC", "C1", "C2", "P1", "P2", "P", "PR", "F", "PC"};

    for (int i = 0; i < orderedKeys.length; i++) {
        if (i > 0) {
            sb.append(";");
        }
        sb.append(orderedKeys[i]).append(":").append(gameData.getOrDefault(orderedKeys[i], "null"));
    }

    return sb.toString();
}

/**
 * Update local game state (called after receiving server response)
 */
public void updateLocalGameStateFromSave(java.util.Map<String, String> gameData) {
    try {
        // Update etapeCoup
        if (gameData.containsKey("etapeCoup")) {
            this.etapeCoup = Integer.parseInt(gameData.get("etapeCoup"));
            System.out.println("GameScene: Updated etapeCoup to: " + this.etapeCoup);
        }

        // Update player selected plateaus
        if (gameData.containsKey("P1")) {
            try {
                this.joueur1SelectedPlateau = Plateau.TypePlateau.valueOf(gameData.get("P1"));
                System.out.println("GameScene: Player 1 selected plateau: " + this.joueur1SelectedPlateau);
            } catch (IllegalArgumentException e) {
                System.err.println("GameScene: Invalid P1 plateau type: " + gameData.get("P1"));
            }
        }

        if (gameData.containsKey("P2")) {
            try {
                this.joueur2SelectedPlateau = Plateau.TypePlateau.valueOf(gameData.get("P2"));
                System.out.println("GameScene: Player 2 selected plateau: " + this.joueur2SelectedPlateau);
            } catch (IllegalArgumentException e) {
                System.err.println("GameScene: Invalid P2 plateau type: " + gameData.get("P2"));
            }
        }

        // Set active plateau based on current player
        if (jeu != null && jeu.getJoueurCourant() != null) {
            if (jeu.getJoueurCourant().getId() == 1) {
                this.activePlateau = this.joueur1SelectedPlateau;
            } else {
                this.activePlateau = this.joueur2SelectedPlateau;
            }
            System.out.println("GameScene: Active plateau set to: " + this.activePlateau);
        }

        // Clear possible selection states
        resetSelection();

        // Clear movement hints
        casesPasse.clear();
        casesPresent.clear();
        casesFutur.clear();

        // Update status message
        updateStatusFromCurrentGame(false);

        System.out.println("GameScene: Local game state update completed");

    } catch (Exception e) {
        System.err.println("GameScene: Error updating local game state: " + e.getMessage());
        e.printStackTrace();
    }
}

/**
 * Parse board states from save data (for debugging)
 */
private void debugParseBoardStates(java.util.Map<String, String> gameData) {
    System.out.println("=== Debug: Board State Parsing ===");

    String[] boardKeys = {"P", "PR", "F"};
    String[] boardNames = {"Past", "Present", "Future"};

    for (int i = 0; i < boardKeys.length; i++) {
        String boardData = gameData.get(boardKeys[i]);
        if (boardData != null && boardData.length() == 16) {
            System.out.println(boardNames[i] + " board state:");
            for (int row = 0; row < 4; row++) {
                StringBuilder line = new StringBuilder();
                for (int col = 0; col < 4; col++) {
                    char piece = boardData.charAt(row * 4 + col);
                    line.append(piece).append(" ");
                }
                System.out.println("  " + line.toString());
            }
        }
    }

    System.out.println("Current player: " + gameData.get("JC"));
    System.out.println("Player 1 clones: " + gameData.get("C1"));
    System.out.println("Player 2 clones: " + gameData.get("C2"));
    System.out.println("Game phase: " + gameData.get("etapeCoup"));
    System.out.println("================================");
}
}
