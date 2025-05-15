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

public class GameScene implements Scene, GameStateUpdateListener {

    private SceneManager sceneManager;
    private Jeu jeu; // Luôn là bản sao của trạng thái game từ server

    // 背景和图像资源
    private BufferedImage backgroundImage;
    private BufferedImage lemielImage;
    private BufferedImage zarekImage;
    private BufferedImage crackPresentImage;
    private BufferedImage crackFutureImage;
    private BufferedImage lemielAvatarImage;
    private BufferedImage zarekAvatarImage;
    private int zarekAnimationFrame = 0;
    private long lastFrameUpdateTime = 0;
    private BufferedImage[] zarekAnimationFrames;

    // Trạng thái lựa chọn của UI
    private Point selectedPiecePosition = null;
    private Plateau.TypePlateau selectedPlateauType = null;
    private Coup.TypeCoup nextActionType = null; // On attend click to MOVE

    // UI Buttons
    private Button backButton;
    private Button undoButton; // 保留撤销按钮
    private Button choosePlateauButton; // 添加选择棋盘按钮
    
    // 添加三个棋盘选择按钮
    private Button choosePastButton;
    private Button choosePresentButton;
    private Button chooseFutureButton;

    // Network and Game Mode
    private GameClient gameClient;
    private String serverIpToConnectOnDemand; // IP pour que le client se connecte (si ce n'est pas l'hôte/solo)
    private boolean isOperatingInSinglePlayerMode; // True si c'est le mode Solo avec auto-hébergement

    private String statusMessage = "Initialisation...";
    private volatile boolean gameHasEnded = false; // volatile car peut être mis à jour depuis un autre thread (onGameMessage)
    private volatile boolean isLoading = false; // Pour afficher l'état de chargement
    private int etapeCoup = 0; // 直接在GameScene中存储etapeCoup值

    // Serveur et IA locaux pour le mode solo
    // Static pour garantir qu'il n'y a qu'une seule instance si GameScene est recréée rapidement (même si dispose devrait gérer cela)
    private static GameServerManager localSinglePlayerServerManager;
    private static Thread localAIClientThread;
    private static AIClient aiClientInstance; // Conserve l'instance de l'IA pour pouvoir la déconnecter

    private MouseAdapter mouseAdapterInternal;
    // MouseMotionListener est intégré dans MouseAdapter si mouseAdapterInternal hérite de MouseAdapter et implémente MouseMotionListener
    // Ou créer une variable séparée pour MouseMotionListener

    // Ajout pour la gestion du serveur en mode multijoueur
    private GameServerManager hostServerManager; // Instance du serveur reprise de HostingScene

