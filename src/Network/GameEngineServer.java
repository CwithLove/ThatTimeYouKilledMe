package Network;

import Modele.Jeu; // Import lớp Jeu của bạn
import Modele.Coup; // Import lớp Coup của bạn
import Modele.Plateau; // Import lớp Plateau của bạn
import Modele.Piece; // Import lớp Piece của bạn
import Modele.Joueur; // Import lớp Joueur của bạn

import java.awt.Point;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List; // Thêm import này
import java.util.ArrayList; // Thêm import này

public class GameEngineServer implements Runnable {
    private Jeu game; // Instance của Modele.Jeu của bạn
    private BlockingQueue<Message> incomingClientMessages; // Queue tin nhắn đến từ tất cả client
    private Map<Integer, BlockingQueue<String>> outgoingClientQueues; // Map các queue gửi đi cho từng client
    private int currentTurnPlayerId; // ID của người chơi đang đến lượt
    private Map<Integer, Joueur> playerMap; // Ánh xạ Client ID với đối tượng Joueur trong game
    private List<Integer> connectedClientIds; // Danh sách các Client ID đã kết nối, theo thứ tự

    public GameEngineServer(BlockingQueue<Message> incomingMessages,
            Map<Integer, BlockingQueue<String>> outgoingQueues,
            List<Integer> connectedClientIds) {
        this.incomingClientMessages = incomingMessages;
        this.outgoingClientQueues = outgoingQueues;
        this.connectedClientIds = connectedClientIds;
        this.game = new Jeu(); // Khởi tạo Modele.Jeu của bạn

        playerMap = new ConcurrentHashMap<>();
        // Gán Joueur 1 cho client đầu tiên kết nối, Joueur 2 cho client thứ hai
        if (connectedClientIds.size() >= 1) {
            playerMap.put(connectedClientIds.get(0), game.getJoueur1()); // Client đầu tiên điều khiển J1
        }
        if (connectedClientIds.size() >= 2) {
            playerMap.put(connectedClientIds.get(1), game.getJoueur2()); // Client thứ hai điều khiển J2
        }

        this.currentTurnPlayerId = game.getJoueurCourant().getId(); // Lượt của người chơi đầu tiên
    }

