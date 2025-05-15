package Network;

import Modele.Coup;
import Modele.Jeu;
import Modele.Joueur;
import Modele.Piece;
import Modele.Plateau;
import java.awt.Point;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AIClient implements GameStateUpdateListener, Runnable {
    private String serverIpAddress;
    private int serverPort = 12345;
    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private volatile boolean isRunning = false;
    private int myPlayerId = -1;
    private Jeu gameInstance; 
    private Random random = new Random();
    private final String aiName = "Bot Adversaire"; 

    public AIClient(String serverIpAddress) {
        this.serverIpAddress = serverIpAddress;
        this.gameInstance = new Jeu(); // Initialiser l'instance de jeu
    }

    public void connect() throws IOException {
        System.out.println(aiName + ": Tentative de connexion à " + serverIpAddress + ":" + serverPort);
        try {
            socket = new Socket(serverIpAddress, serverPort);
            // Assurez-vous de créer les flux dans le bon ordre
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush(); // Il est nécessaire de vider le flux de sortie
            
            // Créez le flux d'entrée après avoir vidé le flux de sortie
            inputStream = new ObjectInputStream(socket.getInputStream());
            System.out.println(aiName + ": Connecté au serveur.");
            try {
                Object idMessageObj = inputStream.readObject();
                if (idMessageObj instanceof String) {
                    String idMessage = (String) idMessageObj;
                    if (idMessage.startsWith("ID:")) {
                        this.myPlayerId = Integer.parseInt(idMessage.substring(3));
                        System.out.println(aiName + ": Mon ID de joueur est : " + this.myPlayerId);
                    } else {
                        throw new IOException(aiName + ": Message d'ID initial invalide: " + idMessage);
                    }
                } else {
                    throw new IOException(aiName + ": Type de message d'ID initial invalide.");
                }
            } catch (ClassNotFoundException e) {
                throw new IOException(aiName + ": Erreur lecture ID.", e);
            }
        } catch (IOException e) {
            System.err.println(aiName + ": Erreur connexion: " + e.getMessage());
            throw e;
        }
        isRunning = true;
    }

    public void disconnect() {
        isRunning = false;
        try {
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println(aiName + ": Déconnecté.");
        } catch (IOException e) {
            System.err.println(aiName + ": Erreur déconnexion: " + e.getMessage());
        }
    }

    public void sendPlayerAction(String actionString) {
        if (outputStream == null || socket == null || socket.isClosed() || !isRunning) {
            System.err.println(aiName + ": Non connecté, impossible d'envoyer: " + actionString);
            return;
        }
        try {
            outputStream.writeObject(actionString);
            outputStream.flush();
            System.out.println(aiName + " (ID: " + myPlayerId + "): Action envoyée -> " + actionString);
        } catch (IOException e) {
            System.err.println(aiName + ": Erreur envoi action: " + e.getMessage());
            disconnect(); // Ngắt kết nối nếu có lỗi gửi
        }
    }

    @Override
    public void run() {
        if (inputStream == null) {
            System.err.println(aiName + ": InputStream non prêt. Arrêt du thread AI.");
            isRunning = false;
            return;
        }
        try {
            while (isRunning) {
                Object obj = inputStream.readObject();
                if (obj instanceof String) {
                    String data = (String) obj;
                    // System.out.println(aiName + " - Server Raw: " + data); // Debug

                    String[] parts = data.split(":", 2);
                    if (parts.length < 2) {
                        System.err.println(aiName + ": Message serveur mal formaté: " + data);
                        continue;
                    }
                    Code code;
                     try {
                        code = Code.valueOf(parts[0].toUpperCase());
                    } catch (IllegalArgumentException e) {
                        System.err.println(aiName + ": Code inconnu du serveur: " + parts[0]);
                        continue;
                    }
                    String content = parts[1];

                    switch (code) {
                        case ETAT:
                            GameStateParser.parseAndUpdateJeu(this.gameInstance, content);
                            onGameStateUpdate(this.gameInstance); // Mettre à jour l'état du jeu
                            break;
                        case GAGNE:
                        case PERDU:
                        case ADVERSAIRE:
                            onGameMessage(code.name(), content);
                            break;
                        default:
                            System.out.println(aiName + ": Commande serveur non gérée: " + code.name());
                            break;
                    }
                }
            }
        } catch (SocketException | EOFException e) {
            if (isRunning) System.out.println(aiName + ": Connexion serveur perdue. " + e.getMessage());
        } catch (IOException | ClassNotFoundException e) {
            if (isRunning) {
                System.err.println(aiName + ": Erreur réception données: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            disconnect();
        }
    }

    @Override
    public void onGameStateUpdate(Jeu newGameState) { // newGameState et this.gameInstance mis à jour
        // this.gameInstance đã được cập nhật bởi GameStateParser trước khi gọi hàm này.
        if (this.gameInstance == null || this.gameInstance.getJoueurCourant() == null) {
            System.out.println(aiName + " (ID: " + myPlayerId + "): État du jeu invalide reçu pour décision.");
            return;
        }

        // System.out.println(aiName + " (ID: " + myPlayerId + "): État jeu MAJ. Tour de Joueur ID: " + this.gameInstance.getJoueurCourant().getId());

        if (this.gameInstance.getJoueurCourant().getId() == this.myPlayerId) {
            System.out.println(aiName + " (ID: " + myPlayerId + "): C'est mon tour ! Prise de décision...");
            try {
                Thread.sleep(500 + random.nextInt(1500)); // AI "suy nghĩ" 0.5-2 giây
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println(aiName + ": Thread interrompu pendant la réflexion.");
                return;
            }
            makeDecisionAndPlay();
        }
    }

    private void makeDecisionAndPlay() {
        if (gameInstance == null) return;

        Joueur aiPlayer = (gameInstance.getJoueur1().getId() == myPlayerId) ? gameInstance.getJoueur1() : gameInstance.getJoueur2();

        // Chiến lược AI rất cơ bản
        List<Piece> aiPiecesPresent = findMyPiecesOnBoard(gameInstance.getPresent(), aiPlayer);
        List<Piece> aiPiecesPast = findMyPiecesOnBoard(gameInstance.getPast(), aiPlayer);
        List<Piece> aiPiecesFuture = findMyPiecesOnBoard(gameInstance.getFuture(), aiPlayer);

        // 1. Thử CLONE từ PRESENT
        if (aiPlayer.getNbClones() > 0 && !aiPiecesPresent.isEmpty()) {
            for (Piece p : aiPiecesPresent) { // Nên chọn ngẫu nhiên hoặc ưu tiên
                 // Kiểm tra đơn giản: nếu ô đích trên PAST không có quân của AI
                Piece destPiecePast = gameInstance.getPast().getPiece(p.getPosition().x, p.getPosition().y);
                if (destPiecePast == null || destPiecePast.getOwner().getId() != myPlayerId) {
                    sendPlayerAction(Coup.TypeCoup.CLONE.name() + ":" + Plateau.TypePlateau.PRESENT.name() + ":" + p.getPosition().x + ":" + p.getPosition().y);
                    return;
                }
            }
        }

        // 2. Thử JUMP từ PAST
        if (!aiPiecesPast.isEmpty()) {
            for (Piece p : aiPiecesPast) {
                Piece destPiecePresent = gameInstance.getPresent().getPiece(p.getPosition().x, p.getPosition().y);
                if (destPiecePresent == null || destPiecePresent.getOwner().getId() != myPlayerId) {
                    sendPlayerAction(Coup.TypeCoup.JUMP.name() + ":" + Plateau.TypePlateau.PAST.name() + ":" + p.getPosition().x + ":" + p.getPosition().y);
                    return;
                }
            }
        }
        
        // 3. Thử CLONE từ FUTURE
        if (aiPlayer.getNbClones() > 0 && !aiPiecesFuture.isEmpty()) {
            for (Piece p : aiPiecesFuture) {
                Piece destPiecePresent = gameInstance.getPresent().getPiece(p.getPosition().x, p.getPosition().y);
                 if (destPiecePresent == null || destPiecePresent.getOwner().getId() != myPlayerId) {
                    sendPlayerAction(Coup.TypeCoup.CLONE.name() + ":" + Plateau.TypePlateau.FUTURE.name() + ":" + p.getPosition().x + ":" + p.getPosition().y);
                    return;
                }
            }
        }

        // 4. Thử MOVE ngẫu nhiên
        List<Piece> allMyPieces = new ArrayList<>();
        allMyPieces.addAll(aiPiecesPresent);
        allMyPieces.addAll(aiPiecesPast);
        allMyPieces.addAll(aiPiecesFuture);

        if (!allMyPieces.isEmpty()) {
            java.util.Collections.shuffle(allMyPieces); // Xáo trộn để có sự ngẫu nhiên hơn
            for(Piece pieceToMove : allMyPieces){
                Plateau.TypePlateau plateauOfPiece = getPlateauTypeOfPiece(pieceToMove, gameInstance);
                if (plateauOfPiece != null) {
                    Point[] directions = {new Point(0, 1), new Point(0, -1), new Point(1, 0), new Point(-1, 0)};
                    java.util.Collections.shuffle(java.util.Arrays.asList(directions)); // Xáo trộn hướng

                    for(Point dir : directions){
                        // AI có thể kiểm tra sơ bộ xem nước đi có ra ngoài bàn cờ không
                        int destX = pieceToMove.getPosition().x + dir.x;
                        int destY = pieceToMove.getPosition().y + dir.y;
                        int boardSize = 4; // 使用固定棋盘大小而不是直接访问私有常量
                        if (destX >= 0 && destX < boardSize && destY >= 0 && destY < boardSize) {
                            // Kiểm tra xem ô đích có quân của mình không
                            Piece pieceAtDest = gameInstance.getPlateauByType(plateauOfPiece).getPiece(destX, destY);
                            // if (pieceAtDest == null || pieceAtDest.getOwner().getId() != myPlayerId) {
                            //     String command = Coup.TypeCoup.MOVE.name() + ":" + plateauOfPiece.name() + ":" +
                            //                      pieceToMove.getPosition().x + ":" + pieceToMove.getPosition().y + ":" +
                            //                      dir.x + ":" + dir.y;
                            //     sendPlayerAction(command);
                            //     return;
                            // }
                        }
                    }
                }
            }
        }
        System.out.println(aiName + " (ID: " + myPlayerId + "): Pas d'action simple trouvée, passe son tour (ou devrait envoyer un NOP?).");
        // Trong tương lai, nếu không có nước đi, AI có thể cần gửi một lệnh "SKIP" hoặc server tự xử lý timeout.
        // Hiện tại, không gửi gì sẽ khiến server chờ.
    }

    private List<Piece> findMyPiecesOnBoard(Plateau board, Joueur myself) {
        List<Piece> myPieces = new ArrayList<>();
        if (board == null || myself == null) return myPieces;
        int boardSize = 4; // 使用固定棋盘大小而不是直接访问私有常量
        for (int r = 0; r < boardSize; r++) {
            for (int c = 0; c < boardSize; c++) {
                Piece p = board.getPiece(r, c);
                if (p != null && p.getOwner().getId() == myself.getId()) {
                    myPieces.add(p);
                }
            }
        }
        return myPieces;
    }

    private Plateau.TypePlateau getPlateauTypeOfPiece(Piece piece, Jeu currentGame) {
        if (currentGame == null || piece == null) return null;
        Point pos = piece.getPosition();
        if (pos == null) return null; // Thêm kiểm tra null cho position

        Plateau past = currentGame.getPast();
        Plateau present = currentGame.getPresent();
        Plateau future = currentGame.getFuture();

        if (past != null && past.getPiece(pos.x, pos.y) == piece) return Plateau.TypePlateau.PAST;
        if (present != null && present.getPiece(pos.x, pos.y) == piece) return Plateau.TypePlateau.PRESENT;
        if (future != null && future.getPiece(pos.x, pos.y) == piece) return Plateau.TypePlateau.FUTURE;
        
        // Fallback nếu piece không được tìm thấy qua so sánh tham chiếu (do có thể là copy)
        // Kiểm tra dựa trên owner và position
        if (past != null) {
            Piece p = past.getPiece(pos.x, pos.y);
            if (p != null && p.getOwner().getId() == myPlayerId && p.getPosition().equals(pos)) return Plateau.TypePlateau.PAST;
        }
         if (present != null) {
            Piece p = present.getPiece(pos.x, pos.y);
            if (p != null && p.getOwner().getId() == myPlayerId && p.getPosition().equals(pos)) return Plateau.TypePlateau.PRESENT;
        }
        if (future != null) {
            Piece p = future.getPiece(pos.x, pos.y);
            if (p != null && p.getOwner().getId() == myPlayerId && p.getPosition().equals(pos)) return Plateau.TypePlateau.FUTURE;
        }
        System.err.println(aiName + ": Impossible de trouver le plateau pour la pièce à " + pos + " appartenant à " + piece.getOwner().getNom());
        return null;
    }

    @Override
    public void onGameMessage(String messageType, String messageContent) {
        System.out.println(aiName + " (ID: " + myPlayerId + "): Message Serveur - " + messageType + ": " + messageContent);
        if ("GAGNE".equalsIgnoreCase(messageType) || "PERDU".equalsIgnoreCase(messageType) || "DISCONNECTED".equalsIgnoreCase(messageType)) {
            System.out.println(aiName + ": Fin de partie ou déconnexion. Arrêt de l'AI.");
            disconnect(); // Dừng AI
        }
    }

    public int getMyPlayerId() {
        return myPlayerId;
    }

    public boolean isConnected() {
        return isRunning && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void startListeningAndPlaying() {
        if (isRunning && inputStream != null && outputStream != null) {
            new Thread(this, aiName + "-Thread-ID-" + myPlayerId).start();
            System.out.println(aiName + " (ID: " + myPlayerId + "): Thread principal de l'AI démarré.");
        } else {
            System.err.println(aiName + ": AI non prête pour démarrer (non connectée ou flux non initialisés).");
        }
    }
}