package Network;

import Modele.Coup;
import Modele.Jeu;
import Modele.Joueur;
import Modele.Piece;
import Modele.Plateau;
import SceneManager.HostingScene;
import java.awt.Point;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket; // Pour utiliser synchronizedList
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap; // Importer la classe Jeu
import java.util.concurrent.LinkedBlockingQueue;

public class GameServerManager {

    private static final int PORT = 12345;
    private ServerSocket serverSocket;
    private Thread acceptConnectionsThread;
    private Thread gameEngineThread;
    private Jeu gameInstance; // 游戏实例，替代原来的GameEngineServer

    private final BlockingQueue<Message> incomingMessages = new LinkedBlockingQueue<>();
    private final Map<Integer, BlockingQueue<String>> outgoingQueues = new ConcurrentHashMap<>();
    // Utilisation de synchronizedList pour la sécurité des threads lorsque plusieurs clients se connectent en même temps
    private final List<Integer> connectedClientIds = Collections.synchronizedList(new ArrayList<>());
    private volatile int clientIdCounter = 0; // volatile car il peut être accédé depuis plusieurs threads client handler
    private int maxClients = 2;
    private int currentTurnPlayerId; // ID du joueur dont c'est le tour

    private HostingScene hostingSceneCallback; // Callback pour HostingScene (si l'hôte est en mode multijoueur)
    private volatile boolean isServerRunning = false;

    public GameServerManager(HostingScene callback) {
        this.hostingSceneCallback = callback; // Peut être null (par exemple : lorsque GameScene héberge en solo)
    }

