package SceneManager;

import Modele.Jeu;
import Modele.Joueur;
import Modele.Piece;
import Modele.Plateau;
import Modele.Coup; // <-- THÊM DÒNG NÀY
import java.awt.*;
import java.awt.event.MouseAdapter; // Đảm bảo import này có
import java.awt.event.MouseEvent;   // Đảm bảo import này có
import java.awt.event.MouseMotionAdapter; // THÊM DÒNG NÀY cho MouseMotionListener an toàn
// import java.awt.event.MouseListener; // KHÔNG CẦN NỮA nếu dùng MouseAdapter/MouseMotionAdapter
// import java.awt.event.MouseMotionListener; // KHÔNG CẦN NỮA
import javax.swing.JPanel; // Có thể cần nếu GameScene tương tác trực tiếp với Panel

public class GameScene implements Scene {
    private boolean fadeComplete = true;
    private SceneManager sceneManager;
    private static int lastLogin = 0; // 0: single, 1: multi
    private static boolean isHost = false; // true: host, false: client

    // Les attributs pour le jeu
    private Jeu jeu;

    // Các thuộc tính để quản lý input và logic nước đi
    private Point selectedPiecePosition = null; // Vị trí quân cờ đang được chọn
    private Plateau.TypePlateau selectedPlateauType = null; // Loại plateau của quân cờ đang được chọn
    private Coup.TypeCoup nextActionType = null; // Hành động tiếp theo sau khi chọn quân (MOVE, JUMP, CLONE)

    // Nút Back (nên dùng lớp Button của bạn)
    private Button backButton;

    // Các MouseListener an toàn
    private MouseAdapter mouseAdapter;
    private MouseMotionAdapter mouseMotionAdapter;

    public GameScene() {
        // Constructor mặc định, không làm gì nhiều, chỉ khởi tạo Jeu
        jeu = new Jeu();
    }

    public GameScene(SceneManager sceneManager) {
        this(); // Gọi constructor mặc định để khởi tạo jeu
        this.sceneManager = sceneManager;

        // Khởi tạo nút Back
        backButton = new Button(50, 500, 150, 40, "Retour", () -> {
            // Logic quay lại menu hoặc màn hình trước đó
            if (lastLogin == 0) {
                sceneManager.setScene(new MenuScene(sceneManager));
            } else if (lastLogin == 1) {
                if (isHost) {
                    sceneManager.setScene(new MultiHostScene(sceneManager));
                } else {
                    // Nếu là client, quay lại màn hình kết nối hoặc lobby
                    sceneManager.setScene(new MultiConnectScene(sceneManager)); // Giả sử quay lại màn hình kết nối
                }
            }
        });

        // Setup Mouse Listeners an toàn
        setupMouseListeners();
    }

    public GameScene(SceneManager sceneManager, boolean isHost) {
        this(sceneManager);
        GameScene.isHost = isHost;
    }

