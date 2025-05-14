package Network;

import Modele.Jeu;
import Modele.Joueur;
import Modele.Plateau;
import SceneManager.HostingScene;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket; // Pour utiliser synchronizedList
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue; // Importer la classe Jeu

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
                                        + connectedClientIds.size() + "/" + maxClients +")...");
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
            // 初始化游戏实例
            gameInstance = new Jeu();
            
            // 设置第一个玩家（游戏开始时应该是玩家1）
            if (gameInstance.getJoueurCourant().getId() != 1) {
                gameInstance.joueurSuivant(); // 确保第一个玩家是玩家1
            }
            currentTurnPlayerId = gameInstance.getJoueurCourant().getId();
            
            // 开始游戏引擎线程
            gameEngineThread = new Thread(this::runGameEngine, "GameEngineThread");
            gameEngineThread.start();
            
            System.out.println("GameServerManager: Moteur de jeu démarré. Tour du joueur " + currentTurnPlayerId);
            
            // 发送初始游戏状态给所有客户端
            sendGameStateToAllClients();
        } else {
            System.out.println("GameServerManager: Pas assez de joueurs ("+ connectedClientIds.size() +"/"+maxClients+") pour démarrer le moteur de jeu.");
        }
    }

    // 游戏引擎逻辑，替代原来的GameEngineServer.run()
    private void runGameEngine() {
        System.out.println("GameServerManager: Boucle de jeu démarrée.");
        try {
            // 等待一小段时间确保客户端已准备好
            Thread.sleep(500);
            
            while (isServerRunning) {
                // 等待客户端消息(阻塞调用)
                Message msg = incomingMessages.take();
                
                // 处理消息
                processClientMessage(msg);
            }
        } catch (InterruptedException e) {
            System.err.println("GameServerManager: Thread de jeu interrompu: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("GameServerManager: Erreur dans la boucle de jeu: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 处理客户端消息
    private void processClientMessage(Message msg) {
        int clientId = msg.clientId;
        System.out.println("GameServerManager: Processing message from client " + clientId + ": " + msg.contenu);
        
        if (gameInstance == null) {
            System.err.println("GameServerManager: Instance de jeu non initialisée.");
            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Erreur serveur: jeu non initialisé.");
            return;
        }
        
        // 首先检查是否是当前玩家的回合
        if (clientId != gameInstance.getJoueurCourant().getId()) {
            System.out.println("GameServerManager: Message du client " + clientId + " ignoré car ce n'est pas son tour.");
            System.out.println("GameServerManager: Current turn player ID: " + currentTurnPlayerId);
            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Ce n'est pas votre tour.");
            return;
        }
        
        // 将命令传递给Jeu类处理
        boolean actionSuccess = gameInstance.processPlayerCommand(msg.contenu, clientId);
        
        if (actionSuccess) {
            // 从Jeu获取当前步骤信息
            int etapeCoup = gameInstance.getEtapeCoup();

            // 检查游戏是否结束
            checkGameOver();
            
            // 如果玩家已经完成了两步操作，应该自动切换到下一个玩家
            if (etapeCoup >= 3) {
                // 第三步选择新的棋盘
                Plateau.TypePlateau plateauType = gameInstance.getPlateauCourant().getType();
                Plateau.TypePlateau nextPlateau = null;
                if (plateauType != Plateau.TypePlateau.PAST) {
                    nextPlateau = Plateau.TypePlateau.PAST;
                } else {
                    nextPlateau = Plateau.TypePlateau.PRESENT;
                }
                
                gameInstance.getJoueurCourant().setProchainPlateau(nextPlateau);
                gameInstance.setEtapeCoup(0);
                gameInstance.joueurSuivant(); // 切换到下一玩家
                gameInstance.majPlateauCourant();
                
                // 添加明确的日志，显示当前轮到谁
                System.out.println("GameServerManager: Joueur suivant - ID: " + gameInstance.getJoueurCourant().getId() + 
                                   " (" + (gameInstance.getJoueurCourant().equals(gameInstance.getJoueur1()) ? "Blanc/Lemiel" : "Noir/Zarek") + ")");
            }
            
            
            // 游戏状态更新成功，更新当前玩家
            currentTurnPlayerId = gameInstance.getJoueurCourant().getId();
            System.out.println("GameServerManager: Action validée. Nouveau tour pour le joueur: " + currentTurnPlayerId);
            
            // 发送更新后的游戏状态给所有客户端
            sendGameStateToAllClients();
        } else {
            System.out.println("GameServerManager: Action invalide du joueur " + clientId);
            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Action invalide.");
        }
    }
    
    // 检查游戏是否结束
    private void checkGameOver() {
        if (gameInstance == null) return;
        
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
        if (gameInstance == null) return;
        
        String gameStateString = gameInstance.getGameStateAsString();
        String messageToSend = Code.ETAT.name() + ":" + gameStateString;
        
        sendStateToAllClients(messageToSend);
        System.out.println("GameServerManager: État du jeu envoyé à tous les clients");
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
