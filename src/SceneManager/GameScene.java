package SceneManager;

import Modele.Coup;
import Modele.Jeu;
import Modele.Piece;
import Modele.Plateau;
import Network.AIClient;
import Network.GameClient;
import Network.GameServerManager;
import Network.GameStateUpdateListener;
import java.awt.*; // Đảm bảo bạn đã tạo lớp này trong package AI
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage; // Sử dụng SwingWorker cho các tác vụ nền
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities; // Cần cho mouseMoved
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
    private Coup.TypeCoup nextActionType = null; // Chủ yếu cho UI biết đang chờ click đích cho MOVE

    // UI Buttons
    private Button backButton;
    private Button upButton;
    private Button downButton;
    private Button leftButton;
    private Button rightButton;
    private Button jumpButton;
    private Button cloneButton;

    // Network and Game Mode
    private GameClient gameClient;
    private String serverIpToConnectOnDemand; // IP để client kết nối (nếu không phải host/single)
    private boolean isOperatingInSinglePlayerMode; // True nếu đây là chế độ Single Player tự host

    private String statusMessage = "Initialisation...";
    private volatile boolean gameHasEnded = false; // volatile vì có thể được cập nhật từ luồng khác (onGameMessage)
    private volatile boolean isLoading = false; // Để hiển thị trạng thái loading

    // Server và AI cục bộ cho chế độ chơi đơn
    // Static để đảm bảo chỉ có một instance nếu GameScene được tạo lại nhanh chóng (dù dispose nên xử lý)
    private static GameServerManager localSinglePlayerServerManager;
    private static Thread localAIClientThread;
    private static AIClient aiClientInstance; // Giữ instance AI để có thể disconnect

    private MouseAdapter mouseAdapterInternal;
    // MouseMotionListener được gộp vào MouseAdapter nếu mouseAdapterInternal kế thừa MouseAdapter và implement MouseMotionListener
    // Hoặc tạo một biến riêng cho MouseMotionListener

    // Ajout pour la gestion du serveur en mode multijoueur
    private GameServerManager hostServerManager; // Instance du serveur reprise de HostingScene

    // Constructor cho Single Player (tự host server và AI)
    public GameScene(SceneManager sceneManager, boolean isSinglePlayer) {
        this.sceneManager = sceneManager;
        this.isOperatingInSinglePlayerMode = isSinglePlayer;
        if (isSinglePlayer) {
            this.serverIpToConnectOnDemand = "127.0.0.1"; // UI client kết nối vào server local
            this.statusMessage = "Mode Solo: Préparation...";
        } else {
            // Constructor này chỉ nên được gọi với isSinglePlayer = true
            throw new IllegalArgumentException("Pour le mode multijoueur client, utilisez le constructeur avec server IP.");
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
            System.out.println("GameScene: Initialisation avec client hôte (ID: " + this.gameClient.getMyPlayerId() + 
                (this.hostServerManager != null ? ", avec serveur hôte" : ", sans serveur hôte"));
            
            this.gameClient.setListener(this); // Définit cette scène comme écouteur
            this.jeu = this.gameClient.getGameInstance(); // Obtient l'état initial du jeu
            
            if (this.jeu == null) {
                System.out.println("GameScene: Instance de jeu est null, attente de mise à jour du serveur");
                this.statusMessage = "En attente de l'état du jeu...";
            } else {
                System.out.println("GameScene: Instance de jeu reçue (Joueur courant: " + 
                    (this.jeu.getJoueurCourant() != null ? this.jeu.getJoueurCourant().getId() : "non défini") + ")");
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
        
        // 四个方向按钮
        upButton = new Button(0, 0, 80, 40, "UP", () -> handleDirectionMove(Coup.TypeCoup.UP));
        downButton = new Button(0, 0, 80, 40, "DOWN", () -> handleDirectionMove(Coup.TypeCoup.DOWN));
        leftButton = new Button(0, 0, 80, 40, "LEFT", () -> handleDirectionMove(Coup.TypeCoup.LEFT));
        rightButton = new Button(0, 0, 80, 40, "RIGHT", () -> handleDirectionMove(Coup.TypeCoup.RIGHT));
        
        // 重新添加JUMP和CLONE按钮
        jumpButton = new Button(0, 0, 100, 40, "JUMP", () -> handleActionCommand(Coup.TypeCoup.JUMP));
        cloneButton = new Button(0, 0, 100, 40, "CLONE", () -> handleActionCommand(Coup.TypeCoup.CLONE));
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
                if(aiClientInstance != null) aiClientInstance.disconnect(); // Demande à l'IA de se déconnecter
                localAIClientThread.interrupt(); // Interrompt le thread de l'IA
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
                    System.out.println("GameScene (Solo): Serveur local solo déjà actif, tentative de réutilisation ou redémarrage.");
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
                    if(aiClientInstance != null) aiClientInstance.disconnect();
                    localAIClientThread.interrupt();
                }

                aiClientInstance = new AIClient("127.0.0.1");
                aiClientInstance.connect(); // AI kết nối
                if(aiClientInstance.isConnected()){
                    aiClientInstance.startListeningAndPlaying(); // Bắt đầu luồng của AI
                    publish("AI (ID: " + aiClientInstance.getMyPlayerId() +") connectée et à l'écoute.");
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
                    publish("Attente que le serveur démarre le moteur (" +
                            (localSinglePlayerServerManager.areAllPlayersConnected() ? "OK" : "Pas encore assez de joueurs") + ")");
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
            System.out.println("isMyTurn: false, jeu.getJoueurCourant(): " + 
                (jeu.getJoueurCourant() == null ? "null" : jeu.getJoueurCourant()) + 
                ", gameClient: " + gameClient + 
                ", gameClient.getMyPlayerId(): " + (gameClient != null ? gameClient.getMyPlayerId() : -1));
            return false;
        }
        
        boolean isMyTurn = jeu.getJoueurCourant().getId() == gameClient.getMyPlayerId();
        // System.out.println("isMyTurn: " + isMyTurn + 
        //     ", jeu.getJoueurCourant().getId(): " + jeu.getJoueurCourant().getId() + 
        //     ", gameClient.getMyPlayerId(): " + gameClient.getMyPlayerId());
        return isMyTurn;
    }

    private void handleActionCommand(Coup.TypeCoup actionType) {
        if (selectedPiecePosition == null || selectedPlateauType == null || gameHasEnded || gameClient == null || !gameClient.isConnected()) {
            statusMessage = "Sélectionnez un pion d'abord ou action non permise.";
            repaintPanel();
            return;
        }

        // 直接构造命令发送到服务器，不在客户端验证
        String command = actionType.name() + ":" + selectedPlateauType.name() + ":" +
                         selectedPiecePosition.x + ":" + selectedPiecePosition.y;

        gameClient.sendPlayerAction(command);
        statusMessage = "Commande " + actionType.name() + " envoyée...";
        resetSelectionAfterAction(); // 发送命令后重置UI
        repaintPanel();
    }

    // 添加一个新的方法处理方向移动
    private void handleDirectionMove(Coup.TypeCoup direction) {
        if (selectedPiecePosition == null || selectedPlateauType == null || gameHasEnded || gameClient == null || !gameClient.isConnected()) {
            statusMessage = "Sélectionnez un pion d'abord ou action non permise.";
            repaintPanel();
            return;
        }
        
        // 直接构造命令发送到服务器，不在客户端验证
        String command = direction.name() + ":" + selectedPlateauType.name() + ":" +
                        selectedPiecePosition.x + ":" + selectedPiecePosition.y;
        
        gameClient.sendPlayerAction(command);
        statusMessage = "Déplacement " + direction.name() + " envoyé...";
        resetSelectionAfterAction();
        repaintPanel();
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
                    upButton.update(mousePos);
                    downButton.update(mousePos);
                    leftButton.update(mousePos);
                    rightButton.update(mousePos);
                    jumpButton.update(mousePos);
                    cloneButton.update(mousePos);
                } else {
                    // Si ce n'est pas notre tour, s'assurer que les boutons ne sont pas en survol
                    upButton.update(new Point(-1, -1));
                    downButton.update(new Point(-1, -1));
                    leftButton.update(new Point(-1, -1));
                    rightButton.update(new Point(-1, -1));
                    jumpButton.update(new Point(-1, -1));
                    cloneButton.update(new Point(-1, -1));
                }
                
                // Le repaint à chaque mouvement de souris est nécessaire pour l'effet de survol
                repaintPanel();
            }
        }
    }

    @Override
    public void render(Graphics g, int width, int height) {
        // Tính toán vị trí nút dựa trên kích thước hiện tại của panel
        int dynamicButtonY = height - 70;
        if (dynamicButtonY < 450) dynamicButtonY = 450; // Vị trí Y tối thiểu
        int buttonCommonHeight = Math.max(40, height/18);
        int backButtonWidth = Math.max(130, width/7);
        int actionButtonWidth = Math.max(90, width/9);

        backButton.setSize(backButtonWidth, buttonCommonHeight);
        backButton.setLocation(30, dynamicButtonY);

        int actionButtonXStart = backButton.getX() + backButton.getWidth() + 20;
        upButton.setSize(actionButtonWidth, buttonCommonHeight);
        upButton.setLocation(actionButtonXStart, dynamicButtonY - buttonCommonHeight - 10);

        downButton.setSize(actionButtonWidth, buttonCommonHeight);
        downButton.setLocation(actionButtonXStart + actionButtonWidth + 10, dynamicButtonY - buttonCommonHeight - 10);

        leftButton.setSize(actionButtonWidth, buttonCommonHeight);
        leftButton.setLocation(actionButtonXStart + (actionButtonWidth + 10) * 2, dynamicButtonY - buttonCommonHeight - 10);

        rightButton.setSize(actionButtonWidth, buttonCommonHeight);
        rightButton.setLocation(actionButtonXStart + (actionButtonWidth + 10) * 3, dynamicButtonY - buttonCommonHeight - 10);

        jumpButton.setSize(actionButtonWidth, buttonCommonHeight);
        jumpButton.setLocation(actionButtonXStart + actionButtonWidth/2, dynamicButtonY);

        cloneButton.setSize(actionButtonWidth, buttonCommonHeight);
        cloneButton.setLocation(actionButtonXStart + actionButtonWidth/2 + actionButtonWidth + 20, dynamicButtonY);

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
                int lemielHeight = Math.min(height/2, lemielImage.getHeight()*2);
                int lemielWidth = lemielHeight * lemielImage.getWidth() / lemielImage.getHeight();
                g2d.drawImage(lemielImage, 10, height/2 - lemielHeight/2, lemielWidth, lemielHeight, null);
            }
            
            // Zarek (J2) 在右边
            if (zarekImage != null) {
                int zarekHeight = Math.min(height/2, zarekImage.getHeight()*2);
                int zarekWidth = zarekHeight * zarekImage.getWidth() / zarekImage.getHeight();
                g2d.drawImage(zarekImage, width - zarekWidth - 10, height/2 - zarekHeight/2, zarekWidth, zarekHeight, null);
            }

            drawPlateau(g2d, past, pastX, offsetY, tileWidth, "PASSÉ", null);
            drawPlateau(g2d, present, presentX, offsetY, tileWidth, "PRÉSENT", crackPresentImage);
            drawPlateau(g2d, future, futureX, offsetY, tileWidth, "FUTUR", crackFutureImage);

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
                if (cloneInfoY < 20) cloneInfoY = 20;
            }

            int avatarSize = 30;
            
            if (jeu.getJoueur1() != null) {
                if (lemielAvatarImage != null) {
                    g2d.drawImage(lemielAvatarImage, Math.max(10, pastX - 20), cloneInfoY - avatarSize, avatarSize, avatarSize, null);
                }
                String p1Info = "Lemiel (ID " + jeu.getJoueur1().getId() + ") Clones: " + jeu.getJoueur1().getNbClones();
                if (gameClient != null && jeu.getJoueur1().getId() == gameClient.getMyPlayerId()) p1Info += " (Vous)";
                g2d.drawString(p1Info, Math.max(10, pastX - 20) + avatarSize + 5, cloneInfoY);
            }
            
            if (jeu.getJoueur2() != null) {
                String p2Info = "Zarek (ID " + jeu.getJoueur2().getId() + ") Clones: " + jeu.getJoueur2().getNbClones();
                if (gameClient != null && jeu.getJoueur2().getId() == gameClient.getMyPlayerId()) p2Info += " (Vous)";
                FontMetrics p2Metrics = g2d.getFontMetrics();
                int p2InfoWidth = p2Metrics.stringWidth(p2Info);
                int p2X = Math.min(width - 10 - p2InfoWidth - avatarSize - 5, futureX + (boardSize * tileWidth) + 20 - p2InfoWidth - avatarSize - 5);
                
                if (zarekAvatarImage != null) {
                    g2d.drawImage(zarekAvatarImage, p2X + p2InfoWidth + 5, cloneInfoY - avatarSize, avatarSize, avatarSize, null);
                }
                g2d.drawString(p2Info, p2X, cloneInfoY);
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
        // Chỉ hiển thị nút action nếu đến lượt người chơi VÀ game chưa kết thúc
        if (isMyTurn() && !gameHasEnded) {
            upButton.render(g2d);
            downButton.render(g2d);
            leftButton.render(g2d);
            rightButton.render(g2d);
            jumpButton.render(g2d);
            cloneButton.render(g2d);
        }
        g2d.dispose();
    }

    private void drawPlateau(Graphics2D g, Plateau plateau, int x, int y, int tileWidth, String title, BufferedImage crackImage) {
        if (plateau == null) {
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 12));
            g.drawString("Plateau " + title + " non disponible", x + 10, y + tileWidth * 2);
            return;
        }

        int boardSize = plateau.getSize();
        int boardPixelSize = boardSize * tileWidth;

        // Title
        g.setColor(Color.decode("#AAAAAA")); // Gris clair pour le titre
        g.setFont(new Font("Tahoma", Font.BOLD, Math.max(10, tileWidth / 2 - 2)));
        FontMetrics metrics = g.getFontMetrics();
        int titleWidth = metrics.stringWidth(title);
        g.drawString(title, x + (boardPixelSize - titleWidth) / 2, y - metrics.getDescent() - 5);

        // Border
        g.setColor(new Color(80, 80, 80));
        g.drawRect(x - 1, y - 1, boardPixelSize + 1, boardPixelSize + 1);

        // 如果有裂缝图像且不是past棋盘，绘制裂缝背景
        if (crackImage != null && !plateau.getType().equals(Plateau.TypePlateau.PAST)) {
            g.drawImage(crackImage, x, y, boardPixelSize, boardPixelSize, null);
        }

        for (int row = 0; row < boardSize; row++) {
            for (int col = 0; col < boardSize; col++) {
                // Cell background (只有past棋盘保持原来的颜色)
                if (plateau.getType().equals(Plateau.TypePlateau.PAST)) {
                    if ((row + col) % 2 == 0) g.setColor(new Color(75, 75, 85)); // Darker
                    else g.setColor(new Color(75, 75, 85, 180)); // Darkest
                    g.fillRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);
                } else {
                    // Present和Future棋盘使用半透明格子
                    if ((row + col) % 2 == 0) {
                        g.setColor(new Color(75, 75, 85, 180)); // 半透明版本
                        g.fillRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);
                    }
                }
                
                // Cell grid
                g.setColor(new Color(100, 100, 110));
                g.drawRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);

                Piece piece = plateau.getPiece(row, col);
                if (piece != null && piece.getOwner() != null) {
                    int pieceMargin = Math.max(2, tileWidth / 8);
                    int pieceSize = tileWidth - 2 * pieceMargin;
                    int pieceX = x + col * tileWidth + pieceMargin;
                    int pieceY = y + row * tileWidth + pieceMargin;
                    
                    // 使用头像图片而不是颜色填充
                    if (piece.getOwner().getId() == 1) { // Joueur 1 (Lemiel)
                        if (lemielAvatarImage != null) {
                            g.drawImage(lemielAvatarImage, pieceX, pieceY, pieceSize, pieceSize, null);
                        } else {
                            // 如果图片加载失败，使用原来的颜色填充
                            g.setColor(new Color(220, 220, 240));
                            g.fillOval(pieceX, pieceY, pieceSize, pieceSize);
                        }
                    } else { // Joueur 2 (Zarek)
                        if (zarekAvatarImage != null) {
                            g.drawImage(zarekAvatarImage, pieceX, pieceY, pieceSize, pieceSize, null);
                        } else {
                            // 如果图片加载失败，使用原来的颜色填充
                            g.setColor(new Color(70, 70, 100));
                            g.fillOval(pieceX, pieceY, pieceSize, pieceSize);
                        }
                    }
                    
                    // 添加棋子边框，突出显示当前棋子
                    Color borderColor = new Color(100, 100, 180);
                    if (gameClient != null && piece.getOwner().getId() == gameClient.getMyPlayerId()){
                        borderColor = Color.GREEN; // 我方棋子绿色边框
                    }
                    
                    g.setColor(borderColor);
                    g.setStroke(new BasicStroke(Math.max(1.5f, tileWidth / 16f)));
                    g.drawRect(pieceX, pieceY, pieceSize, pieceSize);

                    // 高亮选中的棋子
                    if (selectedPiecePosition != null &&
                        selectedPiecePosition.x == row && selectedPiecePosition.y == col &&
                        plateau.getType().equals(selectedPlateauType)) {
                        g.setColor(Color.ORANGE);
                        g.setStroke(new BasicStroke(Math.max(2.5f, tileWidth / 10f)));
                        g.drawRect(pieceX - 2, pieceY - 2, pieceSize + 4, pieceSize + 4);
                    }
                    g.setStroke(new BasicStroke(1f)); // 重置线条宽度
                }
            }
        }
    }

    private void setupMouseListeners() {
        clearMouseListeners(); // Xóa listeners cũ trước khi thêm mới

        mouseAdapterInternal = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isLoading || gameHasEnded || gameClient == null || !gameClient.isConnected()) return;

                Point mousePoint = e.getPoint();

                if (backButton.contains(mousePoint)) { backButton.onClick(); return; }

                // Các nút hành động chỉ được xử lý nếu đến lượt người chơi
                if (isMyTurn()) {
                    if (upButton.contains(mousePoint)) { upButton.onClick(); return; }
                    if (downButton.contains(mousePoint)) { downButton.onClick(); return; }
                    if (leftButton.contains(mousePoint)) { leftButton.onClick(); return; }
                    if (rightButton.contains(mousePoint)) { rightButton.onClick(); return; }
                    if (jumpButton.contains(mousePoint)) { jumpButton.onClick(); return; }
                    if (cloneButton.contains(mousePoint)) { cloneButton.onClick(); return; }
                }
                handleBoardClick(mousePoint); // Xử lý click trên bàn cờ
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isLoading || gameHasEnded) return;
                Point mousePoint = e.getPoint();
                
                boolean needsRepaint = false;
                
                if (backButton.contains(mousePoint)) {
                    backButton.setClicked(true);
                    needsRepaint = true;
                }
                
                // Các nút hành động chỉ được xử lý nếu đến lượt người chơi
                if (isMyTurn()) {
                    if (upButton.contains(mousePoint)) {
                        upButton.setClicked(true);
                        needsRepaint = true;
                    }
                    if (downButton.contains(mousePoint)) {
                        downButton.setClicked(true);
                        needsRepaint = true;
                    }
                    if (leftButton.contains(mousePoint)) {
                        leftButton.setClicked(true);
                        needsRepaint = true;
                    }
                    if (rightButton.contains(mousePoint)) {
                        rightButton.setClicked(true);
                        needsRepaint = true;
                    }
                    if (jumpButton.contains(mousePoint)) {
                        jumpButton.setClicked(true);
                        needsRepaint = true;
                    }
                    if (cloneButton.contains(mousePoint)) {
                        cloneButton.setClicked(true);
                        needsRepaint = true;
                    }
                }
                
                if (needsRepaint) {
                    repaintPanel();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isLoading || gameHasEnded) return;
                
                // Quand la souris est relâchée, réinitialiser simplement tous les boutons
                backButton.setClicked(false);
                upButton.setClicked(false);
                downButton.setClicked(false);
                leftButton.setClicked(false);
                rightButton.setClicked(false);
                jumpButton.setClicked(false);
                cloneButton.setClicked(false);
                repaintPanel();
            }
        };

        // MouseMotionListener cũng cần được quản lý tương tự
        // Ở đây, MouseAdapter không implement MouseMotionListener, nên chúng ta cần một MouseMotionListener riêng.
        // Tuy nhiên, logic hover đã được đưa vào `update()`, nên `mouseMoved` không còn quá cần thiết ở đây.

        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
        }
    }

    private void clearMouseListeners() {
        if (sceneManager.getPanel() != null) {
            if (mouseAdapterInternal != null) {
                sceneManager.getPanel().removeMouseListener(mouseAdapterInternal);
                // Nếu có MouseMotionListener riêng, cũng remove nó ở đây
                // sceneManager.getPanel().removeMouseMotionListener(mouseMotionAdapterForHover);
            }
            // Để chắc chắn, bạn có thể lặp qua tất cả các listener và xóa nếu chúng là instance của adapter này
            // hoặc thuộc về GameScene.
        }
    }

    private void handleBoardClick(Point mousePoint) {
        if (jeu == null || gameClient == null || !gameClient.isConnected() || gameHasEnded) {
            statusMessage = "Jeu non prêt ou déconnecté.";
            repaintPanel();
            return;
        }

        Plateau clickedPlateauObj = getPlateauFromMousePoint(mousePoint);
        Point boardCoords = getBoardCoordinates(mousePoint);

        if (clickedPlateauObj != null && boardCoords != null) { // 有效的棋盘点击
            int clickedRow = boardCoords.x;
            int clickedCol = boardCoords.y;
            Piece pieceAtClick = clickedPlateauObj.getPiece(clickedRow, clickedCol);
            boolean needsRepaint = false; // 是否需要重绘

            if (selectedPiecePosition == null) { // 尚未选择棋子
                // 仅选择棋子，不判断所有权（服务器会判断）
                selectedPiecePosition = new Point(clickedRow, clickedCol);
                selectedPlateauType = clickedPlateauObj.getType();
                nextActionType = null; // 选择新棋子时重置动作
                statusMessage = "Pion (" + selectedPlateauType + " " + clickedRow + "," + clickedCol + ") sélectionné. Choisissez une action.";
                needsRepaint = true;
            } else { // 已经选择了棋子
                if (nextActionType == null) { // 尚未通过按钮选择任何动作
                    if (clickedPlateauObj.getType().equals(selectedPlateauType) &&
                        clickedRow == selectedPiecePosition.x && clickedCol == selectedPiecePosition.y) {
                        resetSelection(); // 点击同一个棋子 -> 取消选择
                        statusMessage = "Pion désélectionné.";
                        needsRepaint = true;
                    } else {
                        // 选择新的棋子
                        selectedPiecePosition = new Point(clickedRow, clickedCol);
                        selectedPlateauType = clickedPlateauObj.getType();
                        statusMessage = "Nouveau pion sélectionné. Choisissez une action.";
                        needsRepaint = true;
                    }
                }
                // 删除了MOVE相关代码，现在通过方向按钮处理
            }
            
            // 只在需要时重绘
            if (needsRepaint) {
                repaintPanel();
            }
        } else { // 点击棋盘外
            boolean needsRepaint = false;
            
            if (selectedPiecePosition != null && nextActionType == null) {
                resetSelection(); // 如果已选择棋子但未选择动作时点击外部则取消选择
                statusMessage = "Pion désélectionné (clic extérieur).";
                needsRepaint = true;
            } else if (selectedPiecePosition == null) {
                statusMessage = "Cliquez sur un pion.";
                needsRepaint = true;
            }
            
            if (needsRepaint) {
                repaintPanel();
            }
        }
    }

    // --- Các hàm helper getPlateauFromMousePoint, getBoardCoordinates, repaintPanel, resetSelection, resetSelectionAfterAction ---
    // (Giữ nguyên như phiên bản trước bạn đã có, đã được tích hợp ở trên)
    private Plateau getPlateauFromMousePoint(Point mousePoint) {
        if (jeu == null || sceneManager == null || sceneManager.getPanel() == null) return null;
        int width = sceneManager.getPanel().getWidth();
        int height = sceneManager.getPanel().getHeight();
        int dynamicButtonY = height - 70; // Đồng bộ với render
        if (dynamicButtonY < 450) dynamicButtonY = 450;

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
            if (mousePoint.x >= pastX && mousePoint.x < pastX + boardPixelWidth) return jeu.getPast();
            if (mousePoint.x >= presentX && mousePoint.x < presentX + boardPixelWidth) return jeu.getPresent();
            if (mousePoint.x >= futureX && mousePoint.x < futureX + boardPixelWidth) return jeu.getFuture();
        }
        return null;
    }

    private Point getBoardCoordinates(Point mousePoint) {
        Plateau clickedPlateau = getPlateauFromMousePoint(mousePoint);
        if (clickedPlateau == null) return null;

        int width = sceneManager.getPanel().getWidth();
        int height = sceneManager.getPanel().getHeight();
        int dynamicButtonY = height - 70; // Đồng bộ
        if (dynamicButtonY < 450) dynamicButtonY = 450;

        int boardSize = jeu.getTAILLE();
        int topMargin = 70;
        int bottomMarginForButtons = height - dynamicButtonY + 30;
        int availableHeightForBoards = height - topMargin - bottomMarginForButtons;
        int tileWidthByWidth = (width - 80) / (boardSize * 3 + 2 * (boardSize / 2));
        int tileWidthByHeight = availableHeightForBoards / (boardSize + 1);
        int tileWidth = Math.max(15, Math.min(tileWidthByWidth, tileWidthByHeight));

        if (tileWidth == 0) return null; // Tránh chia cho 0

        int spacing = Math.max(10, tileWidth / 2);
        int boardRenderHeight = boardSize * tileWidth;
        int totalBoardAreaWidth = boardSize * tileWidth * 3 + spacing * 2;
        int presentX = (width - totalBoardAreaWidth) / 2 + boardSize * tileWidth + spacing;
        int offsetY = topMargin + Math.max(0, (availableHeightForBoards - boardRenderHeight - 20) / 2);
        int pastX = presentX - boardSize * tileWidth - spacing;
        int futureX = presentX + boardSize * tileWidth + spacing;

        int boardXStart;
        switch (clickedPlateau.getType()) {
            case PAST: boardXStart = pastX; break;
            case PRESENT: boardXStart = presentX; break;
            case FUTURE: boardXStart = futureX; break;
            default: return null;
        }

        int col = (mousePoint.x - boardXStart) / tileWidth;
        int row = (mousePoint.y - offsetY) / tileWidth;

        if (row >= 0 && row < boardSize && col >= 0 && col < boardSize) return new Point(row, col);
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
        // statusMessage thường được cập nhật bởi logic gọi hàm này hoặc onGameStateUpdate
    }

    private void updateStatusFromCurrentGame(boolean fromServerUpdate) {
        if (this.jeu != null && this.jeu.getJoueurCourant() != null && gameClient != null) {
            if (this.jeu.getJoueurCourant().getId() == gameClient.getMyPlayerId()) {
                String playerName = this.jeu.getJoueurCourant().getId() == 1 ? "Lemiel" : "Zarek";
                this.statusMessage = "C'est VOTRE tour (" + playerName + " - Joueur " + gameClient.getMyPlayerId() + ")";
            } else {
                String opponentName = this.jeu.getJoueurCourant().getId() == 1 ? "Lemiel" : "Zarek";
                this.statusMessage = "Tour de l'adversaire : " + opponentName + " (ID " + this.jeu.getJoueurCourant().getId() + ")";
            }
        } else if (isLoading) {
             // 如果正在加载则保留SwingWorker的statusMessage
        } else if (gameClient != null && gameClient.isConnected()){
            this.statusMessage = "En attente de l'état du jeu du serveur...";
        } else {
            this.statusMessage = "Non connecté ou jeu non initialisé.";
        }
    }


    // --- GameStateUpdateListener Implementation ---
    @Override
    public void onGameStateUpdate(Jeu newGameState) {
        System.out.println("GameScene: 收到游戏状态更新");
        
        if (this.jeu == null && newGameState != null) {
            System.out.println("GameScene: 首次接收游戏状态");
        }
        
        this.jeu = newGameState;
        isLoading = false;
        
        if (newGameState != null && newGameState.getJoueurCourant() != null) {
            System.out.println("GameScene: 当前玩家 - ID: " + newGameState.getJoueurCourant().getId());
        }
        
        updateStatusFromCurrentGame(true);
        gameHasEnded = false;
        resetSelectionAfterAction();
        
        // 确保界面更新
        SwingUtilities.invokeLater(this::repaintPanel);
    }

    @Override
    public void onGameMessage(String messageType, String messageContent) {
        // Được gọi từ GameClient, đã nằm trên EDT
        isLoading = false; // Bất kỳ tin nhắn nào từ server đều ngụ ý không còn loading ban đầu nữa
        
        // 修改消息内容将玩家ID替换为角色名称
        String modifiedContent = messageContent;
        if (messageContent.contains("Joueur 1")) {
            modifiedContent = messageContent.replace("Joueur 1", "Lemiel");
        } else if (messageContent.contains("Joueur 2")) {
            modifiedContent = messageContent.replace("Joueur 2", "Zarek");
        }
        
        this.statusMessage = messageType + ": " + modifiedContent;
        String dialogTitle = messageType.toUpperCase();
        int jOptionPaneType = JOptionPane.INFORMATION_MESSAGE;
        boolean shouldShowDialog = true;

        if ("WIN".equalsIgnoreCase(messageType) || "LOSE".equalsIgnoreCase(messageType) ||
            "GAGNE".equalsIgnoreCase(messageType) || "PERDU".equalsIgnoreCase(messageType)) {
            gameHasEnded = true;
            dialogTitle = ("WIN".equalsIgnoreCase(messageType) || "GAGNE".equalsIgnoreCase(messageType)) ? "FÉLICITATIONS !" : "DOMMAGE !";
        } else if ("ERROR".equalsIgnoreCase(messageType)) {
            jOptionPaneType = JOptionPane.ERROR_MESSAGE;
        } else if ("DISCONNECTED".equalsIgnoreCase(messageType)){
             gameHasEnded = true; // Coi như kết thúc
             jOptionPaneType = JOptionPane.WARNING_MESSAGE;
             dialogTitle = "DÉCONNECTÉ";
        }
        else if ("ADVERSAIRE".equalsIgnoreCase(messageType) || "INFO".equalsIgnoreCase(messageType) ||
                 messageType.startsWith("PIECE") || messageType.startsWith("ACTION") ||
                 messageType.startsWith("DIRECTION") || messageType.startsWith("PLATEAU")) {
            shouldShowDialog = false; // Chỉ cập nhật statusMessage
        }

        if (shouldShowDialog) {
            JOptionPane.showMessageDialog(sceneManager.getPanel(), modifiedContent, dialogTitle, jOptionPaneType);
        }
        repaintPanel();

        // Nếu là DISCONNECTED hoặc lỗi nghiêm trọng, có thể muốn tự động quay về menu
        if ("DISCONNECTED".equalsIgnoreCase(messageType) ||
           ("ERROR".equalsIgnoreCase(messageType) && modifiedContent.contains("Connexion refusée"))) { // Ví dụ lỗi cụ thể
            //cleanUpAndGoToMenu(); // Đã có nút Retour, để người dùng quyết định
        }
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
}