    @Override
    public void init() {
        // Đảm bảo các listener được thêm khi scene được init
        if (sceneManager != null && sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapter);
            sceneManager.getPanel().addMouseMotionListener(mouseMotionAdapter);
            sceneManager.getPanel().requestFocusInWindow(); // Đảm bảo panel có focus để nhận input
        }
    }

    @Override
    public void update() {
        // Cập nhật các animation, hiệu ứng nếu có
        // Ví dụ: backButton.update(currentMousePosition); // Cần lưu trữ vị trí chuột hiện tại
    }

    @Override
    public void render(Graphics g, int width, int height) {
        // Set up Background
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        // Graphics2D cho các hiệu ứng nâng cao
        Graphics2D g2d = (Graphics2D) g.create(); // Tạo bản sao Graphics để không ảnh hưởng đến bản gốc
        // g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)); // Nếu có hiệu ứng fade-in cho GameScene

        if (jeu != null) {
            // Fetch the game board
            Plateau past = jeu.getPast(); // Sửa getPast() nếu trong Jeu là getPastPlateau()
            Plateau present = jeu.getPresent(); // Sửa getPresent() nếu trong Jeu là getPresentPlateau()
            Plateau future = jeu.getFuture(); // Sửa getFuture() nếu trong Jeu là getFuturePlateau()
            Joueur currentPlayer = jeu.getJoueurCourant();

            int boardSize = jeu.TAILLE; // Lấy kích thước bàn cờ từ Jeu (4x4)

            // Tính toán kích thước ô và khoảng cách dựa trên kích thước cửa sổ
            int totalBoardWidth = (boardSize * 3 + 2) * (width / 50); // Ước lượng tổng chiều rộng các bàn + khoảng cách
            int totalBoardHeight = boardSize * (width / 50); // Ước lượng tổng chiều cao 1 bàn

            int tileWidth = (width / 50); // Kích thước ô cơ bản
            int spacing = tileWidth * 2; // Khoảng cách giữa các bàn

            // Vị trí X của bàn Present
            int presentBoardRenderWidth = boardSize * tileWidth;
            int presentX = (width / 2) - (presentBoardRenderWidth / 2);
            int offsetY = (height / 2) - (boardSize * tileWidth / 2);

            // Vị trí X của bàn Past và Future
            int pastX = presentX - presentBoardRenderWidth - spacing;
            int futureX = presentX + presentBoardRenderWidth + spacing;

            // Dessiner les plateaux
            drawPlateau(g2d, past, pastX, offsetY, tileWidth, "PAST");
            drawPlateau(g2d, present, presentX, offsetY, tileWidth, "PRESENT");
            drawPlateau(g2d, future, futureX, offsetY, tileWidth, "FUTURE");

            // Hiển thị thông tin người chơi hiện tại và trạng thái game
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 30));
            String playerTurnText = "Lượt của: " + currentPlayer.getNom();
            int textWidth = g2d.getFontMetrics().stringWidth(playerTurnText);
            g2d.drawString(playerTurnText, (width - textWidth) / 2, 50); // Đặt ở trên cùng

            // Hiển thị số lượng clones còn lại
            g2d.setFont(new Font("Arial", Font.PLAIN, 20));
            g2d.drawString("Clones Blanc: " + jeu.getJoueur1().getNbClones(), 50, 50);
            g2d.drawString("Clones Noir: " + jeu.getJoueur2().getNbClones(), width - 200, 50);


            // Hiển thị kết quả Game Over
            int winner = jeu.gameOver(currentPlayer);
            if (winner != 0) {
                String winnerMsg = (winner == jeu.getJoueur1().getId()) ? "Joueur " + jeu.getJoueur1().getNom() + " a gagné!" : "Joueur " + jeu.getJoueur2().getNom() + " a gagné!";
                g2d.setColor(Color.GREEN); // Màu cho thông báo thắng
                g2d.setFont(new Font("Arial", Font.BOLD, 60));
                FontMetrics metrics = g2d.getFontMetrics();
                int msgWidth = metrics.stringWidth(winnerMsg);
                g2d.drawString(winnerMsg, (width - msgWidth) / 2, height / 2);
            }
        }

        // Render nút Back
        backButton.render(g2d);

        g2d.dispose();
    }

    private void drawPlateau(Graphics g, Plateau plateau, int x, int y, int tileWidth, String title) {
        // Draw the board background
        g.setColor(Color.GRAY);
        g.fillRect(x, y - tileWidth, tileWidth * plateau.getSize(), tileWidth * plateau.getSize() + tileWidth); // Thêm chiều cao cho title

        // Draw title for the plateau
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, tileWidth / 2));
        FontMetrics metrics = g.getFontMetrics();
        int titleWidth = metrics.stringWidth(title);
        g.drawString(title, x + (tileWidth * plateau.getSize() - titleWidth) / 2, y - tileWidth / 4);

        // Draw the pieces
        for (int row = 0; row < plateau.getSize(); row++) {
            for (int col = 0; col < plateau.getSize(); col++) {
                // Draw the tile
                if ((row + col) % 2 == 0) { // Đảm bảo màu ô cờ xen kẽ
                    g.setColor(new Color(0xFFFFFF)); // Ô trắng
                } else {
                    g.setColor(new Color(0x000000)); // Ô đen
                }
                g.fillRect(x + col * tileWidth, y + row * tileWidth, tileWidth, tileWidth);

                Piece piece = plateau.getPiece(row, col);
                if (piece != null) {
                    int ovalSize = tileWidth * 3 / 4; // Size of the oval
                    int ovalX = x + col * tileWidth + (tileWidth - ovalSize) / 2;
                    int ovalY = y + row * tileWidth + (tileWidth - ovalSize) / 2;

                    // Sử dụng ID của người chơi để chọn màu
                    Color color = piece.getOwner().getId() == 1 ? Color.CYAN : Color.MAGENTA; // Màu sắc quân cờ
                    g.setColor(color);
                    g.fillOval(ovalX, ovalY, ovalSize, ovalSize);

                    // Thêm viền cho quân cờ được chọn (ví dụ)
                    if (selectedPiecePosition != null &&
                        selectedPiecePosition.x == row && selectedPiecePosition.y == col &&
                        plateau.getType().equals(selectedPlateauType)) {
                        g.setColor(Color.YELLOW); // Màu viền cho quân đang chọn
                        ((Graphics2D)g).setStroke(new BasicStroke(3)); // Độ dày của viền
                        g.drawOval(ovalX, ovalY, ovalSize, ovalSize);
                        ((Graphics2D)g).setStroke(new BasicStroke(1)); // Reset độ dày
                    }
                }
            }
        }
    }

    @Override
    public void dispose() {
        // Gỡ bỏ các listener khi scene không còn hoạt động
        if (sceneManager != null && sceneManager.getPanel() != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapter);
            sceneManager.getPanel().removeMouseMotionListener(mouseMotionAdapter);
        }
    }

    public void updateLastLogin(int loginType) {
        lastLogin = loginType;
    }

    // Phương thức để thiết lập các MouseListener an toàn
    private void setupMouseListeners() {
        // MouseAdapter cho các sự kiện click/press/release
        mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) { // Đảm bảo scene đã fade-in hoàn toàn
                    Point mousePoint = e.getPoint();

                    // Xử lý click nút Back
                    if (backButton.contains(mousePoint)) {
                        backButton.onClick();
                        return; // Đã xử lý sự kiện
                    }

                    // --- XỬ LÝ INPUT TRÒ CHƠI VÀ GỌI MODEL ---
                    if (jeu != null) {
                        // Xác định Plateau nào được click và vị trí trên Plateau đó
                        Plateau clickedPlateau = null;
                        int clickedRow = -1;
                        int clickedCol = -1;

                        // Lấy kích thước ô và khoảng cách
                        int boardSize = jeu.TAILLE;
                        int tileWidth = (sceneManager.getPanel().getWidth() / 50); // Kích thước ô cơ bản
                        int spacing = tileWidth * 2; // Khoảng cách giữa các bàn
                        int presentBoardRenderWidth = boardSize * tileWidth;
                        int presentX = (sceneManager.getPanel().getWidth() / 2) - (presentBoardRenderWidth / 2);
                        int offsetY = (sceneManager.getPanel().getHeight() / 2) - (boardSize * tileWidth / 2);

                        // Kiểm tra xem click vào bàn nào
                        if (mousePoint.x >= (presentX - presentBoardRenderWidth - spacing) && mousePoint.x < (presentX - spacing)) { // Past
                            clickedPlateau = jeu.getPast();
                            clickedCol = (mousePoint.x - (presentX - presentBoardRenderWidth - spacing)) / tileWidth;
                            clickedRow = (mousePoint.y - offsetY) / tileWidth;
                        } else if (mousePoint.x >= presentX && mousePoint.x < (presentX + presentBoardRenderWidth)) { // Present
                            clickedPlateau = jeu.getPresent();
                            clickedCol = (mousePoint.x - presentX) / tileWidth;
                            clickedRow = (mousePoint.y - offsetY) / tileWidth;
                        } else if (mousePoint.x >= (presentX + presentBoardRenderWidth + spacing) && mousePoint.x < (presentX + presentBoardRenderWidth * 2 + spacing)) { // Future
                            clickedPlateau = jeu.getFuture();
                            clickedCol = (mousePoint.x - (presentX + presentBoardRenderWidth + spacing)) / tileWidth;
                            clickedRow = (mousePoint.y - offsetY) / tileWidth;
                        }

                        // Đảm bảo click nằm trong ranh giới của bàn
                        if (clickedPlateau != null && clickedRow >= 0 && clickedRow < boardSize && clickedCol >= 0 && clickedCol < boardSize) {
                            Piece clickedPiece = clickedPlateau.getPiece(clickedRow, clickedCol);
                            Joueur currentPlayer = jeu.getJoueurCourant();

                            if (selectedPiecePosition == null) { // Chưa có quân cờ nào được chọn
                                if (clickedPiece != null && clickedPiece.getOwner().equals(currentPlayer)) {
                                    // Chọn quân cờ của người chơi hiện tại
                                    selectedPiecePosition = new Point(clickedRow, clickedCol);
                                    selectedPlateauType = clickedPlateau.getType();
                                    // Có thể hiển thị menu ngữ cảnh để chọn loại hành động (MOVE, JUMP, CLONE)
                                    // Để đơn giản, giả sử mặc định là MOVE hoặc có nút chọn hành động trên UI
                                    nextActionType = Coup.TypeCoup.MOVE; // Mặc định là di chuyển
                                    System.out.println("Đã chọn quân tại " + selectedPiecePosition + " trên " + selectedPlateauType);
                                } else {
                                    System.out.println("Chọn một quân cờ của bạn để bắt đầu.");
                                    selectedPiecePosition = null; // Đảm bảo reset nếu chọn sai
                                }
                            } else { // Đã có quân cờ được chọn
                                // Xác định hành động cần thực hiện
                                // Nếu click vào chính quân cờ đã chọn lần nữa -> bỏ chọn
                                if (selectedPiecePosition.x == clickedRow && selectedPiecePosition.y == clickedCol &&
                                    selectedPlateauType.equals(clickedPlateau.getType())) {
                                    selectedPiecePosition = null;
                                    selectedPlateauType = null;
                                    nextActionType = null;
                                    System.out.println("Đã bỏ chọn quân cờ.");
                                } else {
                                    // Đây là click đích hoặc chọn loại hành động mới
                                    // Lấy Piece thật từ model (quan trọng vì Coup cần tham chiếu Piece thực)
                                    Piece pieceToMove = jeu.getPlateauByType(selectedPlateauType).getPiece(selectedPiecePosition.x, selectedPiecePosition.y);
                                    if (pieceToMove == null) { // Trường hợp đã bị xóa (ví dụ Paradoxe)
                                        selectedPiecePosition = null;
                                        selectedPlateauType = null;
                                        nextActionType = null;
                                        System.out.println("Quân cờ đã chọn không còn tồn tại.");
                                        return;
                                    }

                                    // Tạo đối tượng Coup dựa trên loại hành động đã chọn
                                    Coup coup = null;
                                    Point direction = null; // Chỉ dùng cho MOVE
                                    Plateau currentPlateau = jeu.getPlateauByType(selectedPlateauType); // Plateau gốc của quân được chọn

                                    if (nextActionType == Coup.TypeCoup.MOVE) {
                                        // Tính direction (dx, dy)
                                        int dx = clickedRow - selectedPiecePosition.x;
                                        int dy = clickedCol - selectedPiecePosition.y;
                                        // Kiểm tra xem đây có phải là nước đi hợp lệ 1 ô vuông không (move, jump, clone)
                                        // Trong "That Time You Killed Me", nước đi thường là 1 ô đơn vị
                                        if ((Math.abs(dx) == 1 && dy == 0) || (Math.abs(dy) == 1 && dx == 0)) {
                                            direction = new Point(dx, dy);
                                            coup = new Coup(pieceToMove, direction, currentPlateau, Coup.TypeCoup.MOVE);
                                        } else {
                                            System.out.println("Nước đi không hợp lệ cho MOVE.");
                                            // Không reset selection nếu người chơi cần chọn lại đích
                                            return;
                                        }
                                    } else if (nextActionType == Coup.TypeCoup.JUMP) {
                                        // Logic cho JUMP: không cần direction, chỉ cần Piece và Plateau gốc
                                        // Đích đến sẽ được xử lý trong logic Jeu (jump đến Present/Future)
                                        coup = new Coup(pieceToMove, new Point(0,0), currentPlateau, Coup.TypeCoup.JUMP);
                                    } else if (nextActionType == Coup.TypeCoup.CLONE) {
                                        // Logic cho CLONE: không cần direction
                                        coup = new Coup(pieceToMove, new Point(0,0), currentPlateau, Coup.TypeCoup.CLONE);
                                    }

                                    if (coup != null) {
                                        boolean success = jeu.faireCoup(coup);
                                        if (success) {
                                            System.out.println("Nước đi thành công!");
                                            // Chuyển lượt chơi (Chỉ cho Single Player)
                                            // Nếu là Multiplayer, server sẽ quyết định chuyển lượt
                                            // Và gửi trạng thái mới về client để cập nhật.
                                            // Ví dụ cho Single Player:
                                            // if (lastLogin == 0) {
                                            //    jeu.setJoueurCourant(jeu.getJoueurCourant().equals(jeu.getJoueur1()) ? jeu.getJoueur2() : jeu.getJoueur1());
                                            // }
                                        } else {
                                            System.out.println("Nước đi không hợp lệ hoặc không thể thực hiện.");
                                        }
                                    }

                                    // Reset lựa chọn sau khi thực hiện (hoặc cố gắng thực hiện) nước đi
                                    selectedPiecePosition = null;
                                    selectedPlateauType = null;
                                    nextActionType = null;
                                }
                            }
                        } else {
                            System.out.println("Click không nằm trên bàn cờ hợp lệ.");
                            selectedPiecePosition = null; // Reset nếu click ra ngoài
                            selectedPlateauType = null;
                            nextActionType = null;
                        }
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // Xử lý hiệu ứng khi nhấn nút Back (nếu backButton là lớp Button)
                if (backButton.contains(e.getPoint())) {
                    backButton.setClicked(true);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // Reset hiệu ứng khi nhả nút Back (nếu backButton là lớp Button)
                backButton.setClicked(false);
            }
        };
        sceneManager.getPanel().addMouseListener(mouseAdapter);

        // MouseMotionAdapter cho các sự kiện hover
        mouseMotionAdapter = new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                // Cập nhật trạng thái hover của các Button
                backButton.update(e.getPoint());
            }
        };
        sceneManager.getPanel().addMouseMotionListener(mouseMotionAdapter);
    }

    // Cần một phương thức trong Jeu để lấy Plateau theo TypePlateau
    // Ví dụ:
    // Trong Jeu.java:
    // public Plateau getPlateauByType(Plateau.TypePlateau type) {
    //     switch (type) {
    //         case PAST: return past;
    //         case PRESENT: return present;
    //         case FUTURE: return future;
    //         default: return null;
    //     }
    // }
}