    public void startServer() throws IOException {
        if (isServerRunning) {
            System.out.println("GameServerManager: Le serveur est déjà en cours d'exécution.");
            return;
        }
        serverSocket = new ServerSocket(PORT);
        isServerRunning = true;
        System.out.println("GameServerManager: Serveur démarré sur le port " + PORT);

        acceptConnectionsThread = new Thread(() -> {
            try {
                while (connectedClientIds.size() < maxClients && isServerRunning) {
                    System.out.println("GameServerManager: En attente de connexions client ("
                            + connectedClientIds.size() + "/" + maxClients + ")...");
                    Socket clientSocket = serverSocket.accept(); // Accepter une connexion

                    synchronized (this) { // Synchronisation pour incrémenter le compteur et ajouter l'ID client
                        clientIdCounter++;
                    }
                    int newClientId = clientIdCounter;

                    System.out.println("GameServerManager: Client " + newClientId + " connecté depuis " + clientSocket.getRemoteSocketAddress());

                    try {
                        // 遵循客户端/服务器双方统一的协议顺序：
                        // 1. 服务器创建输出流并刷新
                        ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        out.flush(); // 立即发送流头部信息

                        // 2. 客户端创建输入流
                        // 3. 客户端创建输出流并刷新
                        // 4. 服务器创建输入流
                        ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());

                        // 5. 服务器发送ID消息
                        out.writeObject("ID:" + newClientId);
                        out.flush();
                        System.out.println("GameServerManager: ID " + newClientId + " envoyé au client.");

                        BlockingQueue<String> clientOutgoingQueue = new LinkedBlockingQueue<>();
                        outgoingQueues.put(newClientId, clientOutgoingQueue);
                        connectedClientIds.add(newClientId);

                        // 创建并启动带有客户端ID的ClientReceiver和ClientSender
                        Thread receiverThread = new Thread(
                                new ClientReceiver(in, incomingMessages, newClientId),
                                "ClientReceiver-" + newClientId
                        );
                        receiverThread.start();

                        Thread senderThread = new Thread(
                                new ClientSender(out, clientOutgoingQueue, newClientId),
                                "ClientSender-" + newClientId
                        );
                        senderThread.start();

                        System.out.println("GameServerManager: Client " + newClientId + " configuré. Clients connectés: " + connectedClientIds.size());
                    } catch (IOException e) {
                        System.err.println("GameServerManager: Erreur lors de la configuration du client " + newClientId + ": " + e.getMessage());
                        try {
                            clientSocket.close();
                        } catch (IOException closeEx) {
                            // 忽略关闭错误
                        }
                        continue; // 跳过当前迭代，继续接受下一个连接
                    }

                    if (connectedClientIds.size() == maxClients) {
                        System.out.println("GameServerManager: Nombre maximum de " + maxClients + " joueurs atteint.");
                        if (hostingSceneCallback != null) {
                            // Si HostingScene a un callback, le notifier.
                            // HostingScene décidera quand appeler startGameEngine().
                            hostingSceneCallback.onPlayerTwoConnected();
                            System.out.println("GameServerManager: Notifié HostingScene que le joueur 2 est connecté.");
                        } else {
                            // Si aucun callback (mode solo hébergé par GameScene),
                            // démarrer automatiquement GameEngineServer.
                            System.out.println("GameServerManager: Pas de callback HostingScene, démarrage automatique du moteur de jeu.");
                            startGameEngine();
                        }
                    }
                }
                if (isServerRunning) {
                    System.out.println("GameServerManager: Nombre maximum de clients atteint ou arrêt du serveur. Arrêt de l'acceptation de nouvelles connexions.");
                }
            } catch (IOException e) {
                if (isServerRunning && serverSocket != null && !serverSocket.isClosed()) {
                    System.err.println("GameServerManager: Erreur lors de l'acceptation d'une connexion: " + e.getMessage());
                    // e.printStackTrace(); // Peut encombrer la console
                } else {
                    System.out.println("GameServerManager: Le socket serveur a été fermé, arrêt de l'acceptation des connexions.");
                }
            } finally {
                System.out.println("GameServerManager: Le thread d'acceptation des connexions s'est terminé.");
            }
        });
        acceptConnectionsThread.setName("GSM-AcceptConnectionsThread");
        acceptConnectionsThread.start();
    }

    public synchronized void startGameEngine() { // synchronized pour éviter plusieurs appels
        if (gameEngineThread != null && gameEngineThread.isAlive()) {
            System.out.println("GameServerManager: Le moteur de jeu est déjà en cours d'exécution.");
            return;
        }
        if (connectedClientIds.size() == maxClients) {
            System.out.println("GameServerManager: Démarrage du moteur de jeu...");
            // Initialiser l'instance de jeu
            gameInstance = new Jeu();

            // Définir le premier joueur (au début du jeu, cela devrait être le joueur 1)
            if (gameInstance.getJoueurCourant().getId() != 1) {
                System.out.println("GameServerManager: Le joueur courant n'est pas le joueur 1, changement...");
                gameInstance.joueurSuivant(); // S'assurer que le premier joueur est le joueur 1
            }
            currentTurnPlayerId = gameInstance.getJoueurCourant().getId();
            System.out.println("GameServerManager: Joueur courant initialisé à " + currentTurnPlayerId);

            // Démarrer le thread du moteur de jeu
            gameEngineThread = new Thread(this::runGameEngine, "GameEngineThread");
            gameEngineThread.start();

            System.out.println("GameServerManager: Moteur de jeu démarré. Tour du joueur " + currentTurnPlayerId);

            // Envoyer l'état initial du jeu à tous les clients
            sendGameStateToAllClients();
        } else {
            System.out.println("GameServerManager: Pas assez de joueurs (" + connectedClientIds.size() + "/" + maxClients + ") pour démarrer le moteur de jeu.");
        }
    }

    // Logique du moteur de jeu, remplaçant l'ancien GameEngineServer.run()
    private void runGameEngine() {
        System.out.println("GameServerManager: Boucle de jeu démarrée.");
        try {
            // Attendre un court instant pour s'assurer que les clients sont prêts
            Thread.sleep(500);

            while (isServerRunning) {
                int etapeCoup = gameInstance.getEtapeCoup();
                System.out.println("GameServerManager: Étape du coup: " + etapeCoup);

                Message msg = incomingMessages.take();
                int clientId = msg.clientId;
                System.out.println("GameServerManager: Traitement du message du client " + clientId + ": " + msg.contenu);

                if (gameInstance == null) {
                    System.err.println("GameServerManager: Instance de jeu non initialisée.");
                    sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Erreur serveur: jeu non initialisé.");
                    continue;
                }

                // Vérifier si c'est le tour du joueur actuel
                Joueur joueurCourant = gameInstance.getJoueurCourant();
                if (clientId != joueurCourant.getId()) {
                    System.out.println("GameServerManager: Message du client " + clientId + " ignoré car ce n'est pas son tour.");
                    sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Ce n'est pas votre tour.");
                    continue;
                }

                if (msg.contenu == null) {
                    System.err.println("GameServerManager: Message du client " + clientId + " est null.");
                    sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Message du client null.");
                    continue;
                }

                // Traiter le message en fonction de l'étape du coup
                String[] parts = msg.contenu.split(":");
                System.out.println("GameServerManager: Message du client " + clientId + ": " + msg.contenu);
                if (parts.length < 5) {
                    System.err.println("GameServerManager: Format du message invalide: " + msg.contenu);
                    sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Format de message invalide.");
                    continue;
                }

                // Vérifier si c'est une commande Undo
                int undo = Integer.parseInt(parts[0]);
                if (undo == 1) {
                    gameInstance.setEtapeCoup(0); // Réinitialiser l'étape à 0
                    System.out.println("GameServerManager: Undo effectué par le joueur " + clientId);
                    sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Undo effectué, vous pouvez choisir une nouvelle pièce.");
                    sendGameStateToAllClients();
                    continue;
                }

                // Extraire les coordonnées
                int x, y;
                try {
                    x = Integer.parseInt(parts[3]);
                    y = Integer.parseInt(parts[4]);
                } catch (NumberFormatException e) {
                    System.err.println("GameServerManager: Erreur de conversion des coordonnées: " + e.getMessage());
                    sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Coordonnées invalides.");
                    continue;
                }

                Plateau plateauCourant = gameInstance.getPlateauCourant();
                Piece selectedPiece = plateauCourant.getPiece(x, y);

                // Extraire le ProchainPlateau si spécifié
                Plateau.TypePlateau prochainPlateau = null;
                if (!parts[1].equals("null")) {
                    try {
                        prochainPlateau = Plateau.TypePlateau.valueOf(parts[1]);
                    } catch (IllegalArgumentException e) {
                        System.err.println("GameServerManager: Type de plateau invalide: " + parts[1]);
                        sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Type de plateau invalide.");
                        continue;
                    }
                }

                switch (etapeCoup) {
                    case 0: // Étape initiale: sélection d'une pièce
                        if (selectedPiece == null) {
                            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Aucune pièce à cette position.");
                            continue;
                        }

                        // Vérifier que la pièce appartient au joueur courant
                        if (!selectedPiece.getOwner().equals(joueurCourant)) {
                            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Cette pièce ne vous appartient pas.");
                            continue;
                        }

                        // Vérifier s'il y a des coups possibles pour cette pièce
                        ArrayList<Coup> coupsPossibles = gameInstance.getCoupPossibles(plateauCourant, selectedPiece);
                        if (coupsPossibles.isEmpty()) {
                            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Pas de coup possible pour cette pièce.");
                            continue;
                        }

                        // Pièce valide avec des coups possibles
                        StringBuilder possibleMovesStr = new StringBuilder();
                        for (Coup coup : coupsPossibles) {
                            Point currentPos = selectedPiece.getPosition();
                            Point targetPos = new Point(currentPos.x, currentPos.y);

                            // Calculer la position cible selon le type de coup
                            switch (coup.getTypeCoup()) {
                                case UP:
                                    targetPos.x -= 1;
                                    break;
                                case DOWN:
                                    targetPos.x += 1;
                                    break;
                                case LEFT:
                                    targetPos.y -= 1;
                                    break;
                                case RIGHT:
                                    targetPos.y += 1;
                                    break;
                                case JUMP:
                                case CLONE:
                                    // Pour les mouvements à travers le temps, la position reste la même
                                    break;
                            }

                            // Ajouter à la chaîne des mouvements possibles
                            possibleMovesStr.append(coup.getTypeCoup()).append(":").append(targetPos.x).append(":").append(targetPos.y).append(";");
                        }

                        // Définir la pièce sélectionnée
                        gameInstance.setPieceCourante(selectedPiece);
                        gameInstance.setEtapeCoup(1); // Passer à l'étape suivante
                        
                        System.out.println("GameServerManager: Pièce sélectionnée à " + selectedPiece.getPosition().x + "," + selectedPiece.getPosition().y + 
                                          " sur plateau " + plateauCourant.getType());

                        // Envoyer les informations de la pièce et ses mouvements possibles
                        sendMessageToClient(clientId, Code.PIECE.name() + ":" + selectedPiece.getPosition().x + ":" + selectedPiece.getPosition().y + ";" + possibleMovesStr.toString());

                        // Mettre à jour l'état du jeu
                        sendGameStateToAllClients();
                        break;

                    case 1: // Premier coup: déplacement ou action spéciale
                        Piece pieceCourante = gameInstance.getPieceCourante();
                        if (pieceCourante == null) {
                            System.err.println("GameServerManager: Pièce courante null à l'étape 1");
                            gameInstance.setEtapeCoup(0); // Retour à l'étape 0
                            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Erreur: sélectionnez d'abord une pièce.");
                            continue;
                        }

                        // 验证所选棋盘与当前棋子所在棋盘是否一致
                        Plateau.TypePlateau selectedPlateauType = null;
                        try {
                            selectedPlateauType = Plateau.TypePlateau.valueOf(parts[2]);
                        } catch (IllegalArgumentException e) {
                            System.err.println("GameServerManager: Type de plateau invalide: " + parts[2]);
                            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Type de plateau invalide.");
                            continue;
                        }

                        // 获取当前棋子所在的棋盘类型
                        Plateau.TypePlateau currentPieceBoard = plateauCourant.getType();
                        
                        // 获取点击坐标和棋子位置
                        Point clickedPosition = new Point(x, y);
                        Point piecePosition = pieceCourante.getPosition();
                        
                        // 使用辅助方法确定移动类型
                        Coup.TypeCoup typeCoup = determineMovementType(piecePosition, clickedPosition, 
                                                                     currentPieceBoard, selectedPlateauType, clientId);
                        if (typeCoup == null) {
                            continue;
                        }
                        
                        // 处理移动操作
                        if (!processMove(pieceCourante, plateauCourant, typeCoup, clientId, 2)) {
                            continue;
                        }
                        break;

                    case 2: // Deuxième coup: déplacement ou action spéciale
                        pieceCourante = gameInstance.getPieceCourante();
                        if (pieceCourante == null) {
                            System.err.println("GameServerManager: Pièce courante null à l'étape 2");
                            gameInstance.setEtapeCoup(0); // Retour à l'étape 0
                            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Erreur: sélectionnez d'abord une pièce.");
                            continue;
                        }

                        // 验证所选棋盘与当前棋子所在棋盘是否一致
                        selectedPlateauType = null;
                        try {
                            selectedPlateauType = Plateau.TypePlateau.valueOf(parts[2]);
                        } catch (IllegalArgumentException e) {
                            System.err.println("GameServerManager: Type de plateau invalide: " + parts[2]);
                            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Type de plateau invalide.");
                            continue;
                        }

                        // 获取当前棋子所在的棋盘类型
                        currentPieceBoard = plateauCourant.getType();
                        
                        // 获取点击坐标和棋子位置
                        clickedPosition = new Point(x, y);
                        piecePosition = pieceCourante.getPosition();
                        
                        // 使用辅助方法确定移动类型
                        typeCoup = determineMovementType(piecePosition, clickedPosition, 
                                                      currentPieceBoard, selectedPlateauType, clientId);
                        if (typeCoup == null) {
                            continue;
                        }
                        
                        // 处理移动操作
                        if (!processMove(pieceCourante, plateauCourant, typeCoup, clientId, 3)) {
                            continue;
                        }
                        break;

                    case 3: // Sélection du prochain plateau
                        if (prochainPlateau == null) {
                            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Vous devez sélectionner un plateau (PAST, PRESENT ou FUTURE).");
                            continue;
                        }

                        System.out.println("GameServerManager: Sélection du prochain plateau: " + prochainPlateau);

                        // Définir le prochain plateau pour le joueur
                        joueurCourant.setProchainPlateau(prochainPlateau);

                        // Réinitialiser pour le prochain joueur
                        gameInstance.setEtapeCoup(0);
                        gameInstance.joueurSuivant();
                        gameInstance.majPlateauCourant();

                        // Mettre à jour le joueur courant
                        currentTurnPlayerId = gameInstance.getJoueurCourant().getId();

                        sendMessageToClient(clientId, Code.PLATEAU.name() + ":" + prochainPlateau + ":success");
                        sendGameStateToAllClients();

                        // Vérifier si la partie est terminée
                        checkGameOver();
                        break;
                }
            }
        } catch (InterruptedException e) {
            System.err.println("GameServerManager: Thread de jeu interrompu: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("GameServerManager: Erreur dans la boucle de jeu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 辅助方法：处理棋子移动的通用逻辑
    private Coup.TypeCoup determineMovementType(Point piecePosition, Point clickedPosition, 
                                               Plateau.TypePlateau currentPieceBoard, Plateau.TypePlateau selectedPlateauType,
                                               int clientId) {
        
        // 检查是否是同一位置但不同棋盘（可能是JUMP或CLONE）
        boolean isSamePosition = (piecePosition.x == clickedPosition.x && piecePosition.y == clickedPosition.y);
        boolean isDifferentPlateau = (currentPieceBoard != selectedPlateauType);
        
        // 自动判断操作类型
        Coup.TypeCoup typeCoup = null;
        
        if (isSamePosition && isDifferentPlateau) {
            // 根据棋盘类型判断是JUMP还是CLONE
            if ((currentPieceBoard == Plateau.TypePlateau.PAST && selectedPlateauType == Plateau.TypePlateau.PRESENT) ||
                (currentPieceBoard == Plateau.TypePlateau.PRESENT && selectedPlateauType == Plateau.TypePlateau.FUTURE)) {
                // PAST→PRESENT 或 PRESENT→FUTURE 是JUMP操作
                typeCoup = Coup.TypeCoup.JUMP;
                System.out.println("GameServerManager: Détection automatique d'une opération JUMP");
            } else if ((currentPieceBoard == Plateau.TypePlateau.PRESENT && selectedPlateauType == Plateau.TypePlateau.PAST) ||
                      (currentPieceBoard == Plateau.TypePlateau.FUTURE && selectedPlateauType == Plateau.TypePlateau.PRESENT)) {
                // PRESENT→PAST 或 FUTURE→PRESENT 是CLONE操作
                typeCoup = Coup.TypeCoup.CLONE;
                System.out.println("GameServerManager: Détection automatique d'une opération CLONE");
            } else {
                // 不允许的棋盘切换
                System.err.println("GameServerManager: Transition de plateau non autorisée: " + currentPieceBoard + " -> " + selectedPlateauType);
                sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Transition de plateau non autorisée.");
                return null;
            }
        } else if (currentPieceBoard == selectedPlateauType) {
            // 同一棋盘上的移动，根据相对位置判断方向
            int dx = clickedPosition.x - piecePosition.x;
            int dy = clickedPosition.y - piecePosition.y;
            
            if (dx == -1 && dy == 0) {
                typeCoup = Coup.TypeCoup.UP;
            } else if (dx == 1 && dy == 0) {
                typeCoup = Coup.TypeCoup.DOWN;
            } else if (dx == 0 && dy == -1) {
                typeCoup = Coup.TypeCoup.LEFT;
            } else if (dx == 0 && dy == 1) {
                typeCoup = Coup.TypeCoup.RIGHT;
            } else {
                // 无效的移动方向
                System.err.println("GameServerManager: Déplacement invalide: dx=" + dx + ", dy=" + dy);
                sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Déplacement invalide.");
                return null;
            }
        } else {
            // 不同棋盘上的不同位置，这是无效操作
            System.err.println("GameServerManager: Opération invalide entre plateaux différents à des positions différentes.");
            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Pour JUMP/CLONE, cliquez sur la même position dans un plateau adjacent.");
            return null;
        }
        
        return typeCoup;
    }
    
    // 处理移动操作并返回是否成功
    private boolean processMove(Piece pieceCourante, Plateau plateauCourant, Coup.TypeCoup typeCoup, 
                               int clientId, int nextEtape) {
        // 创建移动操作
        Coup coup = new Coup(pieceCourante, plateauCourant, typeCoup);
        
        // 验证移动是否合法
        ArrayList<Coup> possibleCoups = gameInstance.getCoupPossibles(plateauCourant, pieceCourante);
        boolean coupValide = false;
        
        for (Coup possibleCoup : possibleCoups) {
            if (possibleCoup.getTypeCoup() == typeCoup) {
                coupValide = true;
                break;
            }
        }
        
        if (!coupValide) {
            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Coup invalide.");
            return false;
        }
        
        // 执行移动
        boolean coupReussi = gameInstance.jouerCoup(coup);
        if (coupReussi) {
            // 设置下一个阶段
            gameInstance.setEtapeCoup(nextEtape);
            
            // 确认操作成功
            if (nextEtape == 3) {
                System.out.println("GameServerManager: Deuxième coup réussi, etapeCoup passé à 3");
            }
            
            // 获取当前棋子的新位置和当前棋盘
            Point newPosition = pieceCourante.getPosition();
            Plateau currentPlateau = gameInstance.getPlateauCourant();
            
            // 返回成功消息
            String responseMessage = Code.COUP.name() + ":" + typeCoup.name() + ":" 
                                   + newPosition.x + ":" + newPosition.y + ":" 
                                   + currentPlateau.getType().name() + ":success";
            sendMessageToClient(clientId, responseMessage);
            
            // 更新游戏状态
            sendGameStateToAllClients();
            return true;
        } else {
            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Erreur lors de l'application du coup.");
            return false;
        }
    }

    // 检查游戏是否结束
    private void checkGameOver() {
        if (gameInstance == null) {
            return;
        }

        // 获取游戏状态
        int gameState = gameInstance.gameOver(gameInstance.getJoueurCourant());
        int winnerId = 0;

        System.out.println("GameServerManager: Game state: " + gameState);

        // 通过game state确定获胜者ID
        if (gameState == 1) {
            winnerId = gameInstance.getJoueur1().getId(); // 玩家1赢
        } else if (gameState == 2) {
            winnerId = gameInstance.getJoueur2().getId(); // 玩家2赢
        }

        System.out.println("GameServerManager: Winner ID: " + winnerId);

        if (winnerId != 0) {
            Joueur joueur1 = gameInstance.getJoueur1();
            Joueur joueur2 = gameInstance.getJoueur2();
            String winnerMsg = Code.GAGNE.name() + ":" + winnerId;
            String loserMsg = Code.PERDU.name() + ":" + winnerId;

            if (winnerId == joueur1.getId()) {
                winnerMsg += ":Félicitations " + joueur1.getNom() + " pour votre victoire!";
                loserMsg += ":" + joueur1.getNom() + " a gagné la partie!";
            } else {
                winnerMsg += ":Félicitations " + joueur2.getNom() + " pour votre victoire!";
                loserMsg += ":" + joueur2.getNom() + " a gagné la partie!";
            }

            // 给赢家和输家发送不同消息
            for (Integer clientId : connectedClientIds) {
                if (clientId == winnerId) {
                    sendMessageToClient(clientId, winnerMsg);
                } else {
                    sendMessageToClient(clientId, loserMsg);
                }
            }

            System.out.println("GameServerManager: Jeu terminé! Gagnant: " + winnerId);
        }
    }

    // 发送游戏状态给所有客户端
    private void sendGameStateToAllClients() {
        if (gameInstance == null) {
            return;
        }

        String gameStateString = gameInstance.getGameStateAsString();

        // 确保etapeCoup包含在游戏状态中
        int etapeCoup = gameInstance.getEtapeCoup();
        String messageToSend = Code.ETAT.name() + ":" + gameStateString;

        System.out.println("GameServerManager: Sending game state with etapeCoup=" + etapeCoup
                + ", Game state begins with: " + gameStateString.substring(0, Math.min(20, gameStateString.length())));

        sendStateToAllClients(messageToSend);
        System.out.println("GameServerManager: État du jeu envoyé à tous les clients (etapeCoup=" + gameInstance.getEtapeCoup() + ")");
    }

    public synchronized void stopServer() {
        if (!isServerRunning) {
            System.out.println("GameServerManager: Le serveur n'est pas en cours d'exécution.");
            return;
        }
        System.out.println("GameServerManager: Arrêt du serveur...");
        isServerRunning = false; // Définir ce drapeau en premier

        try {
            if (acceptConnectionsThread != null && acceptConnectionsThread.isAlive()) {
                acceptConnectionsThread.interrupt(); // Essayer d'interrompre le thread d'acceptation
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // Fermer le server socket fera que accept() lève une exception et sort de la boucle
            }
        } catch (IOException e) {
            System.err.println("GameServerManager: Erreur lors de la fermeture du ServerSocket: " + e.getMessage());
        }

        if (gameEngineThread != null && gameEngineThread.isAlive()) {
            gameEngineThread.interrupt(); // Essayer d'interrompre le thread du moteur de jeu
        }

        // TODO : Un mécanisme est nécessaire pour déconnecter tous les ClientReceiver/ClientSender en cours d'exécution
        // et fermer leurs sockets clients. Actuellement, ils se termineront automatiquement lorsque le socket sera fermé.
        // Supprimer les clients connectés et leurs files d'attente
        connectedClientIds.clear();
        outgoingQueues.clear(); // Les ClientSenders se termineront automatiquement lorsque la file d'attente sera vidée ou qu'une exception sera levée
        incomingMessages.clear(); // Les ClientReceivers se termineront lorsque le socket sera fermé

        System.out.println("GameServerManager: Serveur arrêté.");
        // Réinitialiser le compteur pour le prochain démarrage du serveur (le cas échéant)
        clientIdCounter = 0;
    }

    // 发送消息给所有客户端
    private void sendStateToAllClients(String message) {
        for (Integer clientId : connectedClientIds) {
            BlockingQueue<String> queue = outgoingQueues.get(clientId);
            if (queue != null) {
                try {
                    queue.put(message);
                } catch (InterruptedException e) {
                    System.err.println(
                            "GameServerManager: Erreur lors de l'envoi du message au client " + clientId + " : " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    // 发送消息给指定客户端
    private void sendMessageToClient(int clientId, String message) {
        BlockingQueue<String> clientQueue = outgoingQueues.get(clientId);
        if (clientQueue != null) {
            try {
                clientQueue.put(message);
            } catch (InterruptedException e) {
                System.err.println("GameServerManager: Erreur lors de l'envoi du message au client " + clientId + " : " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    public synchronized boolean areAllPlayersConnected() {
        return connectedClientIds.size() == maxClients;
    }

    public boolean isServerRunning() {
        return isServerRunning && serverSocket != null && !serverSocket.isClosed();
    }
}