    // 存储玩家选择的棋盘
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
            throw new IllegalArgumentException("Pour le mode multijoueur client, utilisez le constructeur avec l'IP du serveur.");
        }
        loadResources();
        commonUIInit();
    }

    // Constructor cho Client kết nối tới Host trong Multiplayer
    public GameScene(SceneManager sceneManager, String serverIpToConnect) {
        this.sceneManager = sceneManager;
        this.isOperatingInSinglePlayerMode = false;
        this.serverIpToConnectOnDemand = serverIpToConnect;
        this.statusMessage = "Mode Multi: Connexion à l'hôte...";
        loadResources();
        commonUIInit();
    }

    // Constructor cho Host trong Multiplayer (đã có GameClient được kết nối từ HostingScene)
    public GameScene(SceneManager sceneManager, GameClient alreadyConnectedHostClient, GameServerManager serverManager) {
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
                        + (this.jeu.getJoueurCourant() != null ? this.jeu.getJoueurCourant().getId() : "non défini") + ")");
                updateStatusFromCurrentGame(false); // Met à jour le message de statut initial
            }
        }

        loadResources();
        commonUIInit();
    }

    // 加载图像资源
    private void loadResources() {
        try {
            backgroundImage = ImageIO.read(new File("res/Background.png"));
            lemielImage = ImageIO.read(new File("res/Lemiel/Lemiel_Idle.png"));
            crackPresentImage = ImageIO.read(new File("res/Plateau/Crack_Present.png"));
            crackFutureImage = ImageIO.read(new File("res/Plateau/Crack_Future.png"));
            lemielAvatarImage = ImageIO.read(new File("res/Avatar/Lemiel_Avatar.png"));
            zarekAvatarImage = ImageIO.read(new File("res/Avatar/Zarek_Avatar.png"));

            // 加载Zarek动画帧
            zarekAnimationFrames = new BufferedImage[4];
            zarekAnimationFrames[0] = ImageIO.read(new File("res/Zarek/Zarek_Idle_1.png"));
            zarekAnimationFrames[1] = ImageIO.read(new File("res/Zarek/Zarek_Idle_2.png"));
            zarekAnimationFrames[2] = ImageIO.read(new File("res/Zarek/Zarek_Idle_3.png"));
            zarekAnimationFrames[3] = ImageIO.read(new File("res/Zarek/Zarek_Idle_4.png"));
            zarekImage = zarekAnimationFrames[0]; // 初始帧
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
        // Vị trí nút sẽ được cập nhật trong render()
        backButton = new Button(0, 0, 150, 40, "Retour Menu", this::handleBackButton);

        // 添加撤销按钮
        undoButton = new Button(0, 0, 100, 40, "UNDO", this::handleUndoAction);

        // 添加选择棋盘按钮
        choosePlateauButton = new Button(0, 0, 180, 40, "Choisir ce plateau", this::handleChoosePlateauAction);

        // 添加三个棋盘选择按钮
        choosePastButton = new Button(0, 0, 100, 40, "PAST", this::handleChoosePastAction);
        choosePresentButton = new Button(0, 0, 100, 40, "PRESENT", this::handleChoosePresentAction);
        chooseFutureButton = new Button(0, 0, 100, 40, "FUTURE", this::handleChooseFutureAction);
    }

    private void handleBackButton() {
        int confirmation = JOptionPane.YES_OPTION;
        if (!gameHasEnded && (gameClient != null && gameClient.isConnected())) { // Chỉ hỏi nếu đang trong game
            confirmation = JOptionPane.showConfirmDialog(
                    sceneManager.getPanel(),
                    "Quitter la partie en cours ?",
                    "Confirmation de sortie",
                    JOptionPane.YES_NO_OPTION
            );
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

        // En mode solo, arrête le serveur et l'IA seulement si cette GameScene les a créés
        if (isOperatingInSinglePlayerMode) {
            if (localAIClientThread != null && localAIClientThread.isAlive()) {
                if (aiClientInstance != null) {
                    aiClientInstance.disconnect(); // Demande à l'IA de se déconnecter

                                }localAIClientThread.interrupt(); // Interrompt le thread de l'IA
                localAIClientThread = null;
                aiClientInstance = null;
                System.out.println("GameScene (Solo): Thread AI arrêté.");
            }
            if (localSinglePlayerServerManager != null && localSinglePlayerServerManager.isServerRunning()) {
                localSinglePlayerServerManager.stopServer();
                // Ne pas définir localSinglePlayerServerManager = null pour pouvoir vérifier isServerRunning plus tard
                System.out.println("GameScene (Solo): Serveur local arrêté.");
            }
        }
        sceneManager.setScene(new MenuScene(sceneManager));
    }

    @Override
    public void init() {
        gameHasEnded = false;
        isLoading = true; // Bắt đầu với trạng thái loading
        resetSelection();

        setupMouseListeners(); // Luôn thiết lập listener khi scene init

        if (gameClient != null && gameClient.isConnected()) {
            // Trường hợp Host vào GameScene, client đã được kết nối và truyền vào
            System.out.println("GameScene: Utilisation du GameClient déjà connecté.");

            // Ajoute un mécanisme de timeout pour éviter d'attendre indéfiniment l'état du jeu
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
                isLoading = false; // Host đã kết nối, không cần loading kéo dài
                updateStatusFromCurrentGame(false);
            }
        } else if (isOperatingInSinglePlayerMode) {
            statusMessage = "Mode Solo: Démarrage du serveur local...";
            startSinglePlayerServerAndClients(); // Sẽ chạy trong SwingWorker
        } else if (serverIpToConnectOnDemand != null) { // Client kết nối tới IP
            statusMessage = "Connexion à " + serverIpToConnectOnDemand + "...";
            connectToRemoteServer(); // Sẽ chạy trong SwingWorker
        } else {
            statusMessage = "Erreur: Configuration GameScene invalide.";
            System.err.println(statusMessage);
            isLoading = false;
            // Có thể thêm hành động quay lại MenuScene ở đây nếu lỗi nghiêm trọng
            SwingUtilities.invokeLater(this::cleanUpAndGoToMenu);
        }
        repaintPanel();
    }

    private void startSinglePlayerServerAndClients() {
        isLoading = true;
        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                publish("Démarrage serveur solo...");
                // Đảm bảo dừng server cũ nếu có và nó thuộc về chế độ single player này
                if (localSinglePlayerServerManager != null && localSinglePlayerServerManager.isServerRunning()) {
                    System.out.println("GameScene (Solo Worker): Serveur local solo déjà actif, tentative de réutilisation ou redémarrage.");
                    // Có thể cần dừng và khởi tạo lại để đảm bảo trạng thái sạch
                    localSinglePlayerServerManager.stopServer();
                }
                localSinglePlayerServerManager = new GameServerManager(null); // null callback
                localSinglePlayerServerManager.startServer(); // Server chạy trên 127.0.0.1
                publish("Serveur solo démarré. Connexion du joueur UI...");

                gameClient = new GameClient("127.0.0.1", GameScene.this);
                gameClient.connect(); // Player UI kết nối
                publish("Joueur UI connecté (ID: " + gameClient.getMyPlayerId() + "). Démarrage AI...");

                // Dừng AI cũ nếu có
                if (localAIClientThread != null && localAIClientThread.isAlive()) {
                    if (aiClientInstance != null) {
                        aiClientInstance.disconnect();
                    }
                    localAIClientThread.interrupt();
                }

                aiClientInstance = new AIClient("127.0.0.1");
                aiClientInstance.connect(); // AI kết nối
                if (aiClientInstance.isConnected()) {
                    aiClientInstance.startListeningAndPlaying(); // Bắt đầu luồng của AI
                    publish("AI (ID: " + aiClientInstance.getMyPlayerId() + ") connectée et à l'écoute.");
                } else {
                    publish("Erreur: AI n'a pas pu se connecter.");
                    throw new IOException("AI Client connection failed.");
                }

                // GameServerManager sẽ tự động gọi startGameEngine khi đủ 2 client
                // (Player UI và AI Client) nếu được cấu hình đúng.
                // Chúng ta có thể đợi một chút để đảm bảo server có thời gian xử lý.
                Thread.sleep(500); // Chờ server xử lý kết nối AI
                if (localSinglePlayerServerManager.areAllPlayersConnected()) {
                    // startGameEngine đã được gọi tự động bởi GameServerManager
                    publish("Moteur de jeu solo prêt.");
                } else {
                    publish("Attente que le serveur démarre le moteur ("
                            + (localSinglePlayerServerManager.areAllPlayersConnected() ? "OK" : "Pas encore assez de joueurs") + ")");
                    // Có thể thêm một vòng lặp chờ ở đây nếu cần, nhưng lý tưởng là GameServerManager xử lý
                }
                return true;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String msg : chunks) {
                    statusMessage = msg;
                    System.out.println("GameScene (Solo Worker): " + msg); // Log tiến trình
                    repaintPanel();
                }
            }

            @Override
            protected void done() {
                isLoading = false;
                try {
                    Boolean success = get(); // Kiểm tra lỗi từ doInBackground
                    if (success) {
                        if (gameClient == null || !gameClient.isConnected()) {
                            statusMessage = "Erreur connexion joueur UI en mode solo.";
                        }
                        // statusMessage sẽ được cập nhật bởi onGameStateUpdate đầu tiên
                    } else {
                        statusMessage = "Échec du démarrage du mode solo.";
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    statusMessage = "Erreur critique mode solo: " + e.getMessage();
                    JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur Solo", JOptionPane.ERROR_MESSAGE);
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
                // Không cần publish("Connecté...") ở đây, vì onGameStateUpdate sẽ cập nhật statusMessage
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
                    get(); // Lấy kết quả hoặc bắt ngoại lệ
                    if (gameClient == null || !gameClient.isConnected()) {
                        statusMessage = "Échec de la connexion à l'hôte.";
                        JOptionPane.showMessageDialog(sceneManager.getPanel(),
                                "Impossible de se connecter au serveur : " + serverIpToConnectOnDemand,
                                "Erreur de Connexion", JOptionPane.ERROR_MESSAGE);
                        cleanUpAndGoToMenu();
                    }
                    // Nếu thành công, onGameStateUpdate đầu tiên sẽ đặt statusMessage chính xác
                } catch (Exception e) {
                    e.printStackTrace();
                    statusMessage = "Erreur de connexion: " + e.getMessage();
                    JOptionPane.showMessageDialog(sceneManager.getPanel(),
                            "Impossible de se connecter au serveur : " + e.getMessage() + "\nIP : " + serverIpToConnectOnDemand,
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
            System.out.println("isMyTurn: false, jeu为null");
            return false;
        }

        if (jeu.getJoueurCourant() == null || gameClient == null || gameClient.getMyPlayerId() == -1) {
            System.out.println("isMyTurn: false, jeu.getJoueurCourant(): "
                    + (jeu.getJoueurCourant() == null ? "null" : jeu.getJoueurCourant())
                    + ", gameClient: " + gameClient
                    + ", gameClient.getMyPlayerId(): " + (gameClient != null ? gameClient.getMyPlayerId() : -1));
            return false;
        }

        boolean isMyTurn = jeu.getJoueurCourant().getId() == gameClient.getMyPlayerId();
        // System.out.println("isMyTurn: " + isMyTurn + 
        //     ", jeu.getJoueurCourant().getId(): " + jeu.getJoueurCourant().getId() + 
        //     ", gameClient.getMyPlayerId(): " + gameClient.getMyPlayerId());
        return isMyTurn;
    }

    private void handleBoardClick(Point mousePoint) {
        if (jeu == null || gameClient == null || !gameClient.isConnected() || gameHasEnded) {
            statusMessage = "Jeu non prêt ou déconnecté.";
            repaintPanel();
            return;
        }

        // 检查是否是自己的回合
        if (!isMyTurn()) {
            statusMessage = "Ce n'est pas votre tour.";
            repaintPanel();
            return;
        }

        Plateau clickedPlateauObj = getPlateauFromMousePoint(mousePoint);
        Point boardCoords = getBoardCoordinates(mousePoint);

        if (clickedPlateauObj != null && boardCoords != null) { // 棋盘上的有效点击
            int clickedRow = boardCoords.x;
            int clickedCol = boardCoords.y;
            Plateau.TypePlateau clickedPlateauType = clickedPlateauObj.getType();

            // 简化版：无论在哪个阶段，都只发送点击坐标和棋盘类型给服务器
            // 服务器将根据游戏阶段(etapeCoup)判断这次点击是什么操作
            String command = "0:null:" + clickedPlateauType.name() + ":" + clickedRow + ":" + clickedCol;
            
            System.out.println("GameScene: 发送点击信息 - 棋盘类型: " + clickedPlateauType + 
                                ", 位置: (" + clickedRow + "," + clickedCol + 
                                "), etapeCoup: " + etapeCoup);
            
            // 如果是首次点击，记录为selectedPiecePosition（这只是为了在UI上显示选择效果）
            if (etapeCoup == 0) {
                selectedPiecePosition = new Point(clickedRow, clickedCol);
                selectedPlateauType = clickedPlateauType;
                statusMessage = "Sélection du pion (" + clickedPlateauType + " " + clickedRow + "," + clickedCol + ")...";
            } else {
                statusMessage = "Action sur plateau " + clickedPlateauType + " à la position (" + clickedRow + "," + clickedCol + ")...";
            }
            
            gameClient.sendPlayerAction(command);
            repaintPanel();
        } else { // 棋盘外的点击
            if (etapeCoup == 3) {
                statusMessage = "Veuillez sélectionner un plateau (PAST, PRESENT ou FUTURE).";
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

        // 使用GameScene中的etapeCoup字段
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

        // 发送格式：<Undo ? 1 : 0>:<ProchainPlateau: null>:<SelectedPlateau>:<x>:<y>
        String command = "0:null:" + selectedPlateauType.name() + ":" + targetPosition.x + ":" + targetPosition.y;
        gameClient.sendPlayerAction(command);
        statusMessage = "Commande envoyée: déplacement vers " + targetPosition.x + "," + targetPosition.y;
        repaintPanel();
    }

    // 添加特殊的撤销操作方法
    public void handleUndoAction() {
        if (jeu == null || gameClient == null || !gameClient.isConnected() || gameHasEnded) {
            return;
        }

        if (!isMyTurn()) {
            statusMessage = "Ce n'est pas votre tour.";
            repaintPanel();
            return;
        }

        // 使用GameScene中的etapeCoup字段
        if (etapeCoup == 1 || etapeCoup == 2) {
            // 只有在etapeCoup为1或2时撤销才有意义
            // 发送格式：<Undo ? 1 : 0>:<ProchainPlateau: null>:<SelectedPlateau>:<x>:<y>
            String command = "1:null:" + selectedPlateauType.name() + ":" + selectedPiecePosition.x + ":" + selectedPiecePosition.y;
            gameClient.sendPlayerAction(command);
            statusMessage = "Demande d'annulation envoyée...";
            resetSelection();
            repaintPanel();
        }
    }

    @Override
    public void update() {
        // 更新Zarek动画
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFrameUpdateTime > 250) { // 每250ms更新一帧
            zarekAnimationFrame = (zarekAnimationFrame + 1) % 4;
            zarekImage = zarekAnimationFrames[zarekAnimationFrame];
            lastFrameUpdateTime = currentTime;
        }

        // Chỉ cập nhật hover nếu không loading và game chưa kết thúc
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
                    // 使用GameScene中的etapeCoup字段
                    if (etapeCoup == 1 || etapeCoup == 2) {
                        undoButton.update(mousePos);
                    } else {
                        undoButton.update(new Point(-1, -1));
                    }

                    // 在etapeCoup为3时显示"选择棋盘"按钮
                    if (etapeCoup == 3) {
                        System.out.println("Devrait afficher le bouton: Selectionner le plateau");
                        System.out.println("MousePos: " + mousePos.x + "," + mousePos.y);
                        
                        // 更新三个棋盘选择按钮
                        choosePastButton.update(mousePos);
                        choosePresentButton.update(mousePos);
                        chooseFutureButton.update(mousePos);
                        
                        // 原有的按钮可以不再使用
                        // choosePlateauButton.update(mousePos);

                        // 检查按钮区域是否有效
                        System.out.println("Past按钮位置: " + choosePastButton.getX() + "," + choosePastButton.getY()
                                + " width:" + choosePastButton.getWidth() + " height:" + choosePastButton.getHeight());
                        System.out.println("Present按钮位置: " + choosePresentButton.getX() + "," + choosePresentButton.getY()
                                + " width:" + choosePresentButton.getWidth() + " height:" + choosePresentButton.getHeight());
                        System.out.println("Future按钮位置: " + chooseFutureButton.getX() + "," + chooseFutureButton.getY()
                                + " width:" + chooseFutureButton.getWidth() + " height:" + chooseFutureButton.getHeight());
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
        // 打印当前etapeCoup值，用于调试
        if (jeu != null) {
            System.out.println("GameScene render: etapeCoup = " + etapeCoup);
        }

        // Tính toán vị trí nút dựa trên kích thước hiện tại của panel
        int dynamicButtonY = height - 70;
        if (dynamicButtonY < 450) {
            dynamicButtonY = 450; // Vị trí Y tối thiểu

                }int buttonCommonHeight = Math.max(40, height / 18);
        int backButtonWidth = Math.max(130, width / 7);
        int actionButtonWidth = Math.max(90, width / 9);

        backButton.setSize(backButtonWidth, buttonCommonHeight);
        backButton.setLocation(30, dynamicButtonY);

        int actionButtonXStart = backButton.getX() + backButton.getWidth() + 20;

        // 设置撤销按钮位置
        undoButton.setSize(actionButtonWidth, buttonCommonHeight);
        undoButton.setLocation(actionButtonXStart + actionButtonWidth / 2, dynamicButtonY);

        // 设置选择棋盘按钮位置 - 更大，更居中
        int choosePlateauWidth = Math.max(200, width / 5); // 增加宽度
        int choosePlateauHeight = Math.max(50, height / 15); // 增加高度
        choosePlateauButton.setSize(choosePlateauWidth, choosePlateauHeight);
        // 将按钮居中放置
        choosePlateauButton.setLocation(width / 2 - choosePlateauWidth / 2, height / 2);
        // 变更按钮颜色，使其更醒目
        choosePlateauButton.setNormalColor(new Color(50, 150, 50)); // 绿色
        choosePlateauButton.setHoverColor(new Color(100, 200, 100)); // 亮绿色
        choosePlateauButton.setClickColor(new Color(30, 100, 30)); // 深绿色

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 绘制背景图像
        if (backgroundImage != null) {
            g2d.drawImage(backgroundImage, 0, 0, width, height, null);
        } else {
            // 如果背景加载失败，使用原来的背景色
            g2d.setColor(new Color(25, 25, 35));
            g2d.fillRect(0, 0, width, height);
        }

        // Nếu đang loading, hiển thị thông báo loading
        if (isLoading) {
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 24));
            FontMetrics metrics = g2d.getFontMetrics();
            String loadingMsg = statusMessage != null ? statusMessage : "Chargement...";
            if (loadingMsg.contains("Démarrage AI") || loadingMsg.contains("Attente que l'AI se connecte")) {
                loadingMsg += "..."; // Thêm animation dots đơn giản
            }
            int msgWidth = metrics.stringWidth(loadingMsg);
            g2d.drawString(loadingMsg, (width - msgWidth) / 2, height / 2);
            g2d.dispose();
            return; // Không vẽ gì khác nếu đang loading
        }

        if (jeu != null) {
            Plateau past = jeu.getPast();
            Plateau present = jeu.getPresent();
            Plateau future = jeu.getFuture();

            int boardSize = jeu.getTAILLE();
            int topMargin = 70; // Espace cho statusMessage
            int bottomMarginForButtons = height - dynamicButtonY + 30;
            int availableHeightForBoards = height - topMargin - bottomMarginForButtons;

            int tileWidthByWidth = (width - 80) / (boardSize * 3 + 2 * (boardSize / 2)); // Giảm padding, tăng espacement
            int tileWidthByHeight = availableHeightForBoards / (boardSize + 1);
            int tileWidth = Math.max(15, Math.min(tileWidthByWidth, tileWidthByHeight));

            int spacing = Math.max(10, tileWidth / 2);
            int boardRenderHeight = boardSize * tileWidth;
            int totalBoardAreaWidth = boardSize * tileWidth * 3 + spacing * 2;

            int presentX = (width - totalBoardAreaWidth) / 2 + boardSize * tileWidth + spacing;
            // Đảm bảo offsetY không âm nếu panel quá nhỏ
            int offsetY = topMargin + Math.max(0, (availableHeightForBoards - boardRenderHeight - 20) / 2);

            int pastX = presentX - boardSize * tileWidth - spacing;
            int futureX = presentX + boardSize * tileWidth + spacing;

            // 绘制角色
            // Lemiel (J1) 在左边
            if (lemielImage != null) {
                int lemielHeight = Math.min(height / 2, lemielImage.getHeight() * 2);
                int lemielWidth = lemielHeight * lemielImage.getWidth() / lemielImage.getHeight();
                g2d.drawImage(lemielImage, 10, height / 2 - lemielHeight / 2, lemielWidth, lemielHeight, null);
            }

            // Zarek (J2) 在右边
            if (zarekImage != null) {
                int zarekHeight = Math.min(height / 2, zarekImage.getHeight() * 2);
                int zarekWidth = zarekHeight * zarekImage.getWidth() / zarekImage.getHeight();
                g2d.drawImage(zarekImage, width - zarekWidth - 10, height / 2 - zarekHeight / 2, zarekWidth, zarekHeight, null);
            }

            // 在etapeCoup=3时，高亮显示用户选择的棋盘（如果有）
            if (etapeCoup == 3) {

                // 在etapeCoup=3时，确保"选择棋盘"按钮显示
                if (isMyTurn()) {
                    // 绘制提示文本
                    g2d.setColor(Color.YELLOW);
                    g2d.setFont(new Font("Arial", Font.BOLD, 18));
                    String selectBoardMessage = "Sélectionnez un plateau pour le prochain tour";
                    FontMetrics metrics = g2d.getFontMetrics();
                    int selectMsgWidth = metrics.stringWidth(selectBoardMessage);
                    g2d.drawString(selectBoardMessage, (width - selectMsgWidth) / 2, offsetY - 20);
                }
            }

            // 绘制三个棋盘，突出显示活动棋盘
            int myPlayerId = gameClient != null ? gameClient.getMyPlayerId() : -1;
            int currentPlayerId = jeu.getJoueurCourant() != null ? jeu.getJoueurCourant().getId() : -1;
            
            // 确定当前玩家和对手选择的棋盘
            Plateau.TypePlateau myNextPlateau = (myPlayerId == 1) ? joueur1SelectedPlateau : joueur2SelectedPlateau;
            Plateau.TypePlateau opponentNextPlateau = (myPlayerId == 1) ? joueur2SelectedPlateau : joueur1SelectedPlateau;
            
            System.out.println("我的ID: " + myPlayerId + 
                              ", 当前玩家ID: " + currentPlayerId + 
                              ", 我选择的棋盘: " + myNextPlateau + 
                              ", 对手选择的棋盘: " + opponentNextPlateau);
            
            // 绘制三个棋盘，带有不同的高亮
            drawPlateau(g2d, past, pastX, offsetY, tileWidth, "PASSÉ", null,
                      myNextPlateau == Plateau.TypePlateau.PAST,
                      opponentNextPlateau == Plateau.TypePlateau.PAST);
                      
            drawPlateau(g2d, present, presentX, offsetY, tileWidth, "PRÉSENT", crackPresentImage,
                      myNextPlateau == Plateau.TypePlateau.PRESENT,
                      opponentNextPlateau == Plateau.TypePlateau.PRESENT);
                      
            drawPlateau(g2d, future, futureX, offsetY, tileWidth, "FUTUR", crackFutureImage,
                      myNextPlateau == Plateau.TypePlateau.FUTURE,
                      opponentNextPlateau == Plateau.TypePlateau.FUTURE);
            
            // 在etapeCoup=3时，显示棋盘选择按钮
            if (etapeCoup == 3 && isMyTurn()) {
                // 计算按钮宽度和位置
                int buttonWidth = Math.min(boardSize * tileWidth, 120);
                int buttonHeight = 40;
                int buttonY = offsetY + boardRenderHeight + 10;
                
                // 设置按钮属性
                choosePastButton.setSize(buttonWidth, buttonHeight);
                choosePresentButton.setSize(buttonWidth, buttonHeight);
                chooseFutureButton.setSize(buttonWidth, buttonHeight);
                
                // 设置按钮位置
                choosePastButton.setLocation(pastX + (boardSize * tileWidth - buttonWidth) / 2, buttonY);
                choosePresentButton.setLocation(presentX + (boardSize * tileWidth - buttonWidth) / 2, buttonY);
                chooseFutureButton.setLocation(futureX + (boardSize * tileWidth - buttonWidth) / 2, buttonY);
                
                // 设置按钮颜色
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
                
                // 渲染按钮
                choosePastButton.render(g2d);
                choosePresentButton.render(g2d);
                chooseFutureButton.render(g2d);
                
                // 绘制提示文本
                g2d.setColor(Color.YELLOW);
                g2d.setFont(new Font("Arial", Font.BOLD, 18));
                String selectBoardMessage = "Sélectionnez un plateau pour le prochain tour";
                FontMetrics metrics = g2d.getFontMetrics();
                int selectMsgWidth = metrics.stringWidth(selectBoardMessage);
                g2d.drawString(selectBoardMessage, (width - selectMsgWidth) / 2, offsetY - 20);
            }

            // Status Message
            g2d.setColor(Color.CYAN);
            g2d.setFont(new Font("Consolas", Font.BOLD, 18));
            if (statusMessage != null && !statusMessage.isEmpty()) {
                FontMetrics metrics = g2d.getFontMetrics();
                int msgWidth = metrics.stringWidth(statusMessage);
                g2d.drawString(statusMessage, (width - msgWidth) / 2, 40);
            }

            // Clones Info with Avatars
            g2d.setFont(new Font("Consolas", Font.PLAIN, 15));
            g2d.setColor(Color.LIGHT_GRAY);
            int cloneInfoY = offsetY + boardRenderHeight + 25;
            if (cloneInfoY >= dynamicButtonY - 15) { // Tránh đè lên nút
                cloneInfoY = offsetY - 35; // Đặt phía trên plateau nếu không đủ chỗ
                if (cloneInfoY < 20) {
                    cloneInfoY = 20;
                }
            }

            int avatarSize = 30;

            if (jeu.getJoueur1() != null) {
                if (lemielAvatarImage != null) {
                    g2d.drawImage(lemielAvatarImage, Math.max(10, pastX - 20), cloneInfoY - avatarSize, avatarSize, avatarSize, null);
                }
                String p1Info = "Lemiel (ID " + jeu.getJoueur1().getId() + ") Clones: " + jeu.getJoueur1().getNbClones();
                if (gameClient != null && jeu.getJoueur1().getId() == gameClient.getMyPlayerId()) {
                    p1Info += " (Vous)";
                }
                // 添加棋盘选择信息
                p1Info += " - Plateau: " + joueur1SelectedPlateau;
                g2d.drawString(p1Info, Math.max(10, pastX - 20) + avatarSize + 5, cloneInfoY);
            }

            if (jeu.getJoueur2() != null) {
                String p2Info = "Zarek (ID " + jeu.getJoueur2().getId() + ") Clones: " + jeu.getJoueur2().getNbClones();
                if (gameClient != null && jeu.getJoueur2().getId() == gameClient.getMyPlayerId()) {
                    p2Info += " (Vous)";
                }
                // 添加棋盘选择信息
                p2Info += " - Plateau: " + joueur2SelectedPlateau;
                FontMetrics p2Metrics = g2d.getFontMetrics();
                int p2InfoWidth = p2Metrics.stringWidth(p2Info);
                int p2X = Math.min(width - 10 - p2InfoWidth - avatarSize - 5, futureX + (boardSize * tileWidth) + 20 - p2InfoWidth - avatarSize - 5);

                if (zarekAvatarImage != null) {
                    g2d.drawImage(zarekAvatarImage, p2X + p2InfoWidth + 5, cloneInfoY - avatarSize, avatarSize, avatarSize, null);
                }
                g2d.drawString(p2Info, p2X, cloneInfoY);
            }

            // 渲染按钮
            backButton.render(g2d);

            // 仅在我的回合且不是etapeCoup=3时显示撤销按钮
            if (isMyTurn() && (etapeCoup == 1 || etapeCoup == 2)) {
                undoButton.render(g2d);
            }

        } else { // jeu is null (chưa nhận được trạng thái đầu tiên)
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 22));
            FontMetrics metrics = g2d.getFontMetrics();
            String currentStatus = (statusMessage != null && !statusMessage.isEmpty()) ? statusMessage : "Chargement des données du jeu...";
            int msgWidth = metrics.stringWidth(currentStatus);
            g2d.drawString(currentStatus, (width - msgWidth) / 2, height / 2);
        }

        // Rendu các nút
        backButton.render(g2d);

        // 只有在是自己的回合时才显示这些按钮
        boolean isMyTurn = isMyTurn();
        if (isMyTurn) {
            // 使用GameScene中的etapeCoup字段
            if (etapeCoup == 1 || etapeCoup == 2) {
                undoButton.render(g2d);
            }

        }
        g2d.dispose();
    }

    private void drawPlateau(Graphics2D g, Plateau plateau, int x, int y, int tileWidth, String title, BufferedImage crackImage, boolean isMyNextPlateau, boolean isOpponentNextPlateau) {
        if (plateau == null) {
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.drawString("Plateau " + title + " non disponible", x + 10, y + tileWidth * 2);
            return;
        }

        int boardSize = plateau.getSize();
        int boardPixelSize = boardSize * tileWidth;
        
        // 高亮显示当前玩家选择的下一个棋盘 - 绿色
        if (isMyNextPlateau) {
            g.setColor(new Color(0, 200, 0, 180)); // 绿色半透明
            g.setStroke(new BasicStroke(5f));
            g.drawRect(x - 8, y - 8, boardPixelSize + 16, boardPixelSize + 16);
            g.setStroke(new BasicStroke(1f));
        }
        
        // 高亮显示对手选择的下一个棋盘 - 黄色
        if (isOpponentNextPlateau) {
            g.setColor(new Color(255, 255, 0, 180)); // 黄色半透明
            g.setStroke(new BasicStroke(2f));
            g.drawRect(x - 12, y - 12, boardPixelSize + 24, boardPixelSize + 24);
            g.setStroke(new BasicStroke(1f));
        }

        // Titre
        g.setColor(Color.decode("#AAAAAA")); // Gris clair pour le titre
        g.setFont(new Font("Tahoma", Font.BOLD, Math.max(10, tileWidth / 2 - 2)));
        FontMetrics metrics = g.getFontMetrics();
        int titleWidth = metrics.stringWidth(title);
        g.drawString(title, x + (boardPixelSize - titleWidth) / 2, y - metrics.getDescent() - 5);

        // Bordure
        g.setColor(new Color(80, 80, 80));
        g.drawRect(x - 1, y - 1, boardPixelSize + 1, boardPixelSize + 1);

        // Si une image de fissure est disponible et que ce n'est pas le plateau du passé, dessiner l'image de fond
        if (crackImage != null && !plateau.getType().equals(Plateau.TypePlateau.PAST)) {
            g.drawImage(crackImage, x, y, boardPixelSize, boardPixelSize, null);
        }

        for (int row = 0; row < boardSize; row++) {
            for (int col = 0; col < boardSize; col++) {
                // Fond de la cellule (seul le plateau du passé conserve sa couleur d'origine)
                if (plateau.getType().equals(Plateau.TypePlateau.PAST)) {
                    if ((row + col) % 2 == 0) {
                        g.setColor(new Color(75, 75, 85)); // Plus sombre
                     }else {
                        g.setColor(new Color(75, 75, 85, 180)); // Très sombre

                                        }g.fillRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);
                } else {
                    // Les plateaux présent et futur utilisent des cases semi-transparentes
                    if ((row + col) % 2 == 0) {
                        g.setColor(new Color(75, 75, 85, 180)); // Version semi-transparente
                        g.fillRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);
                    }
                }

                // Grille de la cellule
                g.setColor(new Color(100, 100, 110));
                g.drawRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);

                Piece piece = plateau.getPiece(row, col);
                if (piece != null && piece.getOwner() != null) {
                    int pieceMargin = Math.max(2, tileWidth / 8);
                    int pieceSize = tileWidth - 2 * pieceMargin;
                    int pieceX = x + col * tileWidth + pieceMargin;
                    int pieceY = y + row * tileWidth + pieceMargin;

                    // Utiliser les images d'avatar au lieu de remplir avec une couleur
                    if (piece.getOwner().getId() == 1) { // Joueur 1 (Lemiel)
                        if (lemielAvatarImage != null) {
                            g.drawImage(lemielAvatarImage, pieceX, pieceY, pieceSize, pieceSize, null);
                        } else {
                            // Si l'image n'est pas chargée, utiliser la couleur d'origine
                            g.setColor(new Color(220, 220, 240));
                            g.fillOval(pieceX, pieceY, pieceSize, pieceSize);
                        }
                    } else { // Joueur 2 (Zarek)
                        if (zarekAvatarImage != null) {
                            g.drawImage(zarekAvatarImage, pieceX, pieceY, pieceSize, pieceSize, null);
                        } else {
                            // Si l'image n'est pas chargée, utiliser la couleur d'origine
                            g.setColor(new Color(70, 70, 100));
                            g.fillOval(pieceX, pieceY, pieceSize, pieceSize);
                        }
                    }

                    // Ajouter une bordure autour des pièces pour les mettre en évidence
                    Color borderColor = new Color(100, 100, 180);
                    if (gameClient != null && piece.getOwner().getId() == gameClient.getMyPlayerId()) {
                        borderColor = Color.GREEN; // Bordure verte pour les pièces du joueur
                    }

                    g.setColor(borderColor);
                    g.setStroke(new BasicStroke(Math.max(1.5f, tileWidth / 16f)));
                    g.drawRect(pieceX, pieceY, pieceSize, pieceSize);

                    // Mettre en surbrillance la pièce sélectionnée
                    if (selectedPiecePosition != null
                            && selectedPiecePosition.x == row && selectedPiecePosition.y == col
                            && plateau.getType().equals(selectedPlateauType)) {
                        g.setColor(Color.ORANGE);
                        g.setStroke(new BasicStroke(Math.max(2.5f, tileWidth / 10f)));
                        g.drawRect(pieceX - 2, pieceY - 2, pieceSize + 4, pieceSize + 4);
                    }
                    g.setStroke(new BasicStroke(1f)); // Réinitialiser l'épaisseur des lignes
                }
            }
        }
    }

    private void setupMouseListeners() {
        clearMouseListeners(); // Xóa listeners cũ trước khi thêm mới

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

                // Các nút hành động chỉ được xử lý nếu đến lượt người chơi
                if (isMyTurn()) {
                    // 处理撤销按钮
                    if (undoButton.contains(mousePoint) && (etapeCoup == 1 || etapeCoup == 2)) {
                        undoButton.onClick();
                        return;
                    }

                    // 处理选择棋盘按钮
                    if (choosePastButton.contains(mousePoint) && etapeCoup == 3) {
                        System.out.println("点击了'选择PAST棋盘'按钮");
                        choosePastButton.onClick();
                        return;
                    }
                    
                    if (choosePresentButton.contains(mousePoint) && etapeCoup == 3) {
                        System.out.println("点击了'选择PRESENT棋盘'按钮");
                        choosePresentButton.onClick();
                        return;
                    }
                    
                    if (chooseFutureButton.contains(mousePoint) && etapeCoup == 3) {
                        System.out.println("点击了'选择FUTURE棋盘'按钮");
                        chooseFutureButton.onClick();
                        return;
                    }
                    
                    // 原有的选择棋盘按钮可以不再使用
                    /*
                    if (choosePlateauButton.contains(mousePoint) && etapeCoup == 3) {
                        System.out.println("点击了'选择棋盘'按钮");
                        choosePlateauButton.onClick();
                        return;
                    }
                    */
                }
                handleBoardClick(mousePoint); // Xử lý click trên bàn cờ
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

                // 添加对新按钮的处理
                if (isMyTurn()) {
                    if (undoButton.contains(mousePoint) && (etapeCoup == 1 || etapeCoup == 2)) {
                        undoButton.setClicked(true);
                        needsRepaint = true;
                    }

                    // 选择棋盘的三个按钮
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

        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
        }
    }

    private void clearMouseListeners() {
        if (sceneManager.getPanel() != null) {
            if (mouseAdapterInternal != null) {
                sceneManager.getPanel().removeMouseListener(mouseAdapterInternal);
            }
        }
    }

    // --- Các hàm helper getPlateauFromMousePoint, getBoardCoordinates, repaintPanel, resetSelection, resetSelectionAfterAction ---
    // (Giữ nguyên như phiên bản trước bạn đã có, đã được tích hợp ở trên)
    private Plateau getPlateauFromMousePoint(Point mousePoint) {
        if (jeu == null || sceneManager == null || sceneManager.getPanel() == null) {
            return null;
        }
        int width = sceneManager.getPanel().getWidth();
        int height = sceneManager.getPanel().getHeight();
        int dynamicButtonY = height - 70; // Đồng bộ với render
        if (dynamicButtonY < 450) {
            dynamicButtonY = 450;
        }

        int boardSize = jeu.getTAILLE();
        int topMargin = 70;
        int bottomMarginForButtons = height - dynamicButtonY + 30;
        int availableHeightForBoards = height - topMargin - bottomMarginForButtons;
        int tileWidthByWidth = (width - 80) / (boardSize * 3 + 2 * (boardSize / 2));
        int tileWidthByHeight = availableHeightForBoards / (boardSize + 1);
        int tileWidth = Math.max(15, Math.min(tileWidthByWidth, tileWidthByHeight));

        int spacing = Math.max(10, tileWidth / 2);
        int boardRenderHeight = boardSize * tileWidth;
        int totalBoardAreaWidth = boardSize * tileWidth * 3 + spacing * 2;
        int presentX = (width - totalBoardAreaWidth) / 2 + boardSize * tileWidth + spacing;
        int offsetY = topMargin + Math.max(0, (availableHeightForBoards - boardRenderHeight - 20) / 2);
        int pastX = presentX - boardSize * tileWidth - spacing;
        int futureX = presentX + boardSize * tileWidth + spacing;
        int boardPixelWidth = boardSize * tileWidth;

        if (mousePoint.y >= offsetY && mousePoint.y < offsetY + boardRenderHeight) {
            if (mousePoint.x >= pastX && mousePoint.x < pastX + boardPixelWidth) {
                return jeu.getPast();
            }
            if (mousePoint.x >= presentX && mousePoint.x < presentX + boardPixelWidth) {
                return jeu.getPresent();
            }
            if (mousePoint.x >= futureX && mousePoint.x < futureX + boardPixelWidth) {
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

        int width = sceneManager.getPanel().getWidth();
        int height = sceneManager.getPanel().getHeight();
        int dynamicButtonY = height - 70; // Synced with render
        if (dynamicButtonY < 450) {
            dynamicButtonY = 450;
        }

        int boardSize = jeu.getTAILLE();
        int topMargin = 70;
        int bottomMarginForButtons = height - dynamicButtonY + 30;
        int availableHeightForBoards = height - topMargin - bottomMarginForButtons;
        int tileWidthByWidth = (width - 80) / (boardSize * 3 + 2 * (boardSize / 2));
        int tileWidthByHeight = availableHeightForBoards / (boardSize + 1);
        int tileWidth = Math.max(15, Math.min(tileWidthByWidth, tileWidthByHeight));

        if (tileWidth == 0) {
            return null; // Eviter division par zéro
        }
        int spacing = Math.max(10, tileWidth / 2);
        int boardRenderHeight = boardSize * tileWidth;
        int totalBoardAreaWidth = boardSize * tileWidth * 3 + spacing * 2;
        int presentX = (width - totalBoardAreaWidth) / 2 + boardSize * tileWidth + spacing;
        int offsetY = topMargin + Math.max(0, (availableHeightForBoards - boardRenderHeight - 20) / 2);
        int pastX = presentX - boardSize * tileWidth - spacing;
        int futureX = presentX + boardSize * tileWidth + spacing;

        int boardXStart;
        switch (clickedPlateau.getType()) {
            case PAST:
                boardXStart = pastX;
                break;
            case PRESENT:
                boardXStart = presentX;
                break;
            case FUTURE:
                boardXStart = futureX;
                break;
            default:
                return null;
        }

        int col = (mousePoint.x - boardXStart) / tileWidth;
        int row = (mousePoint.y - offsetY) / tileWidth;

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
        // statusMessage est généralement mis à jour par la logique appelant cette fonction ou par onGameStateUpdate
    }

    private void updateStatusFromCurrentGame(boolean fromServerUpdate) {
        if (this.jeu != null && this.jeu.getJoueurCourant() != null && gameClient != null) {
            if (this.jeu.getJoueurCourant().getId() == gameClient.getMyPlayerId()) {
                String playerName = this.jeu.getJoueurCourant().getId() == 1 ? "Lemiel" : "Zarek";

                // 根据etapeCoup值更新状态消息
                if (etapeCoup == 3) {
                    // 在etapeCoup=3时显示选择棋盘的提示
                    this.statusMessage = "Sélectionnez un plateau pour le prochain tour (PAST, PRESENT ou FUTURE)";
                } else if (etapeCoup == 0) {
                    this.statusMessage = "C'est VOTRE tour (" + playerName + "). Sélectionnez un pion pour commencer.";
                } else if (etapeCoup == 1) {
                    this.statusMessage = "Premier mouvement: déplacez votre pion ou effectuez une action spéciale.";
                } else if (etapeCoup == 2) {
                    this.statusMessage = "Second mouvement: déplacez à nouveau votre pion ou effectuez une action spéciale.";
                } else {
                    this.statusMessage = "C'est VOTRE tour (" + playerName + " - Joueur " + gameClient.getMyPlayerId() + ")";
                }
            } else {
                String opponentName = this.jeu.getJoueurCourant().getId() == 1 ? "Lemiel" : "Zarek";
                this.statusMessage = "Tour de l'adversaire : " + opponentName + " (ID " + this.jeu.getJoueurCourant().getId() + ")";
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

        // 从newGameState中提取etapeCoup
        if (newGameState != null) {
            int newEtapeCoup = newGameState.getEtapeCoup();
            System.out.println("GameScene : etapeCoup from server = " + newEtapeCoup + ", current etapeCoup = " + this.etapeCoup);

            // 只有当新的etapeCoup值与当前值不同时才更新
            if (this.etapeCoup != newEtapeCoup) {
                this.etapeCoup = newEtapeCoup;
                System.out.println("GameScene : Mise à jour de etapeCoup: " + this.etapeCoup);
            }
            
            // 更新玩家选择的棋盘信息
            if (newGameState.getJoueur1() != null && newGameState.getJoueur1().getProchainPlateau() != null) {
                this.joueur1SelectedPlateau = newGameState.getJoueur1().getProchainPlateau();
                System.out.println("GameScene : Joueur 1 a sélectionné le plateau: " + this.joueur1SelectedPlateau);
            }
            
            if (newGameState.getJoueur2() != null && newGameState.getJoueur2().getProchainPlateau() != null) {
                this.joueur2SelectedPlateau = newGameState.getJoueur2().getProchainPlateau();
                System.out.println("GameScene : Joueur 2 a sélectionné le plateau: " + this.joueur2SelectedPlateau);
            }
            
            // 根据当前玩家设置activePlateau
            if (newGameState.getJoueurCourant() != null) {
                if (newGameState.getJoueurCourant().getId() == 1) {
                    this.activePlateau = this.joueur1SelectedPlateau;
                } else {
                    this.activePlateau = this.joueur2SelectedPlateau;
                }
                System.out.println("GameScene : Plateau actif mis à jour: " + this.activePlateau);
            }
            
            // 如果所需的activePlateau在游戏状态中设置了，则使用它
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
        
        // 关键修改：在etapeCoup为1或2时保留选择的棋子信息
        if (this.etapeCoup != 1 && this.etapeCoup != 2) {
            // 只有在非移动阶段才重置选择
            resetSelectionAfterAction();
        }

        // Assurer la mise à jour de l'interface
        SwingUtilities.invokeLater(this::repaintPanel);
    }

    @Override
    public void onGameMessage(String messageType, String messageContent) {
        // 处理从GameClient接收的消息，已在EDT线程上
        isLoading = false; // 收到服务器消息表明初始加载已完成

        System.out.println("GameScene: Message reçu: " + messageType + " -> " + messageContent);

        // 现在etapeCoup通过onGameStateUpdate传递，不需要在这里处理
        switch (messageType) {
            case "PIECE":
                // 处理棋子选择成功的消息
                // 格式：x:y;possibleMoves
                // possibleMoves格式：TYPE_COUP:x:y;TYPE_COUP:x:y;...
                String[] parts = messageContent.split(";", 2); // 最多分割为2部分：坐标和可能的移动

                if (parts.length > 0) {
                    try {
                        // 解析坐标部分
                        String[] coords = parts[0].split(":");
                        if (coords.length < 2) {
                            statusMessage = "Erreur de format dans la réponse du serveur.";
                            break;
                        }

                        int x = Integer.parseInt(coords[0]);
                        int y = Integer.parseInt(coords[1]);

                        // 解析可能的移动部分
                        String possibleMovesStr = parts.length > 1 ? parts[1] : "";

                        System.out.println("GameScene: Pièce sélectionnée à " + x + "," + y + " avec mouvements possibles: " + possibleMovesStr);

                        // 收到PIECE消息表示进入etapeCoup=1
                        this.etapeCoup = 1;

                        // 更新UI显示可能的移动
                        statusMessage = "Pion sélectionné (" + x + "," + y + ") sur plateau " + selectedPlateauType + ". Choisissez une destination sur ce plateau.";

                        // 更新选中的棋子位置
                        selectedPiecePosition = new Point(x, y);

                        // 确保这里不会丢失selectedPlateauType
                        System.out.println("GameScene: Plateau sélectionné sauvegardé: " + selectedPlateauType);
                        
                        // 同步更新activePlateau，确保它反映当前使用的棋盘
                        this.activePlateau = selectedPlateauType;
                        System.out.println("GameScene: activePlateau更新为: " + this.activePlateau);
                    } catch (NumberFormatException e) {
                        statusMessage = "Erreur de format dans la réponse du serveur.";
                    }
                } else {
                    statusMessage = "Message PIECE vide ou incorrect.";
                }
                break;

            case "COUP":
                // 处理移动成功的消息
                // 格式：TYPE_COUP:success:newX:newY:newPlateauType
                String[] coupParts = messageContent.split(":");
                if (coupParts.length >= 3) { // 修改为至少3部分，因为我们需要添加新坐标信息
                    String typeCoup = coupParts[0];
                    
                    // 检查服务器是否返回了新的棋子位置和棋盘
                    if (coupParts.length >= 5) {
                        try {
                            int newX = Integer.parseInt(coupParts[2]);
                            int newY = Integer.parseInt(coupParts[3]);
                            Plateau.TypePlateau newPlateauType = Plateau.TypePlateau.valueOf(coupParts[4]);
                            
                            // 更新selectedPiecePosition和selectedPlateauType
                            selectedPiecePosition = new Point(newX, newY);
                            selectedPlateauType = newPlateauType;
                            this.activePlateau = newPlateauType;
                            
                            System.out.println("GameScene: 更新棋子位置 - 从 " + 
                                               (selectedPiecePosition != null ? selectedPiecePosition.x + "," + selectedPiecePosition.y : "null") + 
                                               " 到 " + newX + "," + newY + 
                                               ", 棋盘从 " + selectedPlateauType + " 到 " + newPlateauType);
                        } catch (Exception e) {
                            System.err.println("GameScene: 解析新棋子位置失败: " + e.getMessage());
                        }
                    }

                    // 检查是否是JUMP或CLONE操作，更新activePlateau
                    if ("JUMP".equals(typeCoup)) {
                        // 根据当前selectedPlateauType计算新的activePlateau
                        if (selectedPlateauType == Plateau.TypePlateau.PAST) {
                            this.activePlateau = Plateau.TypePlateau.PRESENT;
                            System.out.println("GameScene: JUMP操作，activePlateau从PAST更新为PRESENT");
                        } else if (selectedPlateauType == Plateau.TypePlateau.PRESENT) {
                            this.activePlateau = Plateau.TypePlateau.FUTURE;
                            System.out.println("GameScene: JUMP操作，activePlateau从PRESENT更新为FUTURE");
                        }
                        // 更新selectedPlateauType以跟踪当前棋盘
                        selectedPlateauType = this.activePlateau;
                    } else if ("CLONE".equals(typeCoup)) {
                        // 根据当前selectedPlateauType计算新的activePlateau
                        if (selectedPlateauType == Plateau.TypePlateau.PRESENT) {
                            this.activePlateau = Plateau.TypePlateau.PAST;
                            System.out.println("GameScene: CLONE操作，activePlateau从PRESENT更新为PAST");
                        } else if (selectedPlateauType == Plateau.TypePlateau.FUTURE) {
                            this.activePlateau = Plateau.TypePlateau.PRESENT;
                            System.out.println("GameScene: CLONE操作，activePlateau从FUTURE更新为PRESENT");
                        }
                        // 更新selectedPlateauType以跟踪当前棋盘
                        selectedPlateauType = this.activePlateau;
                    }

                    // 收到COUP消息，根据当前etapeCoup决定下一步
                    if (this.etapeCoup == 1) {
                        this.etapeCoup = 2;
                        statusMessage = "Premier déplacement effectué. Choisissez votre seconde action.";
                    } else if (this.etapeCoup == 2) {
                        this.etapeCoup = 3;
                        statusMessage = "Coup " + typeCoup + " effectué avec succès. Sélectionnez le plateau pour le prochain tour.";
                    } else {
                        statusMessage = "Coup " + typeCoup + " effectué avec succès.";
                    }
                }
                break;

            case "PLATEAU":
                // 处理选择棋盘成功的消息
                // 格式：TYPE_PLATEAU:success
                String[] plateauParts = messageContent.split(":");
                if (plateauParts.length >= 1) {
                    String typePlateau = plateauParts[0];

                    // 收到PLATEAU消息，表示结束回合
                    this.etapeCoup = 0;

                    statusMessage = "Plateau " + typePlateau + " sélectionné pour le prochain tour.";
                }
                break;

            case "ADVERSAIRE":
                // 处理服务器错误或轮次信息
                // 修改消息中玩家ID为角色名称
                String modifiedContent = messageContent;
                if (messageContent.contains("Joueur 1")) {
                    modifiedContent = messageContent.replace("Joueur 1", "Lemiel");
                } else if (messageContent.contains("Joueur 2")) {
                    modifiedContent = messageContent.replace("Joueur 2", "Zarek");
                }

                // 如果是撤销操作的响应，重置etapeCoup为0
                if (messageContent.contains("annulation") || messageContent.contains("Undo")) {
                    this.etapeCoup = 0;
                    System.out.println("GameScene: Retour à etapeCoup 0 après Undo");
                }

                statusMessage = modifiedContent;
                break;

            case "WIN":
            case "LOSE":
            case "GAGNE":
            case "PERDU":
                gameHasEnded = true;
                String dialogTitle = ("WIN".equals(messageType) || "GAGNE".equals(messageType)) ? "FÉLICITATIONS !" : "DOMMAGE !";
                // 修改消息内容
                String victoryContent = messageContent;
                if (messageContent.contains("Joueur 1")) {
                    victoryContent = messageContent.replace("Joueur 1", "Lemiel");
                } else if (messageContent.contains("Joueur 2")) {
                    victoryContent = messageContent.replace("Joueur 2", "Zarek");
                }
                statusMessage = victoryContent;
                JOptionPane.showMessageDialog(sceneManager.getPanel(), victoryContent, dialogTitle, JOptionPane.INFORMATION_MESSAGE);
                break;

            case "ERROR":
                statusMessage = "Erreur: " + messageContent;
                JOptionPane.showMessageDialog(sceneManager.getPanel(), messageContent, "ERREUR", JOptionPane.ERROR_MESSAGE);
                break;

            case "DISCONNECTED":
                gameHasEnded = true;
                statusMessage = "Déconnecté: " + messageContent;
                JOptionPane.showMessageDialog(sceneManager.getPanel(), messageContent, "DÉCONNECTÉ", JOptionPane.WARNING_MESSAGE);
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

    // 添加处理选择棋盘的方法
    private void handleChoosePlateauAction() {
        if (jeu == null || gameClient == null || !gameClient.isConnected() || gameHasEnded) {
            return;
        }

        if (!isMyTurn()) {
            statusMessage = "Ce n'est pas votre tour.";
            repaintPanel();
            return;
        }

        // 仅在etapeCoup为3时有效
        if (etapeCoup == 3) {
            // 获取当前选择的棋盘
            Plateau.TypePlateau plateauToSelect = null;

            // 如果没有特定选择，使用默认棋盘（PAST）
            if (selectedPlateauType == null) {
                plateauToSelect = Plateau.TypePlateau.PAST;
                System.out.println("GameScene: Pas de plateau sélectionné, utilisation de PAST par défaut");
            } else {
                plateauToSelect = selectedPlateauType;
                System.out.println("GameScene: Plateau sélectionné: " + plateauToSelect);
            }

            // 发送选择棋盘的命令
            String command = "0:" + plateauToSelect.name() + ":" + (selectedPlateauType != null ? selectedPlateauType.name() : "PAST") + ":0:0";
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

    // 添加三个棋盘选择方法
    private void handleChoosePastAction() {
        if (etapeCoup == 3 && isMyTurn()) {
            selectedPlateauType = Plateau.TypePlateau.PAST;
            String command = "0:" + selectedPlateauType.name() + ":" + selectedPlateauType.name() + ":0:0";
            gameClient.sendPlayerAction(command);
            statusMessage = "Plateau PAST sélectionné pour le prochain tour.";
            System.out.println("GameScene: Sélection du plateau PAST pour le prochain tour");
        }
    }
    
    private void handleChoosePresentAction() {
        if (etapeCoup == 3 && isMyTurn()) {
            selectedPlateauType = Plateau.TypePlateau.PRESENT;
            String command = "0:" + selectedPlateauType.name() + ":" + selectedPlateauType.name() + ":0:0";
            gameClient.sendPlayerAction(command);
            statusMessage = "Plateau PRESENT sélectionné pour le prochain tour.";
            System.out.println("GameScene: Sélection du plateau PRESENT pour le prochain tour");
        }
    }
    
    private void handleChooseFutureAction() {
        if (etapeCoup == 3 && isMyTurn()) {
            selectedPlateauType = Plateau.TypePlateau.FUTURE;
            String command = "0:" + selectedPlateauType.name() + ":" + selectedPlateauType.name() + ":0:0";
            gameClient.sendPlayerAction(command);
            statusMessage = "Plateau FUTURE sélectionné pour le prochain tour.";
            System.out.println("GameScene: Sélection du plateau FUTURE pour le prochain tour");
        }
    }
}
