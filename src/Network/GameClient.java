package Network; // Đảm bảo dòng này có ở đầu file

import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.Point;

// Các import Modele cần thiết (để GameClient có thể tạo ra các đối tượng Modele khi phân tích trạng thái)
import Modele.Jeu;
import Modele.Plateau;
import Modele.Piece;

public class GameClient { // Đã đổi tên từ Client (của contributor)
    private String ipAddress; // Đổi tên từ ip
    private Jeu gameInstance; // Đây sẽ là bản sao cục bộ của trạng thái game trên client
    private ObjectOutputStream outputStream; // Để gửi tin nhắn đến server
    private Socket socket; // Socket kết nối đến server
    private GameStateUpdateListener listener; // Listener để thông báo cho GUI
    private int clientId; // ID của client này, nhận từ server
    private Thread receptionThread; // Luồng nhận dữ liệu

    public GameClient(String ipAddress, GameStateUpdateListener listener) {
        this.ipAddress = ipAddress;
        this.listener = listener;
        this.gameInstance = new Jeu(); // Khởi tạo bản sao game cục bộ
        // Các đối tượng Joueur trong gameInstance này là bản sao, cần được gán đúng ID
        // Server sẽ gửi thông tin ID của client này
    }

    public void connect() throws IOException {
        System.out.println("GameClient: Đang cố gắng kết nối tới server " + ipAddress + ":1234...");
        socket = new Socket(ipAddress, 1234);
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        outputStream.flush(); // Quan trọng: flush header trước khi ObjectInputStream được tạo ở server

        ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());

        // Bắt đầu luồng nhận dữ liệu từ server
        receptionThread = new Thread(() -> {
            try {
                while (true) {
                    Object obj = inputStream.readObject();
                    if (obj instanceof String) {
                        String data = (String) obj;
                        // System.out.println("GameClient - Server Raw: " + data); // Để debug tin nhắn raw từ server

                        String[] parts = data.split(":", 2); // Tách CODE và CONTENT
                        if (parts.length < 2) {
                            System.err.println("GameClient: Lỗi: Tin nhắn từ server không đúng định dạng Code:Content -> " + data);
                            listener.onGameMessage("ERROR", "Tin nhắn server không hợp lệ.");
                            continue;
                        }
                        Code code = Code.valueOf(parts[0]); // Lấy Code từ phần đầu
                        String content = parts[1]; // Lấy nội dung chính từ phần sau

                        switch (code) {
                            case ETAT:
                                // Phân tích chuỗi trạng thái game và cập nhật gameInstance cục bộ
                                parseAndApplyGameStateString(content); 
                                // Thông báo cho GUI để cập nhật
                                if (listener != null) {
                                    // Đảm bảo cập nhật UI trên Event Dispatch Thread (EDT)
                                    javax.swing.SwingUtilities.invokeLater(() -> listener.onGameStateUpdate(gameInstance));
                                }
                                break;
                            case ADVERSAIRE: // Tin nhắn lỗi hoặc thông báo lượt đối thủ
                                System.out.println("GameClient: Thông báo Server -> " + content);
                                if (listener != null) {
                                    javax.swing.SwingUtilities.invokeLater(() -> listener.onGameMessage("INFO", content));
                                }
                                break;
                            case GAGNE: // Thông báo thắng
                                System.out.println("GameClient: Bạn đã thắng! " + content);
                                if (listener != null) {
                                    javax.swing.SwingUtilities.invokeLater(() -> listener.onGameMessage("WIN", content));
                                }
                                break;
                            case PERDU: // Thông báo thua
                                System.out.println("GameClient: Bạn đã thua! " + content);
                                if (listener != null) {
                                    javax.swing.SwingUtilities.invokeLater(() -> listener.onGameMessage("LOSE", content));
                                }
                                break;
                            // Có thể thêm các case khác nếu server gửi các loại lệnh khác
                            default:
                                System.out.println("GameClient: Lệnh Server không rõ: " + code.name() + " - Nội dung: " + content);
                                if (listener != null) {
                                    javax.swing.SwingUtilities.invokeLater(() -> listener.onGameMessage("UNKNOWN", content));
                                }
                                break;
                        }
                    }
                }
            } catch (SocketException se) {
                System.err.println("GameClient: Server đã đóng kết nối hoặc lỗi mạng: " + se.getMessage());
                if (listener != null) {
                    javax.swing.SwingUtilities.invokeLater(() -> listener.onGameMessage("DISCONNECTED", "Mất kết nối với Server."));
                }
            } catch (EOFException eofe) {
                System.err.println("GameClient: Server đã đóng kết nối.");
                if (listener != null) {
                    javax.swing.SwingUtilities.invokeLater(() -> listener.onGameMessage("DISCONNECTED", "Server đã đóng kết nối."));
                }
            } catch (Exception e) {
                System.err.println("GameClient: Lỗi nhận dữ liệu từ server: " + e.getMessage());
                e.printStackTrace();
                if (listener != null) {
                    javax.swing.SwingUtilities.invokeLater(() -> listener.onGameMessage("ERROR", "Lỗi nhận dữ liệu: " + e.getMessage()));
                }
            } finally {
                // Đảm bảo đóng socket khi luồng kết thúc
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        });
        receptionThread.start();
        System.out.println("GameClient: Đã kết nối thành công tới server.");
    }