    @Override
    public void run() {
        System.out.println("GameEngineServer: Đã khởi động vòng lặp game.");
        try {
            // Gửi trạng thái game ban đầu đến tất cả các client
            // Đợi một chút để đảm bảo client đã nhận đủ luồng và sẵn sàng nhận dữ liệu
            Thread.sleep(500);
            sendGameStateToAllClients();

            while (true) {
                // Đợi tin nhắn từ client (blocking call)
                Message msg = incomingClientMessages.take();

                // Lấy Joueur ứng với client ID gửi tin nhắn
                Joueur senderJoueur = playerMap.get(msg.clientId);

                // Kiểm tra xem tin nhắn có phải từ người chơi đang đến lượt và có phải quân của
                // họ không
                if (senderJoueur == null || !senderJoueur.equals(game.getJoueurCourant())) {
                    System.out.println("GameEngineServer: Tin nhắn từ người chơi " + msg.clientId
                            + " không đến lượt hoặc không hợp lệ. Bỏ qua.");
                    sendMessageToClient(msg.clientId,
                            Code.ADVERSAIRE.name() + ":" + "Không phải lượt của bạn hoặc lệnh không hợp lệ.");
                    continue;
                }

                System.out.println("GameEngineServer: Nhận từ client " + msg.clientId + ": " + msg.contenu);

                // Phân tích cú pháp lệnh từ client và áp dụng coup
                // Định dạng lệnh từ client:
                // <TYPE_COUP>:<PLATEAU_TYPE>:<ROW>:<COL>[:<DIR_DX>:<DIR_DY>]
                // Ví dụ: MOVE:PRESENT:1:2:0:1 (di chuyển quân ở (1,2) trên Present sang phải 1
                // ô)
                // CLONE:PRESENT:0:0 (clone quân ở (0,0) trên Present)
                // JUMP:PAST:2:1 (jump quân ở (2,1) trên Past)

                String[] parts = msg.contenu.split(":");
                if (parts.length < 4) { // Lệnh tối thiểu phải có TypeCoup, PlateauType, Row, Col
                    System.err.println("GameEngineServer: Lệnh không đúng định dạng: " + msg.contenu);
                    sendMessageToClient(msg.clientId, Code.ADVERSAIRE.name() + ":" + "Lệnh không đúng định dạng.");
                    continue;
                }

                try {
                    Coup.TypeCoup typeCoup = Coup.TypeCoup.valueOf(parts[0]);
                    Plateau.TypePlateau plateauType = Plateau.TypePlateau.valueOf(parts[1]);
                    int row = Integer.parseInt(parts[2]);
                    int col = Integer.parseInt(parts[3]);

                    Point direction = new Point(0, 0); // Default cho CLONE/JUMP
                    if (typeCoup == Coup.TypeCoup.MOVE) {
                        if (parts.length == 6) { // MOVE cần có dx, dy
                            int dx = Integer.parseInt(parts[4]);
                            int dy = Integer.parseInt(parts[5]);
                            direction = new Point(dx, dy);
                        } else {
                            System.err.println("GameEngineServer: Lệnh MOVE thiếu thông tin hướng: " + msg.contenu);
                            sendMessageToClient(msg.clientId,
                                    Code.ADVERSAIRE.name() + ":" + "Lệnh MOVE thiếu thông tin hướng.");
                            continue;
                        }
                    }

                    // Lấy quân cờ và người chơi tương ứng
                    Plateau selectedPlateau = game.getPlateauByType(plateauType);
                    if (selectedPlateau == null) {
                        System.err.println("GameEngineServer: Plateau không hợp lệ: " + plateauType);
                        sendMessageToClient(msg.clientId, Code.ADVERSAIRE.name() + ":" + "Plateau không hợp lệ.");
                        continue;
                    }

                    Piece selectedPiece = selectedPlateau.getPiece(row, col);
                    // Kiểm tra xem quân cờ có thực sự thuộc về người chơi hiện tại không
                    if (selectedPiece == null || !selectedPiece.getOwner().equals(senderJoueur)) {
                        System.out.println("GameEngineServer: Người chơi " + msg.clientId
                                + " đã chọn một quân cờ không thuộc về họ hoặc không tồn tại tại vị trí đó.");
                        sendMessageToClient(msg.clientId, Code.ADVERSAIRE.name() + ":" + "Quân cờ không hợp lệ.");
                        continue;
                    }

                    // Tạo Coup và áp dụng
                    Coup playerCoup = new Coup(selectedPiece, direction, selectedPlateau, typeCoup);

                    boolean isValid = game.estCoupValide(playerCoup); // Kiểm tra tính hợp lệ của coup

                    if (isValid) {
                        game.appliquerCoup(playerCoup, senderJoueur, game.getPast(), game.getPresent(),
                                game.getFuture());
                        // Lượt chơi đã được chuyển bên trong appliquerCoup.
                        // Cập nhật currentTurnPlayerId của GameEngineServer
                        currentTurnPlayerId = game.getJoueurCourant().getId();
                        System.out.println(
                                "GameEngineServer: Coup hợp lệ. Lượt mới của người chơi: " + currentTurnPlayerId);
                    } else {
                        System.out.println("GameEngineServer: Coup không hợp lệ từ người chơi " + msg.clientId
                                + ". Vui lòng thử lại.");
                        sendMessageToClient(msg.clientId, Code.ADVERSAIRE.name() + ":" + "Nước đi không hợp lệ.");
                    }

                    // Gửi trạng thái game mới đến tất cả các client
                    sendGameStateToAllClients();

                    // Kiểm tra Game Over (Kiểm tra cả hai người chơi)
                    int winnerId = game.gameOver(game.getJoueur1());
                    if (winnerId == 0) {
                        winnerId = game.gameOver(game.getJoueur2());
                    }

                    if (winnerId != 0) {
                        String winnerMsg = Code.GAGNE.name() + ":" + winnerId; // Dùng mã GAGNE để client biết
                        if (winnerId == game.getJoueur1().getId()) {
                            winnerMsg += ":Chúc mừng " + game.getJoueur1().getNom() + " đã thắng!";
                        } else {
                            winnerMsg += ":Chúc mừng " + game.getJoueur2().getNom() + " đã thắng!";
                        }
                        sendStateToAllClients(winnerMsg);
                        System.out.println("GameEngineServer: Game Over! Người thắng: " + winnerId);
                        // Có thể thêm logic để dừng server hoặc reset game
                        break;
                    }
                } catch (IllegalArgumentException e) {
                    System.err.println("GameEngineServer: Lỗi phân tích lệnh hoặc giá trị không hợp lệ: " + e.getMessage());
                    e.printStackTrace();
                } catch (Exception e) { // Bắt lỗi do valueOf hoặc parseInt sai
                    System.err.println("GameEngineServer: Lỗi trong xử lý message: " + e.getMessage());
                    e.printStackTrace(); // In stack trace để debug
                    // Có thể gửi lỗi về client nếu muốn
                    // sendMessageToClient(msg.clientId, Code.ADVERSAIRE.name() + ":" + "Lỗi lệnh
                    // server: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("GameEngineServer: Lỗi trong vòng lặp game: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    // Helper method để gửi trạng thái game đến tất cả client
    private void sendGameStateToAllClients() {
        String gameStateString = game.getGameStateAsString();
        String messageToSend = Code.ETAT.name() + ":" + gameStateString; // Thêm mã ETAT vào đầu
        sendStateToAllClients(messageToSend);
        System.out.println("GameEngineServer: Đã gửi trạng thái game mới: " + messageToSend);
    }

    // Gửi tin nhắn tới tất cả các client đang hoạt động
    private void sendStateToAllClients(String message) {
        for (Integer clientId : connectedClientIds) { // Sử dụng danh sách client IDs để đảm bảo thứ tự
            BlockingQueue<String> queue = outgoingClientQueues.get(clientId);
            if (queue != null) {
                try {
                    queue.put(message);
                } catch (InterruptedException e) {
                    System.err.println(
                            "GameEngineServer: Lỗi gửi tin nhắn đến client " + clientId + ": " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    // Gửi tin nhắn riêng tới một client cụ thể
    private void sendMessageToClient(int clientId, String message) {
        BlockingQueue<String> clientQueue = outgoingClientQueues.get(clientId);
        if (clientQueue != null) {
            try {
                clientQueue.put(message);
            } catch (InterruptedException e) {
                System.err.println("GameEngineServer: Lỗi gửi tin nhắn đến client " + clientId + ": " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }
}