package SceneManager;

import Modele.Jeu;
import Modele.Joueur;
import Modele.Piece;
import Modele.Plateau;
import Modele.Coup;
import Network.GameClient;
import Network.GameStateUpdateListener;
import Network.GameServerManager;
import Network.AIClient; // Đảm bảo bạn đã tạo lớp này trong package AI

import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker; // Sử dụng SwingWorker cho các tác vụ nền
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener; // Cần cho mouseMoved
import java.io.IOException;

public class GameScene implements Scene, GameStateUpdateListener {
    private SceneManager sceneManager;
    private Jeu jeu; // Luôn là bản sao của trạng thái game từ server

    // Trạng thái lựa chọn của UI
    private Point selectedPiecePosition = null;
    private Plateau.TypePlateau selectedPlateauType = null;
    private Coup.TypeCoup nextActionType = null; // Chủ yếu cho UI biết đang chờ click đích cho MOVE

    // UI Buttons
    private Button backButton;
    private Button moveButton;
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
        commonUIInit();
    }

    // Constructor cho Client kết nối tới Host trong Multiplayer
    public GameScene(SceneManager sceneManager, String serverIpToConnect) {
        this.sceneManager = sceneManager;
        this.isOperatingInSinglePlayerMode = false;
        this.serverIpToConnectOnDemand = serverIpToConnect;
        this.statusMessage = "Mode Multi: Connexion à l'hôte...";
        commonUIInit();
    }

    // Constructor cho Host trong Multiplayer (đã có GameClient được kết nối từ HostingScene)
    public GameScene(SceneManager sceneManager, GameClient alreadyConnectedHostClient) {
        this.sceneManager = sceneManager;
        this.isOperatingInSinglePlayerMode = false;
        this.gameClient = alreadyConnectedHostClient; // Sử dụng client đã được cung cấp
        if (this.gameClient != null) {
            this.gameClient.setListener(this); // Đảm bảo listener là GameScene này
            this.jeu = this.gameClient.getGameInstance(); // Lấy trạng thái game ban đầu
            updateStatusFromCurrentGame(false); // Cập nhật status message ban đầu
        } else {
            this.statusMessage = "Erreur: Client hôte non fourni à GameScene.";
            System.err.println(statusMessage);
        }
        commonUIInit();
    }

    private void commonUIInit() {
        // Vị trí nút sẽ được cập nhật trong render()
        backButton = new Button(0, 0, 150, 40, "Retour Menu", this::handleBackButton);
        moveButton = new Button(0, 0, 100, 40, "MOVE", () -> {
            if (isMyTurn() && selectedPiecePosition != null && !gameHasEnded) {
                nextActionType = Coup.TypeCoup.MOVE;
                statusMessage = "MOVE: Cliquez sur la case destination.";
                repaintPanel();
            } else if (!isMyTurn()){
                statusMessage = "Ce n'est pas votre tour.";
                repaintPanel();
            }
        });
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
        isLoading = false; // Dừng mọi trạng thái loading
        if (gameClient != null) {
            gameClient.disconnect();
            gameClient = null;
        }
        // Dừng server và AI CHỈ nếu GameScene này chịu trách nhiệm khởi động chúng (chế độ single player)
        if (isOperatingInSinglePlayerMode) {
            if (localAIClientThread != null && localAIClientThread.isAlive()) {
                if(aiClientInstance != null) aiClientInstance.disconnect(); // Yêu cầu AI client ngắt kết nối
                localAIClientThread.interrupt(); // Ngắt luồng AI
                localAIClientThread = null;
                aiClientInstance = null;
                 System.out.println("GameScene (Solo): Thread AI arrêté.");
            }
            if (localSinglePlayerServerManager != null && localSinglePlayerServerManager.isServerRunning()) {
                localSinglePlayerServerManager.stopServer();
                // Không set localSinglePlayerServerManager = null để có thể kiểm tra isServerRunning lần sau
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
            System.out.println("GameScene (Host): Utilisation du GameClient déjà connecté.");
            isLoading = false; // Host đã kết nối, không cần loading kéo dài
            updateStatusFromCurrentGame(false);
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
        if (jeu == null || jeu.getJoueurCourant() == null || gameClient == null || gameClient.getMyPlayerId() == -1) {
            return false; // Chưa sẵn sàng hoặc không biết ID của mình
        }
        return jeu.getJoueurCourant().getId() == gameClient.getMyPlayerId();
    }

    private void handleActionCommand(Coup.TypeCoup actionType) {
        if (!isMyTurn()){
            statusMessage = "Ce n'est pas votre tour.";
            repaintPanel();
            return;
        }
        if (selectedPiecePosition == null || selectedPlateauType == null || gameHasEnded || gameClient == null || !gameClient.isConnected()) {
            statusMessage = "Sélectionnez un pion d'abord ou action non permise.";
            repaintPanel();
            return;
        }

        // Lấy quân cờ từ trạng thái game cục bộ (đã được server cập nhật) để kiểm tra owner
        Piece piece = jeu.getPlateauByType(selectedPlateauType).getPiece(selectedPiecePosition.x, selectedPiecePosition.y);
        if (piece == null || piece.getOwner().getId() != gameClient.getMyPlayerId()) {
            statusMessage = "Pion invalide ou n'appartient pas à vous.";
            resetSelection();
            repaintPanel();
            return;
        }

        // JUMP và CLONE gửi lệnh ngay
        nextActionType = actionType;
        String command = nextActionType.name() + ":" + selectedPlateauType.name() + ":" +
                         selectedPiecePosition.x + ":" + selectedPiecePosition.y;

        gameClient.sendPlayerAction(command);
        statusMessage = "Commande " + actionType.name() + " envoyée...";
        resetSelectionAfterAction(); // Reset UI sau khi gửi lệnh
        repaintPanel();
    }


    @Override
    public void update() {
        // Chỉ cập nhật hover nếu không loading và game chưa kết thúc
        if (!isLoading && !gameHasEnded && sceneManager != null && sceneManager.getPanel() != null) {
            Point mousePos = sceneManager.getPanel().getMousePosition();
            if (mousePos != null) {
                backButton.update(mousePos);
                boolean myTurn = isMyTurn(); // Kiểm tra một lần

                if (myTurn && selectedPiecePosition != null) {
                    moveButton.update(mousePos);
                    jumpButton.update(mousePos);
                    cloneButton.update(mousePos);
                } else { // Không phải lượt hoặc không có quân nào được chọn -> không hover nút action
                    if(moveButton.contains(mousePos)) moveButton.update(new Point(-1,-1));
                    if(jumpButton.contains(mousePos)) jumpButton.update(new Point(-1,-1));
                    if(cloneButton.contains(mousePos)) cloneButton.update(new Point(-1,-1));
                }
                // repaintPanel(); // Có thể không cần thiết nếu render() được gọi thường xuyên
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
        moveButton.setSize(actionButtonWidth, buttonCommonHeight);
        moveButton.setLocation(actionButtonXStart, dynamicButtonY);

        jumpButton.setSize(actionButtonWidth, buttonCommonHeight);
        jumpButton.setLocation(actionButtonXStart + actionButtonWidth + 10, dynamicButtonY);

        cloneButton.setSize(actionButtonWidth, buttonCommonHeight);
        cloneButton.setLocation(actionButtonXStart + (actionButtonWidth + 10) * 2, dynamicButtonY);

        // Vẽ nền
        g.setColor(new Color(25, 25, 35)); // Nền tối hơn
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

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

            int boardSize = Jeu.TAILLE;
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

            drawPlateau(g2d, past, pastX, offsetY, tileWidth, "PASSÉ");
            drawPlateau(g2d, present, presentX, offsetY, tileWidth, "PRÉSENT");
            drawPlateau(g2d, future, futureX, offsetY, tileWidth, "FUTUR");

            // Status Message
            g2d.setColor(Color.CYAN);
            g2d.setFont(new Font("Consolas", Font.BOLD, 18));
            if (statusMessage != null && !statusMessage.isEmpty()) {
                FontMetrics metrics = g2d.getFontMetrics();
                int msgWidth = metrics.stringWidth(statusMessage);
                g2d.drawString(statusMessage, (width - msgWidth) / 2, 40);
            }

            // Clones Info
            g2d.setFont(new Font("Consolas", Font.PLAIN, 15));
            g2d.setColor(Color.LIGHT_GRAY);
            int cloneInfoY = offsetY + boardRenderHeight + 25;
             if (cloneInfoY >= dynamicButtonY - 15) { // Tránh đè lên nút
                cloneInfoY = offsetY - 35; // Đặt phía trên plateau nếu không đủ chỗ
                 if (cloneInfoY < 20) cloneInfoY = 20;
            }

            if (jeu.getJoueur1() != null) {
                String p1Info = jeu.getJoueur1().getNom() + " (ID " + jeu.getJoueur1().getId() + ") Clones: " + jeu.getJoueur1().getNbClones();
                if (gameClient != null && jeu.getJoueur1().getId() == gameClient.getMyPlayerId()) p1Info += " (Vous)";
                g2d.drawString(p1Info, Math.max(10,pastX - 20), cloneInfoY);
            }
            if (jeu.getJoueur2() != null) {
                String p2Info = jeu.getJoueur2().getNom() + " (ID " + jeu.getJoueur2().getId() + ") Clones: " + jeu.getJoueur2().getNbClones();
                 if (gameClient != null && jeu.getJoueur2().getId() == gameClient.getMyPlayerId()) p2Info += " (Vous)";
                FontMetrics p2Metrics = g2d.getFontMetrics();
                int p2InfoWidth = p2Metrics.stringWidth(p2Info);
                g2d.drawString(p2Info, Math.min(width - 10 - p2InfoWidth, futureX + (boardSize * tileWidth) + 20 - p2InfoWidth), cloneInfoY);
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
        // Chỉ hiển thị nút action nếu quân cờ được chọn VÀ đến lượt người chơi VÀ game chưa kết thúc
        if (selectedPiecePosition != null && isMyTurn() && !gameHasEnded) {
            moveButton.render(g2d);
            jumpButton.render(g2d);
            cloneButton.render(g2d);
        }
        g2d.dispose();
    }

    private void drawPlateau(Graphics2D g, Plateau plateau, int x, int y, int tileWidth, String title) {
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

        for (int row = 0; row < boardSize; row++) {
            for (int col = 0; col < boardSize; col++) {
                // Cell background
                if ((row + col) % 2 == 0) g.setColor(new Color(75, 75, 85)); // Darker
                else g.setColor(new Color(60, 60, 70)); // Darkest
                g.fillRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);
                // Cell grid
                g.setColor(new Color(100, 100, 110));
                g.drawRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);

                Piece piece = plateau.getPiece(row, col);
                if (piece != null && piece.getOwner() != null) {
                    int ovalMargin = Math.max(2, tileWidth / 8);
                    int ovalSize = tileWidth - 2 * ovalMargin;
                    int ovalX = x + col * tileWidth + ovalMargin;
                    int ovalY = y + row * tileWidth + ovalMargin;

                    Color pieceFillColor;
                    Color pieceBorderColor;

                    if (piece.getOwner().getId() == 1) { // Joueur 1 (Blanc)
                        pieceFillColor = new Color(220, 220, 240); // Presque blanc
                        pieceBorderColor = new Color(150, 150, 200);
                    } else { // Joueur 2 (Noir/Bot)
                        pieceFillColor = new Color(60, 60, 80);   // Bleu-noir foncé
                        pieceBorderColor = new Color(100, 100, 180);
                    }
                    // Thêm hiệu ứng nếu là quân của client hiện tại
                    if (gameClient != null && piece.getOwner().getId() == gameClient.getMyPlayerId()){
                         pieceBorderColor = Color.GREEN; // Viền xanh lá cho quân của mình
                    }


                    g.setColor(pieceFillColor);
                    g.fillOval(ovalX, ovalY, ovalSize, ovalSize);
                    g.setColor(pieceBorderColor);
                    g.setStroke(new BasicStroke(Math.max(1.5f, tileWidth / 16f)));
                    g.drawOval(ovalX, ovalY, ovalSize, ovalSize);

                    // Highlight if selected
                    if (selectedPiecePosition != null &&
                        selectedPiecePosition.x == row && selectedPiecePosition.y == col &&
                        plateau.getType().equals(selectedPlateauType)) {
                        g.setColor(Color.ORANGE);
                        g.setStroke(new BasicStroke(Math.max(2.5f, tileWidth / 10f)));
                        g.drawOval(ovalX - 2, ovalY - 2, ovalSize + 4, ovalSize + 4); // Slightly larger highlight
                    }
                    g.setStroke(new BasicStroke(1f)); // Reset stroke
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

                // Các nút hành động chỉ được xử lý nếu một quân cờ đã được chọn VÀ đến lượt người chơi
                if (selectedPiecePosition != null && isMyTurn()) {
                    if (moveButton.contains(mousePoint)) { moveButton.onClick(); return; }
                    if (jumpButton.contains(mousePoint)) { jumpButton.onClick(); return; }
                    if (cloneButton.contains(mousePoint)) { cloneButton.onClick(); return; }
                }
                handleBoardClick(mousePoint); // Xử lý click trên bàn cờ
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isLoading || gameHasEnded) return;
                Point mousePoint = e.getPoint();
                if (backButton.contains(mousePoint)) backButton.setClicked(true);
                if (selectedPiecePosition != null && isMyTurn()) {
                    if (moveButton.contains(mousePoint)) moveButton.setClicked(true);
                    if (jumpButton.contains(mousePoint)) jumpButton.setClicked(true);
                    if (cloneButton.contains(mousePoint)) cloneButton.setClicked(true);
                }
                repaintPanel();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (isLoading || gameHasEnded) return;
                backButton.setClicked(false);
                moveButton.setClicked(false);
                jumpButton.setClicked(false);
                cloneButton.setClicked(false);
                repaintPanel();
            }
        };

        // MouseMotionListener cũng cần được quản lý tương tự
        // Nếu mouseAdapterInternal cũng implement MouseMotionListener, thì một lần add/remove là đủ.
        // Nếu không, bạn cần một biến riêng cho MouseMotionListener.
        // Ở đây, MouseAdapter không implement MouseMotionListener, nên chúng ta cần một MouseMotionListener riêng.
        // Tuy nhiên, logic hover đã được đưa vào `update()`, nên `mouseMoved` không còn quá cần thiết ở đây.
        // Nếu bạn vẫn muốn dùng mouseMoved cho hover, hãy tạo một MouseMotionAdapter riêng.
        // Ví dụ: (trong setupMouseListeners)
        // mouseMotionAdapterInternal = new MouseMotionAdapter() { ... };
        // sceneManager.getPanel().addMouseMotionListener(mouseMotionAdapterInternal);

        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
            // Nếu bạn có logic hover trong mouseMoved của adapter riêng:
            // sceneManager.getPanel().addMouseMotionListener(mouseMotionAdapterForHover);
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
        if (!isMyTurn()) {
            statusMessage = "Ce n'est pas votre tour.";
            repaintPanel();
            return;
        }
        if (jeu == null || gameClient == null || !gameClient.isConnected()) {
            statusMessage = "Jeu non prêt ou déconnecté.";
            repaintPanel();
            return;
        }

        Plateau clickedPlateauObj = getPlateauFromMousePoint(mousePoint);
        Point boardCoords = getBoardCoordinates(mousePoint);

        if (clickedPlateauObj != null && boardCoords != null) { // Click hợp lệ trên một ô
            int clickedRow = boardCoords.x;
            int clickedCol = boardCoords.y;
            Piece pieceAtClick = clickedPlateauObj.getPiece(clickedRow, clickedCol);

            if (selectedPiecePosition == null) { // CHƯA CHỌN QUÂN CỜ
                if (pieceAtClick != null && pieceAtClick.getOwner().getId() == gameClient.getMyPlayerId()) {
                    selectedPiecePosition = new Point(clickedRow, clickedCol);
                    selectedPlateauType = clickedPlateauObj.getType();
                    nextActionType = null; // Reset action khi chọn quân mới
                    statusMessage = "Pion (" + selectedPlateauType + " " + clickedRow + "," + clickedCol + ") sélectionné. Choisissez une action.";
                } else {
                    statusMessage = "Cliquez sur un de VOS pions.";
                    resetSelection();
                }
            } else { // ĐÃ CHỌN QUÂN CỜ
                if (nextActionType == null) { // Chưa chọn MOVE/JUMP/CLONE bằng nút
                    if (clickedPlateauObj.getType().equals(selectedPlateauType) &&
                        clickedRow == selectedPiecePosition.x && clickedCol == selectedPiecePosition.y) {
                        resetSelection(); // Click lại quân cũ -> bỏ chọn
                        statusMessage = "Pion désélectionné.";
                    } else if (pieceAtClick != null && pieceAtClick.getOwner().getId() == gameClient.getMyPlayerId()) {
                        selectedPiecePosition = new Point(clickedRow, clickedCol); // Chọn quân khác của mình
                        selectedPlateauType = clickedPlateauObj.getType();
                        statusMessage = "Nouveau pion sélectionné. Choisissez une action.";
                    } else {
                        // Click vào ô trống/quân địch khi chưa có action -> không làm gì, chờ nút action
                        statusMessage = "Pion ("+selectedPlateauType+" "+selectedPiecePosition.x+","+selectedPiecePosition.y+ ") sélectionné. Choisissez une ACTION.";
                    }
                } else if (nextActionType == Coup.TypeCoup.MOVE) {
                    if (!clickedPlateauObj.getType().equals(selectedPlateauType)) {
                        statusMessage = "MOVE doit être sur le même plateau (" + selectedPlateauType + ").";
                    } else {
                        // Tính toán dx, dy dựa trên vị trí nguồn và đích
                        int dx = clickedRow - selectedPiecePosition.x;
                        int dy = clickedCol - selectedPiecePosition.y;

                        // Kiểm tra xem có phải là ô kề không (không cho phép di chuyển tới chính nó)
                        if ( (Math.abs(dx) == 1 && dy == 0) || (dx == 0 && Math.abs(dy) == 1) ) {
                             String command = "MOVE:" + selectedPlateauType.name() + ":" +
                                         selectedPiecePosition.x + ":" + selectedPiecePosition.y + ":" +
                                         dx + ":" + dy; // Gửi dx, dy tương đối
                            gameClient.sendPlayerAction(command);
                            statusMessage = "MOVE envoyé à ("+clickedRow+","+clickedCol+"). Attente serveur...";
                            resetSelectionAfterAction();
                        } else {
                            statusMessage = "Destination invalide pour MOVE. Doit être une case adjacente.";
                            // Không reset nextActionType, cho phép thử lại đích MOVE
                        }
                    }
                }
                // JUMP và CLONE đã được xử lý bởi handleActionCommand() khi nhấn nút
            }
        } else { // Click bên ngoài bàn cờ
            if (selectedPiecePosition != null && nextActionType == null) {
                resetSelection(); // Bỏ chọn nếu click ra ngoài khi chưa chọn action
                statusMessage = "Pion désélectionné (clic extérieur).";
            } else if (selectedPiecePosition == null) {
                statusMessage = "Cliquez sur un de vos pions.";
            }
        }
        repaintPanel();
    }

    // --- Các hàm helper getPlateauFromMousePoint, getBoardCoordinates, repaintPanel, resetSelection, resetSelectionAfterAction ---
    // (Giữ nguyên như phiên bản trước bạn đã có, đã được tích hợp ở trên)
    private Plateau getPlateauFromMousePoint(Point mousePoint) {
        if (jeu == null || sceneManager == null || sceneManager.getPanel() == null) return null;
        int width = sceneManager.getPanel().getWidth();
        int height = sceneManager.getPanel().getHeight();
        int dynamicButtonY = height - 70; // Đồng bộ với render
        if (dynamicButtonY < 450) dynamicButtonY = 450;

        int boardSize = Jeu.TAILLE;
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

        int boardSize = Jeu.TAILLE;
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
                this.statusMessage = "C'est VOTRE tour (Joueur " + gameClient.getMyPlayerId() + " - " + this.jeu.getJoueurCourant().getNom() + ")";
            } else {
                this.statusMessage = "Tour de l'adversaire : " + this.jeu.getJoueurCourant().getNom() + " (ID " + this.jeu.getJoueurCourant().getId() + ")";
            }
        } else if (isLoading) {
             // Giữ statusMessage của SwingWorker nếu đang loading
        } else if (gameClient != null && gameClient.isConnected()){
            this.statusMessage = "En attente de l'état du jeu du serveur...";
        } else {
            this.statusMessage = "Non connecté ou jeu non initialisé.";
        }
    }


    // --- GameStateUpdateListener Implementation ---
    @Override
    public void onGameStateUpdate(Jeu newGameState) {
        // Được gọi từ GameClient, đã nằm trên EDT
        this.jeu = newGameState;
        isLoading = false; // Game state received, no longer "initial loading"
        updateStatusFromCurrentGame(true);
        gameHasEnded = false; // Chờ tin nhắn GAGNE/PERDU cụ thể
        resetSelectionAfterAction(); // Xóa lựa chọn UI sau khi server cập nhật
        repaintPanel();
    }

    @Override
    public void onGameMessage(String messageType, String messageContent) {
        // Được gọi từ GameClient, đã nằm trên EDT
        isLoading = false; // Bất kỳ tin nhắn nào từ server đều ngụ ý không còn loading ban đầu nữa
        this.statusMessage = messageType + ": " + messageContent;
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
            JOptionPane.showMessageDialog(sceneManager.getPanel(), messageContent, dialogTitle, jOptionPaneType);
        }
        repaintPanel();

        // Nếu là DISCONNECTED hoặc lỗi nghiêm trọng, có thể muốn tự động quay về menu
        if ("DISCONNECTED".equalsIgnoreCase(messageType) ||
           ("ERROR".equalsIgnoreCase(messageType) && messageContent.contains("Connexion refusée"))) { // Ví dụ lỗi cụ thể
            //cleanUpAndGoToMenu(); // Đã có nút Retour, để người dùng quyết định
        }
    }

    @Override
    public void dispose() {
        System.out.println("GameScene: Dispose appelée.");
        isLoading = false;
        clearMouseListeners();

        // Dọn dẹp client và server nếu GameScene chịu trách nhiệm
        if (gameClient != null) {
            gameClient.disconnect();
            gameClient = null;
        }
        if (isOperatingInSinglePlayerMode) { // Chỉ dừng server và AI nếu GameScene khởi tạo chúng
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
                // localSinglePlayerServerManager = null; // Không nullify static ở đây, trừ khi muốn reset hoàn toàn
            }
            System.out.println("GameScene (Solo): Ressources serveur/AI nettoyées.");
        }
    }
}