    // Phương thức để gửi hành động của người chơi đến server
    public void sendPlayerAction(String actionString) {
        try {
            if (outputStream != null) {
                outputStream.writeObject(actionString);
                outputStream.flush();
                // System.out.println("GameClient: Đã gửi hành động: " + actionString);
            }
        } catch (IOException e) {
            System.err.println("GameClient: Lỗi gửi hành động đến server: " + e.getMessage());
            e.printStackTrace();
            if (listener != null) {
                javax.swing.SwingUtilities.invokeLater(() -> listener.onGameMessage("ERROR", "Lỗi gửi hành động: " + e.getMessage()));
            }
        }
    }

    // Phương thức để lấy instance game cục bộ (đã được cập nhật)
    public Jeu getGameInstance() {
        return gameInstance;
    }

    // Phương thức để đóng kết nối client
    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("GameClient: Đã đóng kết nối.");
            }
            if (receptionThread != null && receptionThread.isAlive()) {
                receptionThread.interrupt(); // Ngắt luồng nhận
            }
        } catch (IOException e) {
            System.err.println("GameClient: Lỗi khi đóng kết nối: " + e.getMessage());
        }
    }

    // Phương thức mới để phân tích chuỗi trạng thái game và cập nhật gameInstance
    private void parseAndApplyGameStateString(String gameStateString) {
        // Định dạng chuỗi: CP:1;C1:4;C2:4;GS:IN_PROGRESS;W:0;P:________;PR:________;F:________
        String[] parts = gameStateString.split(";");
        Map<String, String> gameStateMap = new HashMap<>();
        for (String part : parts) {
            String[] keyValue = part.split(":", 2); // Tách key và value
            if (keyValue.length == 2) {
                gameStateMap.put(keyValue[0], keyValue[1]);
            }
        }

        // Cập nhật JoueurCourant
        int currentPlayerId = Integer.parseInt(gameStateMap.getOrDefault("CP", "0"));
        if (currentPlayerId == gameInstance.getJoueur1().getId()) {
            gameInstance.setJoueurCourant(gameInstance.getJoueur1());
        } else if (currentPlayerId == gameInstance.getJoueur2().getId()) {
            gameInstance.setJoueurCourant(gameInstance.getJoueur2());
        } else {
            System.err.println("GameClient: Lỗi: ID người chơi hiện tại không hợp lệ trong chuỗi trạng thái.");
        }

        // Cập nhật số lượng clones
        gameInstance.getJoueur1().setNbClones(Integer.parseInt(gameStateMap.getOrDefault("C1", "0")));
        gameInstance.getJoueur2().setNbClones(Integer.parseInt(gameStateMap.getOrDefault("C2", "0")));

        // Cập nhật trạng thái các Plateau
        updatePlateauFromString(gameInstance.getPast(), gameStateMap.get("P"));
        updatePlateauFromString(gameInstance.getPresent(), gameStateMap.get("PR"));
        updatePlateauFromString(gameInstance.getFuture(), gameStateMap.get("F"));

        // Có thể cập nhật trạng thái game over và người thắng nếu cần
        // String gameStatus = gameStateMap.get("GS");
        // int winner = Integer.parseInt(gameStateMap.get("W"));
    }

    // Helper method để cập nhật một Plateau từ chuỗi 16 ký tự
    private void updatePlateauFromString(Plateau plateau, String boardString) {
        if (boardString == null || boardString.length() != Jeu.TAILLE * Jeu.TAILLE) {
            System.err.println("GameClient: Lỗi: Chuỗi board không hợp lệ hoặc sai kích thước cho plateau " + plateau.getType());
            return;
        }
        // Xóa tất cả các quân cờ hiện có trên Plateau trước khi cập nhật
        // và reset lại số lượng quân
        plateau.resetCounts(); 
        for (int r = 0; r < Jeu.TAILLE; r++) {
            for (int c = 0; c < Jeu.TAILLE; c++) {
                plateau.removePiece(r, c); // Đảm bảo ô trống
            }
        }
        
        // Cập nhật quân cờ dựa trên chuỗi
        for (int i = 0; i < Jeu.TAILLE; i++) {
            for (int j = 0; j < Jeu.TAILLE; j++) {
                char pieceChar = boardString.charAt(i * Jeu.TAILLE + j);
                Piece newPiece = null;
                if (pieceChar == '1') { // Quân của Joueur 1
                    newPiece = new Piece(gameInstance.getJoueur1(), new Point(i, j));
                } else if (pieceChar == '2') { // Quân của Joueur 2
                    newPiece = new Piece(gameInstance.getJoueur2(), new Point(i, j));
                }
                
                if (newPiece != null) {
                    plateau.setPiece(newPiece, i, j);
                    // Cập nhật số lượng quân trên Plateau sau khi đặt Piece
                    if (newPiece.getOwner().equals(gameInstance.getJoueur1())) {
                        plateau.incBlancs();
                    } else if (newPiece.getOwner().equals(gameInstance.getJoueur2())) {
                        plateau.incNoirs();
                    }
                }
            }
        }
    }
}