package SceneManager;

import Modele.Coup;
import Modele.Jeu;
import Modele.Piece;
import Modele.Plateau;
import Network.AIClient;
import Network.GameClient;
import Network.GameServerManager;
import Network.GameStateUpdateListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class GameScene implements Scene, GameStateUpdateListener {

    private SceneManager sceneManager;
    private Jeu jeu; // Même état de jeu que dans le serveur

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

    // État de sélection de l'interface utilisateur
    private Point selectedPiecePosition = null;
    private Plateau.TypePlateau selectedPlateauType = null;
    private Coup.TypeCoup nextActionType = null; // On attend un clic pour DÉPLACER

    // Boutons UI
    private Button backButton; // Bouton pour revenir au menu principal
    private Button undoButton; // Bouton pour annuler une action
    private Button choosePlateauButton; // Bouton pour choisir un plateau

    // Ajout de trois boutons pour sélectionner un plateau spécifique
    private Button choosePastButton; // Bouton pour sélectionner le plateau du passé
    private Button choosePresentButton; // Bouton pour sélectionner le plateau du présent
    private Button chooseFutureButton; // Bouton pour sélectionner le plateau du futur

    // Réseau et Mode de Jeu
    private GameClient gameClient;
    private String serverIpToConnectOnDemand; // IP pour que le client se connecte (si ce n'est pas l'hôte/solo)
    private boolean isOperatingInSinglePlayerMode; // True si c'est le mode Solo avec auto-hébergement

    private String statusMessage = "Initialisation...";
    private volatile boolean gameHasEnded = false; // volatile car peut être mis à jour depuis un autre thread
                                                   // (onGameMessage)
    private volatile boolean isLoading = false; // Pour afficher l'état de chargement
    private int etapeCoup = 0; // 直接在GameScene中存储etapeCoup值

    Point mousePoint;

    ArrayList<Point> casesPasse = new ArrayList<>();
    ArrayList<Point> casesPresent = new ArrayList<>();
    ArrayList<Point> casesFutur = new ArrayList<>();

    // Serveur et IA locaux pour le mode solo
    // Static pour garantir qu'il n'y a qu'une seule instance si GameScene est
    // recréée rapidement (même si dispose devrait gérer cela)
    private static GameServerManager localSinglePlayerServerManager;
    private static Thread localAIClientThread;
    private static AIClient aiClientInstance; // Conserve l'instance de l'IA pour pouvoir la déconnecter

    private MouseAdapter mouseAdapterInternal;
    // MouseMotionListener est intégré dans MouseAdapter si mouseAdapterInternal
    // hérite de MouseAdapter et implémente MouseMotionListener
    // Ou créer une variable séparée pour MouseMotionListener

    private MouseAdapter mouseAdapterFeedForward;

    // Ajout pour la gestion du serveur en mode multijoueur
    private GameServerManager hostServerManager; // Instance du serveur reprise de HostingScene

    // Stocke les plateaux sélectionnés par les joueurs
    private Plateau.TypePlateau joueur1SelectedPlateau = Plateau.TypePlateau.PAST;
    private Plateau.TypePlateau joueur2SelectedPlateau = Plateau.TypePlateau.PAST;
    private Plateau.TypePlateau activePlateau = null;

    // Constructeur pour le mode Solo (auto-hébergement du serveur et de l'IA)
    public GameScene(SceneManager sceneManager, boolean isSinglePlayer) {
        this.sceneManager = sceneManager;
        this.isOperatingInSinglePlayerMode = isSinglePlayer;
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
            GameServerManager serverManager) {
        this.sceneManager = sceneManager;
        this.isOperatingInSinglePlayerMode = false;
        this.gameClient = alreadyConnectedHostClient; // Utilise le client déjà connecté
        this.hostServerManager = serverManager; // Reprend la gestion du serveur

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
            this.statusMessage = "Erreur fatale: Client hôte non connecté.";
            System.err.println(this.statusMessage);
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                        "Erreur fatale lors de l'initialisation du jeu: Client hôte déconnecté.",
                        "Erreur de Connexion", JOptionPane.ERROR_MESSAGE);
                cleanUpAndGoToMenu();
            });
        } else {
            System.out.println("GameScene: Initialisation avec client hôte (ID: " + this.gameClient.getMyPlayerId()
                    + (this.hostServerManager != null ? ", avec serveur hôte" : ", sans serveur hôte"));

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
        this(sceneManager, alreadyConnectedHostClient, null);
    }

    private void commonUIInit() {
        // La position des boutons sera mise à jour dans render()
        backButton = new Button(0, 0, 150, 40, "Retour Menu", this::handleBackButton);

        // Ajouter un bouton pour annuler une action
        undoButton = new Button(0, 0, 100, 40, "UNDO", this::handleUndoAction);

        // Ajouter un bouton pour choisir un plateau
        choosePlateauButton = new Button(0, 0, 180, 40, "Choisir ce plateau", this::handleChoosePlateauAction);

        // Ajouter trois boutons pour sélectionner un plateau spécifique
        choosePastButton = new Button(0, 0, 100, 40, "PASSÉ", this::handleChoosePastAction);
        choosePresentButton = new Button(0, 0, 100, 40, "PRÉSENT", this::handleChoosePresentAction);
        chooseFutureButton = new Button(0, 0, 100, 40, "FUTUR", this::handleChooseFutureAction);
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
        isLoading = false; // Arrête tous les états de chargement
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
                // Assurez-vous d'arrêter le serveur précédent s'il existe et qu'il appartient à
                // ce mode solo
                if (localSinglePlayerServerManager != null && localSinglePlayerServerManager.isServerRunning()) {
                    System.out.println(
                            "GameScene (Solo Worker): Serveur local solo déjà actif, tentative de réutilisation ou redémarrage.");
                    // Il peut être nécessaire de l'arrêter et de le réinitialiser pour garantir un
                    // état propre
                    localSinglePlayerServerManager.stopServer();
                }
                localSinglePlayerServerManager = new GameServerManager(null); // null pour le callback
                localSinglePlayerServerManager.startServer(); // Le serveur fonctionne sur 127.0.0.1
                publish("Serveur solo démarré. Connexion du joueur UI...");

                gameClient = new GameClient("127.0.0.1", GameScene.this);
                gameClient.connect(); // Le joueur UI se connecte
                publish("Joueur UI connecté (ID: " + gameClient.getMyPlayerId() + "). Démarrage de l'IA...");

                // Arrêter l'ancienne IA si elle existe
                if (localAIClientThread != null && localAIClientThread.isAlive()) {
                    if (aiClientInstance != null) {
                        aiClientInstance.disconnect();
                    }
                    localAIClientThread.interrupt();
                }

                aiClientInstance = new AIClient("127.0.0.1");
                aiClientInstance.connect(); // L'IA se connecte
                if (aiClientInstance.isConnected()) {
                    aiClientInstance.startListeningAndPlaying(); // Démarrer le thread de l'IA
                    publish("IA (ID: " + aiClientInstance.getMyPlayerId() + ") connectée et à l'écoute.");
                } else {
                    publish("Erreur: L'IA n'a pas pu se connecter.");
                    throw new IOException("Échec de la connexion du client IA.");
                }

                // GameServerManager appellera automatiquement startGameEngine lorsqu'il y aura
                // 2 clients
                // (Joueur UI et Client IA) si configuré correctement.
                // Nous pouvons attendre un peu pour nous assurer que le serveur a le temps de
                // traiter.
                Thread.sleep(500); // Attendre que le serveur traite la connexion de l'IA
                if (localSinglePlayerServerManager.areAllPlayersConnected()) {
                    // startGameEngine a été appelé automatiquement par GameServerManager
                    publish("Moteur de jeu solo prêt.");
                } else {
                    publish("En attente que le serveur démarre le moteur ("
                            + (localSinglePlayerServerManager.areAllPlayersConnected() ? "OK"
                                    : "Pas encore assez de joueurs")
                            + ")");
                    // On peut ajouter une boucle d'attente ici si nécessaire, mais idéalement
                    // GameServerManager gère cela
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
                        if (gameClient == null || !gameClient.isConnected()) {
                            statusMessage = "Erreur connexion joueur UI en mode solo.";
                        }
                        // statusMessage sera mis à jour par le premier onGameStateUpdate
                    } else {
                        statusMessage = "Échec du démarrage du mode solo.";
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    statusMessage = "Erreur critique mode solo: " + e.getMessage();
                    JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur Solo",
                            JOptionPane.ERROR_MESSAGE);
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

            System.out.println("GameScene: Envoi des informations de clic - Type de plateau : " + clickedPlateauType +
                    ", Position : (" + clickedRow + "," + clickedCol +
                    "), etapeCoup : " + etapeCoup);

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

        if (!isMyTurn()) {
            statusMessage = "Ce n'est pas votre tour.";
            repaintPanel();
            return;
        }

        // Utiliser le champ etapeCoup de GameScene
        if (etapeCoup == 1 || etapeCoup == 2) {
            // L'annulation n'a de sens que lorsque etapeCoup est égal à 1 ou 2
            // Format d'envoi : <Annuler ? 1 : 0>:<ProchainPlateau:
            // null>:<PlateauSélectionné>:<x>:<y>
            String command = "1:null:" + selectedPlateauType.name() + ":" + selectedPiecePosition.x + ":"
                    + selectedPiecePosition.y;
            gameClient.sendPlayerAction(command);
            statusMessage = "Demande d'annulation envoyée...";
            resetSelection();
            repaintPanel();
        }
    }

    @Override
    public void update() {
        // Mettre à jour l'animation de Zarek
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFrameUpdateTime > 250) { // Mettre à jour une image toutes les 250 ms
            frame = (frame + 1) % 8;
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
                // Activer les boutons d'action seulement quand c'est notre tour
                if (myTurn) {
                    // Utiliser le champ etapeCoup de GameScene
                    if (etapeCoup == 1 || etapeCoup == 2) {
                        undoButton.update(mousePos);
                    } else {
                        undoButton.update(new Point(-1, -1));
                    }

                    // Afficher le bouton "Choisir un plateau" lorsque etapeCoup est égal à 3
                    if (etapeCoup == 3) {
                        // System.out.println("Devrait afficher le bouton : Sélectionner le plateau");
                        // System.out.println("Position de la souris : " + mousePos.x + "," +
                        // mousePos.y);

                        // Met à jour les trois boutons de sélection de plateau
                        choosePastButton.update(mousePos);
                        choosePresentButton.update(mousePos);
                        chooseFutureButton.update(mousePos);

                        // Le bouton original peut ne plus être utilisé
                        // choosePlateauButton.update(mousePos);

                        // Vérifie si la zone des boutons est valide
                        // System.out.println("Position du bouton Past : " + choosePastButton.getX() +
                        // "," + choosePastButton.getY()
                        // + " largeur : " + choosePastButton.getWidth() + " hauteur : " +
                        // choosePastButton.getHeight());
                        // System.out.println("Position du bouton Present : " +
                        // choosePresentButton.getX() + "," + choosePresentButton.getY()
                        // + " largeur : " + choosePresentButton.getWidth() + " hauteur : " +
                        // choosePresentButton.getHeight());
                        // System.out.println("Position du bouton Future : " + chooseFutureButton.getX()
                        // + "," + chooseFutureButton.getY()
                        // + " largeur : " + chooseFutureButton.getWidth() + " hauteur : " +
                        // chooseFutureButton.getHeight());
                    } else {
                        choosePlateauButton.update(new Point(-1, -1));
                        choosePastButton.update(new Point(-1, -1));
                        choosePresentButton.update(new Point(-1, -1));
                        chooseFutureButton.update(new Point(-1, -1));
                    }
                } else {
                    // Si ce n'est pas notre tour, s'assurer que les boutons ne sont pas en survol
                    undoButton.update(new Point(-1, -1));
                    choosePlateauButton.update(new Point(-1, -1));
                    choosePastButton.update(new Point(-1, -1));
                    choosePresentButton.update(new Point(-1, -1));
                    chooseFutureButton.update(new Point(-1, -1));
                }

                // Le repaint à chaque mouvement de souris est nécessaire pour l'effet de survol
                repaintPanel();
            }
        }
    }

    @Override
    public void render(Graphics g, int width, int height) {
        int centerX = width / 2;
        int centerY = height / 2;

        // Afficher la valeur actuelle de etapeCoup pour le débogage
        if (jeu != null) {
            // System.out.println("GameScene render: etapeCoup = " + etapeCoup);
        }

        // Calculer la position du bouton en fonction de la taille actuelle du panneau
        int dynamicButtonY = height - 70;
        // if (dynamicButtonY < 450) {
        // dynamicButtonY = 450; // Position Y minimale
        // }

        // int buttonCommonHeight = Math.max(40, height / 18);
        // int backButtonWidth = Math.max(130, width / 7);
        // int actionButtonWidth = Math.max(90, width / 9);

        // backButton.setSize(backButtonWidth, buttonCommonHeight);
        // backButton.setLocation(30, dynamicButtonY);

        // int actionButtonXStart = backButton.getX() + backButton.getWidth() + 20;

        // // Définir la position du bouton "Annuler"
        // undoButton.setSize(actionButtonWidth, buttonCommonHeight);
        // undoButton.setLocation(actionButtonXStart + actionButtonWidth / 2,
        // dynamicButtonY);

        // // Centrer le bouton
        // choosePlateauButton.setLocation(width / 2 - choosePlateauWidth / 2, height /
        // 2);
        // // Modifier la couleur du bouton pour le rendre plus visible
        // choosePlateauButton.setNormalColor(new Color(50, 150, 50)); // Vert
        // choosePlateauButton.setHoverColor(new Color(100, 200, 100)); // Vert clair
        // choosePlateauButton.setClickColor(new Color(30, 100, 30)); // Vert foncé

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
            int choosePlateauWidth = width * 24 / 100; // 24% de la largeur du panneau
            int choosePlateauHeight = choosePlateauWidth; // 32% de la hauteur du panneau car ratio est 16:9
            choosePlateauButton.setSize(choosePlateauWidth, choosePlateauHeight);

            // Spacine entre les plateaux
            int spacing = width / 10; // Espace entre les plateaux, 10% de la largeur du panneau
            int topMargin = height / 20; // Espace pour statusMessage, 5% de la hauteur du panneau
            int bottomMargin = height / 20; // Espace pour les boutons, 5% de la hauteur du panneau

            // Definir les tailles de plateau (dynamique en fonction de la taille du
            // panneau)
            int boardSize = jeu.getTAILLE();
            int tileWidth = choosePlateauHeight / boardSize; // Taille de la tuile basée sur la hauteur du panneau
            int tileHeight = tileWidth * 1; // A changer pour dessiner isométriquement
            int deltaX = 0; // Décalage horizontal pour centrer les plateaux -> Pour ce moment pas
                            // d'isométrie

            // Definir la position depart de chaque plateau
            int presentStartX = centerX - choosePlateauWidth / 2 + deltaX;
            int pastStartX = presentStartX - choosePlateauWidth - spacing;
            int futureStartX = presentStartX + choosePlateauWidth + spacing;
            int offsetY = centerY - choosePlateauHeight / 2; // Centrer verticalement les plateaux

            Plateau past = jeu.getPast();
            Plateau present = jeu.getPresent();
            Plateau future = jeu.getFuture();

            // Dessiner les pickers (prochain plateau)
            drawPickerJ1(g2d, pastStartX, presentStartX, futureStartX, offsetY, tileWidth);
            drawPickerJ2(g2d, pastStartX, presentStartX, futureStartX, offsetY, tileWidth);

            // Dessiner les plateaux
            drawPlateau(g2d, past, pastStartX, offsetY, tileWidth, "PASSÉ", null);
            drawPlateau(g2d, present, presentStartX, offsetY, tileWidth, "PRÉSENT", crackPresentImage);
            drawPlateau(g2d, future, futureStartX, offsetY, tileWidth, "FUTURE", backgroundImage);

            // À l'étape etapeCoup=3, mettez en surbrillance le plateau sélectionné par
            // l'utilisateur (s'il y en a un)
            if (etapeCoup == 3) {

                // À l'étape etapeCoup=3, assurez-vous que le bouton "Choisir un plateau" est
                // affiché
                if (isMyTurn()) {
                    // Afficher le texte d'invite
                    g2d.setColor(Color.YELLOW);
                    g2d.setFont(new Font("Arial", Font.BOLD, 18));
                    String selectBoardMessage = "Sélectionnez un plateau pour le prochain tour";
                    FontMetrics metrics = g2d.getFontMetrics();
                    int selectMsgWidth = metrics.stringWidth(selectBoardMessage);
                    g2d.drawString(selectBoardMessage, (width - selectMsgWidth) / 2, offsetY - 20);
                }
            }

            // Dessiner les trois plateaux, en mettant en évidence le plateau actif
            int myPlayerId = gameClient != null ? gameClient.getMyPlayerId() : -1;
            int currentPlayerId = jeu.getJoueurCourant() != null ? jeu.getJoueurCourant().getId() : -1;

            // Déterminer les plateaux sélectionnés par le joueur actuel et l'adversaire
            Plateau.TypePlateau myNextPlateau = (myPlayerId == 1) ? joueur1SelectedPlateau : joueur2SelectedPlateau;
            Plateau.TypePlateau opponentNextPlateau = (myPlayerId == 1) ? joueur2SelectedPlateau
                    : joueur1SelectedPlateau;

            // System.out.println("Mon ID : " + myPlayerId +
            // ", ID du joueur actuel : " + currentPlayerId +
            // ", Plateau que j'ai sélectionné : " + myNextPlateau +
            // ", Plateau sélectionné par l'adversaire : " + opponentNextPlateau);

            // 在etapeCoup=3时，显示棋盘选择按钮
            if (etapeCoup == 3 && isMyTurn()) {
                // Feedforward des plateaux
                g2d.setColor(Color.WHITE);

                Stroke originalStroke = g2d.getStroke();
                g2d.setStroke(new BasicStroke(4f));

                Plateau p;
                if (mousePoint != null) {
                    p = getPlateauFromMousePoint(mousePoint);
                    if (p != null && p.getType() != activePlateau) {
                        if (p.getType() == Plateau.TypePlateau.PAST) {
                            g2d.drawRoundRect(pastStartX - 2, offsetY - 2, tileWidth * past.getSize() + 4,
                                    tileWidth * past.getSize() + 4,
                                    10, 10);
                        } else if (p.getType() == Plateau.TypePlateau.PRESENT) {
                            g2d.drawRect(presentStartX - 2, offsetY - 2, tileWidth * present.getSize() + 4,
                                    tileWidth * present.getSize() + 4);
                        } else {
                            g2d.drawRoundRect(futureStartX - 2, offsetY - 2, tileWidth * future.getSize() + 4,
                                    tileWidth * future.getSize() + 4, 5, 5);
                        }
                    }
                }
                g2d.setStroke(originalStroke);

                // 计算按钮宽度和位置
                int buttonWidth = Math.min(boardSize * tileWidth, 120);
                int buttonHeight = 40;
                int buttonY = offsetY + choosePlateauHeight + 10;

                // Définir les attributs des boutons
                choosePastButton.setSize(buttonWidth, buttonHeight);
                choosePresentButton.setSize(buttonWidth, buttonHeight);
                chooseFutureButton.setSize(buttonWidth, buttonHeight);

                // Définir la position des boutons
                choosePastButton.setLocation(pastStartX + (boardSize * tileWidth - buttonWidth) / 2, buttonY);
                choosePresentButton.setLocation(presentStartX + (boardSize * tileWidth - buttonWidth) / 2, buttonY);
                chooseFutureButton.setLocation(futureStartX + (boardSize * tileWidth - buttonWidth) / 2, buttonY);

                // Définir la couleur des boutons
                Color normalColor = new Color(50, 150, 50);
                Color hoverColor = new Color(100, 200, 100);
                Color clickColor = new Color(30, 100, 30);

                choosePastButton.setNormalColor(normalColor);
                choosePastButton.setHoverColor(hoverColor);
                choosePastButton.setClickColor(clickColor);

                choosePresentButton.setNormalColor(normalColor);
                choosePresentButton.setHoverColor(hoverColor);
                choosePresentButton.setClickColor(clickColor);
                chooseFutureButton.setNormalColor(normalColor);
                chooseFutureButton.setHoverColor(hoverColor);
                chooseFutureButton.setClickColor(clickColor);

                // Rendre (afficher) les boutons
                choosePastButton.render(g2d);
                choosePresentButton.render(g2d);
                chooseFutureButton.render(g2d);

                // Dessiner le texte d'invite
                g2d.setColor(Color.YELLOW);
                g2d.setFont(new Font("Arial", Font.BOLD, 18));
                String selectBoardMessage = "Sélectionnez un plateau pour le prochain tour";
                FontMetrics metrics = g2d.getFontMetrics();
                int selectMsgWidth = metrics.stringWidth(selectBoardMessage);
                g2d.drawString(selectBoardMessage, (width - selectMsgWidth) / 2, offsetY - 20);
            }

            // Message de statut
            g2d.setColor(Color.CYAN);
            g2d.setFont(new Font("Consolas", Font.BOLD, 18));
            if (statusMessage != null && !statusMessage.isEmpty()) {
                FontMetrics metrics = g2d.getFontMetrics();
                int msgWidth = metrics.stringWidth(statusMessage);
                g2d.drawString(statusMessage, (width - msgWidth) / 2, 40);
            }

            // Informations sur les clones avec Avatars
            g2d.setFont(new Font("Consolas", Font.PLAIN, 15));
            g2d.setColor(Color.LIGHT_GRAY);
            int cloneInfoY = offsetY + choosePlateauHeight + 25;
            if (cloneInfoY >= dynamicButtonY - 15) { // Éviter de superposer les boutons
                cloneInfoY = offsetY - 35; // Placer au-dessus du plateau s'il n'y a pas assez d'espace
                if (cloneInfoY < 20) {
                    cloneInfoY = 20;
                }
            }

            int avatarSize = 30;

            if (jeu.getJoueur1() != null) {
                if (lemielAvatarImage != null) {
                    g2d.drawImage(lemielAvatarImage, Math.max(10, pastStartX - 20), cloneInfoY - avatarSize, avatarSize,
                            avatarSize, null);
                }
                String p1Info = "Lemiel (ID " + jeu.getJoueur1().getId() + ") Clones: "
                        + jeu.getJoueur1().getNbClones();
                if (gameClient != null && jeu.getJoueur1().getId() == gameClient.getMyPlayerId()) {
                    p1Info += " (Vous)";
                }
                // Ajouter les informations de sélection du plateau
                p1Info += " - Plateau: " + joueur1SelectedPlateau;
                g2d.drawString(p1Info, Math.max(10, pastStartX - 20) + avatarSize + 5, cloneInfoY);
            }

            if (jeu.getJoueur2() != null) {
                String p2Info = "Zarek (ID " + jeu.getJoueur2().getId() + ") Clones: " + jeu.getJoueur2().getNbClones();
                if (gameClient != null && jeu.getJoueur2().getId() == gameClient.getMyPlayerId()) {
                    p2Info += " (Vous)";
                }
                // Ajouter les informations de sélection du plateau
                p2Info += " - Plateau: " + joueur2SelectedPlateau;
                FontMetrics p2Metrics = g2d.getFontMetrics();
                int p2InfoWidth = p2Metrics.stringWidth(p2Info);
                int p2X = Math.min(width - 10 - p2InfoWidth - avatarSize - 5,
                        futureStartX + (boardSize * tileWidth) + 20 - p2InfoWidth - avatarSize - 5);

                if (zarekAvatarImage != null) {
                    g2d.drawImage(zarekAvatarImage, p2X + p2InfoWidth + 5, cloneInfoY - avatarSize, avatarSize,
                            avatarSize, null);
                }
                g2d.drawString(p2Info, p2X, cloneInfoY);
            }

            // Rendre les boutons
            backButton.render(g2d);

            // Afficher le bouton Annuler seulement si c'est mon tour et que etapeCoup n'est
            // pas égal à 3
            if (isMyTurn() && (etapeCoup == 1 || etapeCoup == 2)) {
                undoButton.render(g2d);
            }

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

        // Afficher ces boutons uniquement si c'est le tour du joueur
        boolean isMyTurn = isMyTurn();
        if (isMyTurn) {
            // Utiliser le champ etapeCoup de GameScene
            if (etapeCoup == 1 || etapeCoup == 2) {
                undoButton.render(g2d);
            }

        }
        g2d.dispose();
    }

    private void drawPickerJ1(Graphics2D g, int pastStartX, int presentStartX, int futureStartX,
            int offsetY, int tileWidth) {
        int size = tileWidth * 7 / 8; // Taille du picker du prochain plateau => 7% de height
        int spacing = tileWidth / 2; // Espace entre les boutons du picker => 4% de width
        int pickerY = offsetY - spacing - size; // Position Y du picker, juste en dessous des plateaux

        int[] xPos = {
                pastStartX + tileWidth * 2 - size / 2, // Position pour le plateau passé
                presentStartX + tileWidth * 2 - size / 2, // Position pour le plateau présent
                futureStartX + tileWidth * 2 - size / 2 // Position pour le plateau futur
        };

        Shape oldClip = g.getClip();

        // Pour chaque plateau, dessiner un oval de sélection
        for (int plt = 0; plt < 3; plt++) {
            g.setColor(Color.BLACK);
            g.fillOval(xPos[plt], pickerY, size, size); // Fond noir

            // Dessiner l'avatar
            switch (jeu.getJoueur1().getProchainPlateau()) {
                case PAST:
                    if (lemielAvatarImage != null) {
                        g.drawImage(lemielAvatarImage, xPos[0], pickerY, size, size, null);
                    }
                    break;

                case PRESENT:
                    if (lemielAvatarImage != null) {
                        g.drawImage(lemielAvatarImage, xPos[1], pickerY, size, size, null);
                    }
                    break;

                case FUTURE:
                    if (lemielAvatarImage != null) {
                        g.drawImage(lemielAvatarImage, xPos[2], pickerY, size, size, null);
                    }
                    break;
            }
        }

        g.setClip(oldClip); // Restaurer le clip précédent
    }

    private void drawPickerJ2(Graphics2D g, int pastStartX, int presentStartX, int futureStartX,
            int offsetY, int tileWidth) {
        int size = tileWidth * 7 / 8; // Taille du picker du prochain plateau => 7% de height
        int spacing = tileWidth / 2; // Espace entre les boutons du picker => 4% de width
        int pickerY = offsetY + tileWidth * jeu.getTAILLE() + spacing; // Position Y du picker, juste en dessous des
                                                                       // plateaux

        int[] xPos = {
                pastStartX + tileWidth * 2 - size / 2, // Position pour le plateau passé
                presentStartX + tileWidth * 2 - size / 2, // Position pour le plateau présent
                futureStartX + tileWidth * 2 - size / 2 // Position pour le plateau futur
        };

        Shape oldClip = g.getClip();

        // Pour chaque plateau, dessiner un oval de sélection
        for (int plt = 0; plt < 3; plt++) {
            g.setColor(Color.BLACK);
            g.fillOval(xPos[plt], pickerY, size, size); // Fond noir

            // Dessiner l'avatar
            switch (jeu.getJoueur2().getProchainPlateau()) {
                case PAST:
                    if (zarekAvatarImage != null) {
                        g.drawImage(zarekAvatarImage, xPos[0], pickerY, size, size, null);
                    }
                    break;

                case PRESENT:
                    if (zarekAvatarImage != null) {
                        g.drawImage(zarekAvatarImage, xPos[1], pickerY, size, size, null);
                    }
                    break;

                case FUTURE:
                    if (zarekAvatarImage != null) {
                        g.drawImage(zarekAvatarImage, xPos[2], pickerY, size, size, null);
                    }
                    break;
            }
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
        g.drawRect(x - 1, y - 1, boardPixelSize + 1, boardPixelSize + 1);

        for (int row = 0; row < boardSize; row++) {
            for (int col = 0; col < boardSize; col++) {
                Piece piece = plateau.getPiece(row, col);

                // Set up les couleurs de fond des cases
                Color white = null, black = null;
                switch (plateau.getType()) {
                    case PAST:
                        white = new Color(0xe8e7de);
                        black = new Color(0xbfb9b4);
                        break;
                    case PRESENT:
                        white = new Color(0xb3afac);
                        black = new Color(0x8e8a84);
                        break;
                    case FUTURE:
                        white = new Color(0x777871);
                        black = new Color(0x545251);
                        break;
                }

                if (row % 2 == 0 && col % 2 == 0 || row % 2 == 1 && col % 2 == 1) {
                    g.setColor(white); // Couleur blanche pour les cases paires
                } else {
                    g.setColor(black); // Couleur noire pour les cases impaires
                }

                // Dessiner la case
                g.fillRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);

                if (piece != null && piece.getOwner() != null) {
                    int imageHeight = tileWidth; // Taille de l'image du personnage
                    int imageWidth = imageHeight * zarekAnimation[0][0].getWidth() / zarekAnimation[0][0].getHeight(); // Ratio de l'image
                    switch (piece.getOwner().getId()) {
                        case 1: // Joueur 1 (Lemiel)
                            g.drawImage(lemielAnimation[0][frame], x + col * tileWidth, y + row * tileWidth, imageWidth, imageHeight, null);
                            g.drawImage(lemielAnimation[1][frame], x + col * tileWidth, y + row * tileWidth, imageWidth, imageHeight, null);
                            break;
                        case 2: // Joueur 2 (Zarek)
                            g.drawImage(zarekAnimation[0][frame], x + col * tileWidth, y + row * tileWidth, imageWidth, imageHeight, null);
                            g.drawImage(zarekAnimation[1][frame], x + col * tileWidth, y + row * tileWidth, imageWidth, imageHeight, null);
                            break;
                        default:
                            g.setColor(Color.GRAY); // Couleur par défaut si l'ID n'est pas reconnu
                    }
                }

                // Mettre en surbrillance la pièce sélectionnée
                // if (piece == jeu.getPieceCourante()) {
                //     g.setColor(Color.ORANGE);
                //     g.setStroke(new BasicStroke(Math.max(2.5f, tileWidth / 10f)));
                //     g.drawRect(pieceX - 2, pieceY - 2, pieceSize + 4, pieceSize + 4);
                // }

                g.setStroke(new BasicStroke(1f)); // Réinitialiser l'épaisseur des lignes
            }
        }
        
        // Si une image de fissure est disponible et que ce n'est pas le plateau du
        // passé, dessiner l'image de fond
        // if (crackImage != null && !plateau.getType().equals(Plateau.TypePlateau.PAST)) {
        //     g.drawImage(crackImage, x, y, boardPixelSize, boardPixelSize, null);
        // }
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

                // Les boutons d'action ne sont traités que si c'est le tour du joueur
                if (isMyTurn()) {
                    // 处理撤销按钮
                    if (undoButton.contains(mousePoint) && (etapeCoup == 1 || etapeCoup == 2)) {
                        undoButton.onClick();
                        return;
                    }

                    // Gérer les boutons de sélection de plateau
                    if (choosePastButton.contains(mousePoint) && etapeCoup == 3) {
                        System.out.println("Clic sur le bouton 'Sélectionner plateau PASSÉ'");
                        choosePastButton.onClick();
                        return;
                    }

                    if (choosePresentButton.contains(mousePoint) && etapeCoup == 3) {
                        System.out.println("Clic sur le bouton 'Sélectionner plateau PRÉSENT'");
                        choosePresentButton.onClick();
                        return;
                    }

                    if (chooseFutureButton.contains(mousePoint) && etapeCoup == 3) {
                        System.out.println("Clic sur le bouton 'Sélectionner plateau FUTUR'");
                        chooseFutureButton.onClick();
                        return;
                    }

                    // Le bouton original de sélection de plateau peut ne plus être utilisé
                    /*
                     * if (choosePlateauButton.contains(mousePoint) && etapeCoup == 3) {
                     * System.out.println("Clic sur le bouton 'Choisir un plateau'");
                     * choosePlateauButton.onClick();
                     * return;
                     * }
                     */
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

                // Ajouter la gestion des nouveaux boutons
                if (isMyTurn()) {
                    if (undoButton.contains(mousePoint) && (etapeCoup == 1 || etapeCoup == 2)) {
                        undoButton.setClicked(true);
                        needsRepaint = true;
                    }

                    // Les trois boutons de sélection de plateau
                    if (etapeCoup == 3) {
                        if (choosePastButton.contains(mousePoint)) {
                            choosePastButton.setClicked(true);
                            needsRepaint = true;
                        }
                        if (choosePresentButton.contains(mousePoint)) {
                            choosePresentButton.setClicked(true);
                            needsRepaint = true;
                        }
                        if (chooseFutureButton.contains(mousePoint)) {
                            chooseFutureButton.setClicked(true);
                            needsRepaint = true;
                        }

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
                choosePlateauButton.setClicked(false);
                choosePastButton.setClicked(false);
                choosePresentButton.setClicked(false);
                chooseFutureButton.setClicked(false);
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
        int choosePlateauWidth = width * 24 / 100; // 24% de la largeur de l'écran
        int choosePlateauHeight = choosePlateauWidth; // 32% de l'hauteur de l'écran

        // Spacing entre les plateaux
        int spacing = width / 10; // Espace entre les plateaux, 10% de la largeur du panneau

        // Définir les tailles de plateau (dynamique en fonction de la taille du
        // panneau)
        int boardSize = jeu.getTAILLE();
        int tileWidth = choosePlateauWidth / boardSize; // Largeur d'une case
        int tileHeight = tileWidth * 1; // Hauteur d'une case (carrée pour l'instant)
        int deltaX = 0; // Décalage horizontal pour centrer les plateaux -> Pour ce moment pas
                        // d'isométrie

        // Definir la position de départ de chaque plateau
        int offsetY = centerY - choosePlateauHeight / 2; // Centrer verticalement
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
        int choosePlateauWidth = width * 24 / 100; // 24% de la largeur de l'écran
        int choosePlateauHeight = choosePlateauWidth; // 32% de l'hauteur de l'écran

        // Spacing entre les plateaux
        int spacing = width / 10; // Espace entre les plateaux, 10% de la largeur du panneau

        // Définir les tailles de plateau (dynamique en fonction de la taille du
        // panneau)
        int boardSize = jeu.getTAILLE();
        int tileWidth = choosePlateauWidth / boardSize; // Largeur d'une case
        int tileHeight = tileWidth * 1; // Hauteur d'une case (carrée pour l'instant)
        int deltaX = 0; // Décalage horizontal pour centrer les plateaux -> Pour ce moment pas
                        // d'isométrie

        // Definir la position de départ de chaque plateau
        int offsetY = centerY - choosePlateauHeight / 2; // Centrer verticalement
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
        isLoading = false; // La réception d'un message du serveur indique que le chargement initial est
                           // terminé

        System.out.println("GameScene: Message reçu: " + messageType + " -> " + messageContent);

        switch (messageType) {
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

                            // Mettre à jour selectedPiecePosition et selectedPlateauType
                            selectedPiecePosition = new Point(newX, newY);
                            selectedPlateauType = newPlateauType;
                            this.activePlateau = newPlateauType;

                            System.out.println("GameScene: Mise à jour de la position de la pièce - de " +
                                    (selectedPiecePosition != null
                                            ? selectedPiecePosition.x + "," + selectedPiecePosition.y
                                            : "null")
                                    +
                                    " à " + newX + "," + newY +
                                    ", plateau de " + selectedPlateauType + " à " + newPlateauType);
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
                gameHasEnded = true;
                String dialogTitle = ("WIN".equals(messageType) || "GAGNE".equals(messageType)) ? "FÉLICITATIONS !"
                        : "DOMMAGE !";
                // Modifier le contenu du message
                String victoryContent = messageContent;
                if (messageContent.contains("Joueur 1")) {
                    victoryContent = messageContent.replace("Joueur 1", "Lemiel");
                } else if (messageContent.contains("Joueur 2")) {
                    victoryContent = messageContent.replace("Joueur 2", "Zarek");
                }
                statusMessage = victoryContent;
                JOptionPane.showMessageDialog(sceneManager.getPanel(), victoryContent, dialogTitle,
                        JOptionPane.INFORMATION_MESSAGE);
                break;

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

                break;

            default:
                statusMessage = messageType + ": " + messageContent;
                break;
        }

        repaintPanel();
    }

    @Override
    public void dispose() {
        System.out.println("GameScene: Dispose appelée.");
        isLoading = false;
        clearMouseListeners();

        // Nettoie le client et le serveur
        if (gameClient != null) {
            gameClient.disconnect();
            gameClient = null;
        }

        // Si cette GameScene gère le serveur hôte, l'arrêter
        if (hostServerManager != null) {
            System.out.println("GameScene: Arrêt du serveur hôte géré par GameScene.");
            hostServerManager.stopServer();
            hostServerManager = null;
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
}