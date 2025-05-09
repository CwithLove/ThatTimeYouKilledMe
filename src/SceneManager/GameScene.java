// Các import giữ nguyên như trước, thêm/sửa nếu cần
package SceneManager;

import Modele.Jeu;
import Modele.Joueur;
import Modele.Piece;
import Modele.Plateau;
import Modele.Coup;
import Network.GameClient;
import Network.GameStateUpdateListener;

import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseListener; // THÊM MỚI
import java.awt.event.MouseMotionListener; // THÊM MỚI
import java.io.IOException;

public class GameScene implements Scene, GameStateUpdateListener {
    private boolean fadeComplete = true;
    private SceneManager sceneManager;

    private Jeu jeu;

    private Point selectedPiecePosition = null;
    private Plateau.TypePlateau selectedPlateauType = null;
    private Coup.TypeCoup nextActionType = null;

    private Button backButton;
    private Button moveButton;
    private Button jumpButton;
    private Button cloneButton;

    private GameClient gameClient;
    private String serverIpAddress;
    private boolean isMultiplayerClient = false;
    private String statusMessage = "Chào mừng!";
    private boolean gameHasEnded = false; // Cờ để kiểm soát input sau khi game kết thúc

    // Constructor cho Single Player
    public GameScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        this.jeu = new Jeu();
        this.isMultiplayerClient = false;
        commonInit();
        // Set trạng thái ban đầu cho single player
        if (this.jeu != null && this.jeu.getJoueurCourant() != null) {
            this.statusMessage = "Lượt của: " + this.jeu.getJoueurCourant().getNom();
        }
    }

    // Constructor cho Multiplayer Client
    public GameScene(SceneManager sceneManager, String serverIpAddress) {
        this.sceneManager = sceneManager;
        this.serverIpAddress = serverIpAddress;
        this.isMultiplayerClient = true;
        this.jeu = new Jeu(); // Client sẽ nhận trạng thái đúng từ server
        commonInit();
    }

    // Constructor cho Multiplayer Host (đóng vai trò client)
    public GameScene(SceneManager sceneManager, boolean isHostPlayingAsClient) {
        this.sceneManager = sceneManager;
        if (isHostPlayingAsClient) {
            this.serverIpAddress = "127.0.0.1"; // Host kết nối tới server của chính nó
            this.isMultiplayerClient = true;
        } else {
            this.isMultiplayerClient = false; // Single player
        }
        this.jeu = new Jeu(); // Client/Host sẽ nhận trạng thái đúng từ server nếu là multi
        commonInit();
         // Set trạng thái ban đầu nếu là single player
        if (!this.isMultiplayerClient && this.jeu != null && this.jeu.getJoueurCourant() != null) {
            this.statusMessage = "Lượt của: " + this.jeu.getJoueurCourant().getNom();
        }
    }

    private void commonInit() {
        int buttonY = 550; // Tọa độ Y chung cho các nút
        if (sceneManager != null && sceneManager.getPanel() != null) {
             // Điều chỉnh vị trí Y dựa trên chiều cao của panel, ví dụ: cách đáy 100px
             buttonY = sceneManager.getPanel().getHeight() - 100;
             if (buttonY < 400) buttonY = 550; // Đảm bảo không quá cao nếu panel quá nhỏ ban đầu
        }


        backButton = new Button(50, buttonY, 150, 40, "Retour", () -> {
            if (isMultiplayerClient && gameClient != null) {
                // Xem xét gửi tin nhắn "DISCONNECT" hoặc "LEAVE_GAME" tới server
                // gameClient.sendPlayerAction("LEAVE_GAME"); // Ví dụ
            }
             // Yêu cầu xác nhận nếu đang trong game
            int confirmation = JOptionPane.YES_NO_OPTION;
            if (!gameHasEnded) { // Chỉ hỏi nếu game chưa kết thúc
                confirmation = JOptionPane.showConfirmDialog(sceneManager.getPanel(),
                        "Bạn có chắc muốn thoát? Tiến trình game có thể bị mất.",
                        "Xác nhận thoát",
                        JOptionPane.YES_NO_OPTION);
            }

            if (confirmation == JOptionPane.YES_OPTION || gameHasEnded) {
                sceneManager.setScene(new MenuScene(sceneManager));
            }
        });

        moveButton = new Button(250, buttonY, 100, 40, "MOVE", () -> {
            if (selectedPiecePosition != null && !gameHasEnded) {
                nextActionType = Coup.TypeCoup.MOVE;
                statusMessage = "Đã chọn MOVE. Click vào ô đích.";
                this.sceneManager.getPanel().repaint();
            }
        });
        jumpButton = new Button(360, buttonY, 100, 40, "JUMP", () -> {
            if (selectedPiecePosition != null && selectedPlateauType != null && !gameHasEnded) {
                nextActionType = Coup.TypeCoup.JUMP;
                String command = nextActionType.name() + ":" + selectedPlateauType.name() + ":" +
                                 selectedPiecePosition.x + ":" + selectedPiecePosition.y;
                if (isMultiplayerClient && gameClient != null) {
                    gameClient.sendPlayerAction(command);
                    statusMessage = "Đã gửi lệnh JUMP. Chờ server...";
                    resetSelectionAfterAction();
                } else if (!isMultiplayerClient) { // Xử lý JUMP cho Single Player
                    handleSinglePlayerAction(command);
                } else {
                    statusMessage = "Lỗi: GameClient không hoạt động.";
                }
                this.sceneManager.getPanel().repaint();
            }
        });
        cloneButton = new Button(470, buttonY, 100, 40, "CLONE", () -> {
            if (selectedPiecePosition != null && selectedPlateauType != null && !gameHasEnded) {
                nextActionType = Coup.TypeCoup.CLONE;
                String command = nextActionType.name() + ":" + selectedPlateauType.name() + ":" +
                                 selectedPiecePosition.x + ":" + selectedPiecePosition.y;
                if (isMultiplayerClient && gameClient != null) {
                    gameClient.sendPlayerAction(command);
                    statusMessage = "Đã gửi lệnh CLONE. Chờ server...";
                    resetSelectionAfterAction();
                } else if (!isMultiplayerClient) { // Xử lý CLONE cho Single Player
                     handleSinglePlayerAction(command);
                } else {
                    statusMessage = "Lỗi: GameClient không hoạt động.";
                }
                this.sceneManager.getPanel().repaint();
            }
        });
    }

    @Override
    public void init() {
        gameHasEnded = false; // Reset cờ kết thúc game khi scene được init
        if (isMultiplayerClient) {
            statusMessage = "Đang kết nối tới server...";
            if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();

            gameClient = new GameClient(serverIpAddress, this);
            try {
                gameClient.connect();
                statusMessage = "Đã kết nối. Chờ trạng thái game...";
            } catch (IOException e) {
                e.printStackTrace();
                statusMessage = "Lỗi kết nối: " + e.getMessage();
                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                        "Không thể kết nối đến server: " + e.getMessage() + "\nIP: " + serverIpAddress,
                        "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                sceneManager.setScene(new MenuScene(sceneManager));
                return;
            }
        } else {
             // statusMessage đã được set trong constructor cho single player
        }

        if (sceneManager != null && sceneManager.getPanel() != null) {
            // Gỡ bỏ listener cũ một cách an toàn
            for (MouseListener ml : sceneManager.getPanel().getMouseListeners()) {
                sceneManager.getPanel().removeMouseListener(ml);
            }
            for (MouseMotionListener mml : sceneManager.getPanel().getMouseMotionListeners()) {
                sceneManager.getPanel().removeMouseMotionListener(mml);
            }
            setupMouseListeners();
            sceneManager.getPanel().requestFocusInWindow();
        }
        if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
    }

    @Override
    public void update() {
        // Cập nhật vị trí nút nếu kích thước panel thay đổi (cần thiết hơn nếu layout phức tạp)
        // Tuy nhiên, vị trí Y của nút hiện được đặt cố định trong commonInit dựa trên chiều cao ban đầu.
        // Nếu muốn nút luôn ở cuối màn hình, cần cập nhật vị trí ở đây hoặc trong render.
        // Ví dụ:
        // if (sceneManager != null && sceneManager.getPanel() != null) {
        //     int panelHeight = sceneManager.getPanel().getHeight();
        //     int newButtonY = panelHeight - 60; // Cách đáy 60px
        //     if (newButtonY < 400) newButtonY = 400; // Giới hạn tối thiểu
        //     backButton.setLocation(backButton.getX(), newButtonY);
        //     moveButton.setLocation(moveButton.getX(), newButtonY);
        //     // ... tương tự cho các nút khác
        // }


        // Hiệu ứng hover dựa trên vị trí chuột tức thời
        if (sceneManager != null && sceneManager.getPanel() != null) {
            Point mousePos = sceneManager.getPanel().getMousePosition(); // Có thể null
            if (mousePos != null) {
                backButton.update(mousePos);
                // Chỉ cập nhật nút action nếu chúng hiển thị (đã chọn quân)
                if (selectedPiecePosition != null || !isMultiplayerClient) { // Luôn cho phép hover nút action ở single player nếu chúng được vẽ
                    moveButton.update(mousePos);
                    jumpButton.update(mousePos);
                    cloneButton.update(mousePos);
                }
            }
        }
    }


    @Override
    public void render(Graphics g, int width, int height) {
        // Cập nhật vị trí Y của các nút dựa trên chiều cao hiện tại của panel
        // Điều này đảm bảo các nút luôn ở một vị trí tương đối so với cuối màn hình game.
        int dynamicButtonY = height - 70; // Ví dụ: cách đáy 70 pixels
        if (dynamicButtonY < 400) dynamicButtonY = 400; // Giữ ở một mức tối thiểu nếu panel quá thấp

        backButton.setLocation(50, dynamicButtonY);
        moveButton.setLocation(250, dynamicButtonY);
        jumpButton.setLocation(360, dynamicButtonY);
        cloneButton.setLocation(470, dynamicButtonY);


        g.setColor(Color.DARK_GRAY); // Nền tối hơn một chút
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
         // Antialiasing cho chữ và hình vẽ mượt hơn
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);


        if (jeu != null) {
            Plateau past = jeu.getPast();
            Plateau present = jeu.getPresent();
            Plateau future = jeu.getFuture();

            int boardSize = Jeu.TAILLE;
            // Tính toán tileWidth và offsetY linh hoạt hơn
            int availableHeightForBoards = dynamicButtonY - 100; // Chiều cao có sẵn phía trên các nút
            int tileWidth = Math.min(width / (boardSize * 3 + 4), availableHeightForBoards / (boardSize + 1)); // +4 cho spacing, +1 cho title
            if (tileWidth < 10) tileWidth = 10; // Kích thước tối thiểu cho ô

            int spacing = tileWidth; // Giảm spacing một chút
            int boardRenderHeight = boardSize * tileWidth;
            int totalBoardWidth = boardSize * tileWidth * 3 + spacing * 2;

            int presentX = (width - totalBoardWidth) / 2 + boardSize * tileWidth + spacing; // Tính toán lại cho cân đối
            int offsetY = (availableHeightForBoards - boardRenderHeight) / 2 + 40; // +40 cho phần status message

            int pastX = presentX - boardSize * tileWidth - spacing;
            int futureX = presentX + boardSize * tileWidth + spacing;


            drawPlateau(g2d, past, pastX, offsetY, tileWidth, "PAST");
            drawPlateau(g2d, present, presentX, offsetY, tileWidth, "PRESENT");
            drawPlateau(g2d, future, futureX, offsetY, tileWidth, "FUTURE");

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 18));
            if (statusMessage != null && !statusMessage.isEmpty()){
                FontMetrics metrics = g2d.getFontMetrics();
                int msgWidth = metrics.stringWidth(statusMessage);
                g2d.drawString(statusMessage, (width - msgWidth) / 2, 30);
            }

            g2d.setFont(new Font("Arial", Font.PLAIN, 16));
            if (jeu.getJoueur1() != null)
                g2d.drawString("Clones " + jeu.getJoueur1().getNom() + ": " + jeu.getJoueur1().getNbClones(), 50, offsetY -15);
            if (jeu.getJoueur2() != null)
                g2d.drawString("Clones " + jeu.getJoueur2().getNom() + ": " + jeu.getJoueur2().getNbClones(), width - 200, offsetY - 15);

        } else {
             g2d.setColor(Color.WHITE);
             g2d.setFont(new Font("Arial", Font.BOLD, 20));
             FontMetrics metrics = g2d.getFontMetrics();
             int msgWidth = metrics.stringWidth(statusMessage);
             g2d.drawString(statusMessage, (width - msgWidth) / 2, height / 2);
        }

        backButton.render(g2d);
        // Luôn hiển thị nút hành động nếu không phải multiplayer hoặc nếu là single player
        // và đã chọn quân (nếu bạn muốn UI đồng nhất)
        // Hoặc, chỉ hiện khi selectedPiecePosition != null bất kể chế độ nào
        if (selectedPiecePosition != null) {
             moveButton.render(g2d);
             jumpButton.render(g2d);
             cloneButton.render(g2d);
        }
        g2d.dispose();
    }

    private void drawPlateau(Graphics g, Plateau plateau, int x, int y, int tileWidth, String title) {
        if (plateau == null) return;

        int boardPixelSize = plateau.getSize() * tileWidth;
        // Vẽ title cho plateau
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, Math.max(12, tileWidth / 2))); // Font size linh hoạt
        FontMetrics metrics = g.getFontMetrics();
        int titleWidth = metrics.stringWidth(title);
        g.drawString(title, x + (boardPixelSize - titleWidth) / 2, y - metrics.getDescent() - 5); // Vẽ title phía trên

        // Vẽ viền ngoài cho bàn cờ
        g.setColor(Color.LIGHT_GRAY);
        g.drawRect(x -1 , y -1, boardPixelSize + 2, boardPixelSize + 2);


        for (int row = 0; row < plateau.getSize(); row++) {
            for (int col = 0; col < plateau.getSize(); col++) {
                if ((row + col) % 2 == 0) {
                    g.setColor(new Color(230, 230, 230)); // Màu trắng ngà
                } else {
                    g.setColor(new Color(180, 180, 180)); // Màu xám nhạt
                }
                g.fillRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);
                // Vẽ lưới cho ô
                g.setColor(Color.DARK_GRAY);
                g.drawRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);


                Piece piece = plateau.getPiece(row, col);
                if (piece != null && piece.getOwner() != null) {
                    int ovalMargin = tileWidth / 8; // Thu nhỏ quân cờ một chút
                    int ovalSize = tileWidth - 2 * ovalMargin;
                    int ovalX = x + col * tileWidth + ovalMargin;
                    int ovalY = y + row * tileWidth + ovalMargin;

                    Color pieceColor = piece.getOwner().getId() == 1 ? new Color(0, 150, 255) : new Color(255, 50, 150); // Màu sắc rõ ràng hơn
                    g.setColor(pieceColor);
                    g.fillOval(ovalX, ovalY, ovalSize, ovalSize);
                    // Thêm viền cho quân cờ
                    g.setColor(pieceColor.darker());
                    ((Graphics2D)g).setStroke(new BasicStroke(Math.max(1, tileWidth/20)));
                    g.drawOval(ovalX, ovalY, ovalSize, ovalSize);


                    if (selectedPiecePosition != null &&
                        selectedPiecePosition.x == row && selectedPiecePosition.y == col &&
                        plateau.getType().equals(selectedPlateauType)) {
                        g.setColor(Color.YELLOW); // Viền vàng chóe
                        ((Graphics2D)g).setStroke(new BasicStroke(Math.max(2, tileWidth/10))); // Viền dày hơn
                        g.drawOval(ovalX - 2, ovalY - 2, ovalSize + 4, ovalSize + 4); // Viền to hơn một chút
                    }
                     ((Graphics2D)g).setStroke(new BasicStroke(1)); // Reset stroke
                }
            }
        }
    }

    @Override
    public void dispose() {
        if (isMultiplayerClient && gameClient != null) {
            gameClient.disconnect();
        }
        if (sceneManager != null && sceneManager.getPanel() != null && mouseAdapter != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapter);
            mouseAdapter = null; // Giúp GC
        }
        if (sceneManager != null && sceneManager.getPanel() != null && mouseMotionAdapter != null) {
             sceneManager.getPanel().removeMouseMotionListener(mouseMotionAdapter);
             mouseMotionAdapter = null; // Giúp GC
        }
    }

    private MouseAdapter mouseAdapter;
    private MouseMotionAdapter mouseMotionAdapter;

    private void setupMouseListeners() {
        mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!fadeComplete || gameHasEnded) return; // Không xử lý click nếu game đã kết thúc hoặc đang fade

                Point mousePoint = e.getPoint();

                if (backButton.contains(mousePoint)) {
                    backButton.onClick();
                    return;
                }
                // Nút hành động chỉ được xử lý nếu đã chọn quân
                if (selectedPiecePosition != null) {
                    if (moveButton.contains(mousePoint)) {
                        moveButton.onClick();
                        return;
                    }
                    if (jumpButton.contains(mousePoint)) {
                        jumpButton.onClick();
                        return;
                    }
                    if (cloneButton.contains(mousePoint)) {
                        cloneButton.onClick();
                        return;
                    }
                }

                if (isMultiplayerClient) {
                    handleMultiplayerBoardClick(mousePoint);
                } else {
                    handleSinglePlayerBoardClick(mousePoint);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (gameHasEnded) return;
                Point mousePoint = e.getPoint();
                if (backButton.contains(mousePoint)) backButton.setClicked(true);
                if (selectedPiecePosition != null) {
                    if (moveButton.contains(mousePoint)) moveButton.setClicked(true);
                    if (jumpButton.contains(mousePoint)) jumpButton.setClicked(true);
                    if (cloneButton.contains(mousePoint)) cloneButton.setClicked(true);
                }
                 if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (gameHasEnded) return;
                backButton.setClicked(false);
                moveButton.setClicked(false);
                jumpButton.setClicked(false);
                cloneButton.setClicked(false);
                if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
            }
        };
        sceneManager.getPanel().addMouseListener(mouseAdapter);

        mouseMotionAdapter = new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (gameHasEnded) return; // Không cập nhật hover nếu game đã kết thúc
                Point mousePoint = e.getPoint(); // Phải lấy lại mousePoint ở đây
                backButton.update(mousePoint);
                 // Chỉ cập nhật hover nút action nếu chúng được hiển thị (đã chọn quân)
                if (selectedPiecePosition != null) {
                    moveButton.update(mousePoint);
                    jumpButton.update(mousePoint);
                    cloneButton.update(mousePoint);
                }
                if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
            }
        };
        sceneManager.getPanel().addMouseMotionListener(mouseMotionAdapter);
    }

    private void handleMultiplayerBoardClick(Point mousePoint) {
        if (jeu == null || jeu.getJoueurCourant() == null || gameClient == null) {
             statusMessage = "Chưa sẵn sàng hoặc mất kết nối.";
             if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
             return;
        }

        Plateau clickedPlateauObj = null;
        int clickedRow = -1, clickedCol = -1;
        Point boardClickLocation = getBoardCoordinates(mousePoint);

        if (boardClickLocation != null) {
            clickedRow = boardClickLocation.x;
            clickedCol = boardClickLocation.y;
            // Xác định Plateau dựa trên mousePoint (cần một hàm helper)
            clickedPlateauObj = getPlateauFromMousePoint(mousePoint);
        }


        if (clickedPlateauObj != null && clickedRow != -1) {
            if (selectedPiecePosition == null) {
                Piece piece = clickedPlateauObj.getPiece(clickedRow, clickedCol);
                if (piece != null && piece.getOwner().equals(jeu.getJoueurCourant())) {
                    selectedPiecePosition = new Point(clickedRow, clickedCol);
                    selectedPlateauType = clickedPlateauObj.getType();
                    nextActionType = null;
                    statusMessage = "Đã chọn quân. Chọn hành động.";
                } else {
                    statusMessage = "Click vào quân cờ của bạn.";
                    resetSelection();
                }
            } else { // Đã chọn quân
                if (nextActionType == null) {
                    // Nếu click vào chính quân đã chọn -> bỏ chọn
                    if(clickedPlateauObj.getType().equals(selectedPlateauType) &&
                       clickedRow == selectedPiecePosition.x && clickedCol == selectedPiecePosition.y) {
                        resetSelection();
                        statusMessage = "Đã bỏ chọn quân.";
                    } else {
                        // Nếu click vào quân khác của mình -> đổi lựa chọn
                        Piece piece = clickedPlateauObj.getPiece(clickedRow, clickedCol);
                        if (piece != null && piece.getOwner().equals(jeu.getJoueurCourant())) {
                            selectedPiecePosition = new Point(clickedRow, clickedCol);
                            selectedPlateauType = clickedPlateauObj.getType();
                            statusMessage = "Đã chọn quân mới. Chọn hành động.";
                        } else {
                             statusMessage = "Chọn hành động (MOVE, JUMP, CLONE).";
                        }
                    }
                    // Không return, để repaint ở cuối
                } else if (nextActionType == Coup.TypeCoup.MOVE) {
                    if (!clickedPlateauObj.getType().equals(selectedPlateauType)) {
                        statusMessage = "MOVE phải trên cùng bàn cờ.";
                        // Không reset selection, để người chơi chọn lại đích
                    } else {
                        int dx = clickedRow - selectedPiecePosition.x;
                        int dy = clickedCol - selectedPiecePosition.y;
                        String command = "MOVE:" + selectedPlateauType.name() + ":" +
                                         selectedPiecePosition.x + ":" + selectedPiecePosition.y + ":" +
                                         dx + ":" + dy;
                        gameClient.sendPlayerAction(command);
                        statusMessage = "Đã gửi lệnh MOVE. Chờ server...";
                        resetSelectionAfterAction();
                    }
                }
                // JUMP và CLONE đã được xử lý bởi nút bấm của chúng
            }
        } else { // Click ra ngoài
            if (selectedPiecePosition != null && nextActionType == null) {
                statusMessage = "Đã chọn quân. Click lại để bỏ chọn hoặc chọn hành động.";
            } else if (selectedPiecePosition == null){
                 statusMessage = "Click vào một quân cờ.";
            }
            // Nếu đã chọn action MOVE mà click ra ngoài thì không làm gì, chờ click đúng target
        }
        if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
    }

    // Hàm helper để lấy Plateau dựa trên vị trí chuột
    private Plateau getPlateauFromMousePoint(Point mousePoint) {
        if (jeu == null || sceneManager == null || sceneManager.getPanel() == null) return null;

        int width = sceneManager.getPanel().getWidth();
        int height = sceneManager.getPanel().getHeight(); // Cần height để tính dynamicButtonY
        int dynamicButtonY = height - 70;
        if (dynamicButtonY < 400) dynamicButtonY = 400;

        int boardSize = Jeu.TAILLE;
        int availableHeightForBoards = dynamicButtonY - 100;
        int tileWidth = Math.min(width / (boardSize * 3 + 4), availableHeightForBoards / (boardSize + 1));
        if (tileWidth < 10) tileWidth = 10;

        int spacing = tileWidth;
        int boardRenderHeight = boardSize * tileWidth;
        int totalBoardWidth = boardSize * tileWidth * 3 + spacing * 2;

        int presentX = (width - totalBoardWidth) / 2 + boardSize * tileWidth + spacing;
        int offsetY = (availableHeightForBoards - boardRenderHeight) / 2 + 40;

        int pastX = presentX - boardSize * tileWidth - spacing;
        int futureX = presentX + boardSize * tileWidth + spacing;
        int boardPixelWidth = boardSize * tileWidth; // Chiều rộng thực của một bàn cờ

        if (mousePoint.x >= pastX && mousePoint.x < pastX + boardPixelWidth &&
            mousePoint.y >= offsetY && mousePoint.y < offsetY + boardRenderHeight) {
            return jeu.getPast();
        } else if (mousePoint.x >= presentX && mousePoint.x < presentX + boardPixelWidth &&
                   mousePoint.y >= offsetY && mousePoint.y < offsetY + boardRenderHeight) {
            return jeu.getPresent();
        } else if (mousePoint.x >= futureX && mousePoint.x < futureX + boardPixelWidth &&
                   mousePoint.y >= offsetY && mousePoint.y < offsetY + boardRenderHeight) {
            return jeu.getFuture();
        }
        return null;
    }

    // Hàm helper để lấy tọa độ (row, col) trên bàn cờ từ vị trí chuột
    private Point getBoardCoordinates(Point mousePoint) {
        Plateau clickedPlateau = getPlateauFromMousePoint(mousePoint);
        if (clickedPlateau == null || sceneManager == null || sceneManager.getPanel() == null) return null;

        int width = sceneManager.getPanel().getWidth();
        int height = sceneManager.getPanel().getHeight();
        int dynamicButtonY = height - 70;
        if (dynamicButtonY < 400) dynamicButtonY = 400;

        int boardSize = Jeu.TAILLE;
        int availableHeightForBoards = dynamicButtonY - 100;
        int tileWidth = Math.min(width / (boardSize * 3 + 4), availableHeightForBoards / (boardSize + 1));
         if (tileWidth < 10) tileWidth = 10;

        int spacing = tileWidth;
        int boardRenderHeight = boardSize * tileWidth;
        int totalBoardWidth = boardSize * tileWidth * 3 + spacing * 2;

        int presentX = (width - totalBoardWidth) / 2 + boardSize * tileWidth + spacing;
        int offsetY = (availableHeightForBoards - boardRenderHeight) / 2 + 40;
        int pastX = presentX - boardSize * tileWidth - spacing;
        int futureX = presentX + boardSize * tileWidth + spacing;

        int boardXStart = 0;
        if (clickedPlateau.getType() == Plateau.TypePlateau.PAST) boardXStart = pastX;
        else if (clickedPlateau.getType() == Plateau.TypePlateau.PRESENT) boardXStart = presentX;
        else if (clickedPlateau.getType() == Plateau.TypePlateau.FUTURE) boardXStart = futureX;
        else return null;

        int col = (mousePoint.x - boardXStart) / tileWidth;
        int row = (mousePoint.y - offsetY) / tileWidth;

        if (row >= 0 && row < boardSize && col >= 0 && col < boardSize) {
            return new Point(row, col);
        }
        return null;
    }


    private void handleSinglePlayerBoardClick(Point mousePoint) {
        if (jeu == null || jeu.getJoueurCourant() == null) {
             statusMessage = "Game chưa sẵn sàng.";
             if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
             return;
        }

        Plateau clickedPlateauObj = getPlateauFromMousePoint(mousePoint);
        Point boardCoords = getBoardCoordinates(mousePoint); // Lấy row, col

        if (clickedPlateauObj != null && boardCoords != null) {
            int clickedRow = boardCoords.x;
            int clickedCol = boardCoords.y;

            if (selectedPiecePosition == null) {
                Piece piece = clickedPlateauObj.getPiece(clickedRow, clickedCol);
                if (piece != null && piece.getOwner().equals(jeu.getJoueurCourant())) {
                    selectedPiecePosition = new Point(clickedRow, clickedCol);
                    selectedPlateauType = clickedPlateauObj.getType();
                    nextActionType = null;
                    statusMessage = "Đã chọn quân. Chọn hành động.";
                } else {
                     statusMessage = "Chọn một quân cờ của bạn.";
                     resetSelection();
                }
            } else { // Đã chọn quân
                if (nextActionType == null) {
                    // Click lại quân đã chọn -> bỏ chọn
                    if(clickedPlateauObj.getType().equals(selectedPlateauType) &&
                       clickedRow == selectedPiecePosition.x && clickedCol == selectedPiecePosition.y) {
                        resetSelection();
                        statusMessage = "Đã bỏ chọn quân.";
                    } else {
                         Piece piece = clickedPlateauObj.getPiece(clickedRow, clickedCol);
                        if (piece != null && piece.getOwner().equals(jeu.getJoueurCourant())) {
                            selectedPiecePosition = new Point(clickedRow, clickedCol);
                            selectedPlateauType = clickedPlateauObj.getType();
                            statusMessage = "Đã chọn quân mới. Chọn hành động.";
                        } else {
                            statusMessage = "Chọn hành động (MOVE, JUMP, CLONE).";
                        }
                    }
                } else { // Đã chọn quân và action
                    // JUMP, CLONE được xử lý bởi nút bấm, chỉ còn MOVE ở đây
                    if (nextActionType == Coup.TypeCoup.MOVE) {
                        if (!clickedPlateauObj.getType().equals(selectedPlateauType)) {
                            statusMessage = "MOVE phải trên cùng bàn cờ.";
                        } else {
                            int dx = clickedRow - selectedPiecePosition.x;
                            int dy = clickedCol - selectedPiecePosition.y;
                            String commandString = "MOVE:" + selectedPlateauType.name() + ":" +
                                             selectedPiecePosition.x + ":" + selectedPiecePosition.y + ":" +
                                             dx + ":" + dy;
                            handleSinglePlayerAction(commandString); // Gửi lệnh cho xử lý single player
                        }
                    }
                }
            }
        } else {
            if (selectedPiecePosition != null && nextActionType == null) {
                statusMessage = "Đã chọn quân. Click lại để bỏ chọn hoặc chọn hành động.";
            } else if(selectedPiecePosition == null) {
                 statusMessage = "Click vào một quân cờ.";
            }
        }
        if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
    }

    // Phương thức mới để xử lý action cho single player từ command string
    private void handleSinglePlayerAction(String commandString) {
        if (jeu == null || selectedPiecePosition == null || selectedPlateauType == null || nextActionType == null) {
            statusMessage = "Lỗi: Lựa chọn không đầy đủ cho hành động.";
            resetSelectionAfterAction();
            if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
            return;
        }

        Piece pieceToAct = jeu.getPlateauByType(selectedPlateauType).getPiece(selectedPiecePosition.x, selectedPiecePosition.y);
        if (pieceToAct == null) {
            statusMessage = "Lỗi: Quân cờ đã chọn không tồn tại.";
            resetSelectionAfterAction();
            if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
            return;
        }

        Coup coup = null;
        // Phân tích commandString để tạo Coup
        // Ví dụ đơn giản, không phân tích lại từ string mà dùng các biến hiện có
        Point direction = new Point(0,0); // Mặc định cho JUMP/CLONE
        if (nextActionType == Coup.TypeCoup.MOVE) {
            // Lấy dx, dy từ commandString nếu cần, hoặc từ click cuối cùng
            // Giả sử dx, dy đã được xác định trước khi gọi hàm này cho MOVE
            String[] parts = commandString.split(":");
            if (parts.length == 6 && parts[0].equals("MOVE")) {
                 try {
                    int dx = Integer.parseInt(parts[4]);
                    int dy = Integer.parseInt(parts[5]);
                    direction = new Point(dx, dy);
                } catch (NumberFormatException e) {
                    statusMessage = "Lỗi định dạng lệnh MOVE.";
                     if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
                    return;
                }
            } else if (parts.length < 6 && parts[0].equals("MOVE")){
                 statusMessage = "Lệnh MOVE thiếu thông tin hướng.";
                 if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
                return;
            }
        }
        coup = new Coup(pieceToAct, direction, jeu.getPlateauByType(selectedPlateauType), nextActionType);


        if (coup != null) {
            boolean isValid = jeu.estCoupValide(coup);
            if (isValid) {
                jeu.appliquerCoup(coup, jeu.getJoueurCourant(), jeu.getPast(), jeu.getPresent(), jeu.getFuture());
                // Chuyển lượt
                Joueur nextPlayer = jeu.getJoueurCourant().equals(jeu.getJoueur1()) ? jeu.getJoueur2() : jeu.getJoueur1();
                jeu.setJoueurCourant(nextPlayer);
                statusMessage = "Lượt của: " + jeu.getJoueurCourant().getNom();

                int winnerId = jeu.gameOver(jeu.getJoueur1()); // Kiểm tra cho J1
                if (winnerId == 0) { // Nếu J1 chưa thắng, kiểm tra cho J2
                    winnerId = jeu.gameOver(jeu.getJoueur2());
                }

                if (winnerId != 0) {
                    Joueur winningPlayer = (winnerId == jeu.getJoueur1().getId()) ? jeu.getJoueur1() : jeu.getJoueur2();
                    statusMessage = "Người chơi " + winningPlayer.getNom() + " đã thắng!";
                    gameHasEnded = true;
                    JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Game Over", JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                statusMessage = "Nước đi không hợp lệ!";
            }
        }
        resetSelectionAfterAction(); // Reset sau khi thử thực hiện hành động
        if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
    }


    private void resetSelection() { // Được gọi khi chỉ muốn reset lựa chọn thô
        selectedPiecePosition = null;
        selectedPlateauType = null;
        nextActionType = null;
    }
    private void resetSelectionAfterAction() { // Được gọi sau khi 1 action đã được thử (gửi hoặc thực hiện)
        selectedPiecePosition = null;
        selectedPlateauType = null;
        nextActionType = null;
        // statusMessage thường đã được set bởi logic action, không reset ở đây
    }


    @Override
    public void onGameStateUpdate(Jeu newGameState) {
        this.jeu = newGameState;
        if (this.jeu != null && this.jeu.getJoueurCourant() != null) {
             this.statusMessage = "Lượt của: " + this.jeu.getJoueurCourant().getNom();
        } else {
            this.statusMessage = "Đang chờ dữ liệu người chơi...";
        }
        // Kiểm tra game over từ trạng thái server gửi về
        // Server sẽ gửi ETAT, trong đó có thể đã bao gồm thông tin thắng thua tiềm ẩn
        // Hoặc server sẽ gửi một tin nhắn GAGNE/PERDU riêng biệt
        gameHasEnded = false; // Reset, chờ tin nhắn GAGNE/PERDU từ server
        resetSelectionAfterAction();
        if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
    }

    @Override
    public void onGameMessage(String messageType, String messageContent) {
        this.statusMessage = messageType + ": " + messageContent;
        if ("WIN".equalsIgnoreCase(messageType) || "LOSE".equalsIgnoreCase(messageType)) {
            gameHasEnded = true; // Đặt cờ khi nhận được thông báo thắng/thua
        } else if ("DISCONNECTED".equalsIgnoreCase(messageType)) {
            gameHasEnded = true; // Coi như game kết thúc nếu mất kết nối
        }


        // Đảm bảo dialog hiển thị trên EDT (GameClient đã làm, nhưng cẩn thận không thừa)
        SwingUtilities.invokeLater(() -> {
            String title = messageType.toUpperCase();
            int jOptionMessageType = JOptionPane.INFORMATION_MESSAGE;

            if("ERROR".equalsIgnoreCase(messageType) || "DISCONNECTED".equalsIgnoreCase(messageType)) {
                jOptionMessageType = JOptionPane.ERROR_MESSAGE;
            } else if ("WIN".equalsIgnoreCase(messageType)) {
                title = "CHÚC MỪNG!";
            } else if ("LOSE".equalsIgnoreCase(messageType)) {
                title = "THẬT TIẾC!";
            }
            JOptionPane.showMessageDialog(sceneManager.getPanel(), messageContent, title, jOptionMessageType);
        });

        if (sceneManager.getPanel() != null) sceneManager.getPanel().repaint();
    }
}