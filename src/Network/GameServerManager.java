package Network;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections; // Pour utiliser synchronizedList
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import SceneManager.HostingScene; // Gardé pour que HostingScene puisse faire un callback

public class GameServerManager {
    private static final int PORT = 1234;
    private ServerSocket serverSocket;
    private Thread acceptConnectionsThread;
    private Thread gameEngineThread;
    private GameEngineServer gameEngineServer; // Instance de GameEngineServer

    private final BlockingQueue<Message> incomingMessages = new LinkedBlockingQueue<>();
    private final Map<Integer, BlockingQueue<String>> outgoingQueues = new ConcurrentHashMap<>();
    // Utilisation de synchronizedList pour la sécurité des threads lorsque plusieurs clients se connectent en même temps
    private final List<Integer> connectedClientIds = Collections.synchronizedList(new ArrayList<>());
    private volatile int clientIdCounter = 0; // volatile car il peut être accédé depuis plusieurs threads client handler
    private int maxClients = 2;

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

                    ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                    out.flush(); // Flush avant que le client ne crée InputStream

                    // Envoyer l'ID au client connecté
                    out.writeObject("ID:" + newClientId);
                    out.flush();
                    System.out.println("GameServerManager: ID " + newClientId + " envoyé au client.");

                    ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());

                    BlockingQueue<String> clientOutgoingQueue = new LinkedBlockingQueue<>();
                    outgoingQueues.put(newClientId, clientOutgoingQueue);
                    connectedClientIds.add(newClientId);

                    // Démarrer ClientReceiver et ClientSender pour ce client
                    new Thread(new ClientReceiver(in, incomingMessages, newClientId), "ClientReceiver-" + newClientId).start();
                    new Thread(new ClientSender(out, clientOutgoingQueue), "ClientSender-" + newClientId).start();

                    System.out.println("GameServerManager: Client " + newClientId + " configuré. Clients connectés: " + connectedClientIds.size());

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
                            System.out.println("GameServerManager: Pas de callback HostingScene, démarrage automatique du GameEngineServer.");
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
            System.out.println("GameServerManager: Démarrage du GameEngineServer...");
            // Créer une copie de connectedClientIds pour la passer à GameEngineServer
            // afin d'éviter ConcurrentModificationException si la liste d'origine change
            gameEngineServer = new GameEngineServer(incomingMessages, outgoingQueues, new ArrayList<>(connectedClientIds));
            gameEngineThread = new Thread(gameEngineServer, "GameEngineServerThread");
            gameEngineThread.start();
            System.out.println("GameServerManager: GameEngineServer démarré.");
        } else {
            System.out.println("GameServerManager: Pas assez de joueurs ("+ connectedClientIds.size() +"/"+maxClients+") pour démarrer le moteur de jeu.");
        }
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

    public synchronized boolean areAllPlayersConnected() {
        return connectedClientIds.size() == maxClients;
    }
    
    public boolean isServerRunning() {
        return isServerRunning && serverSocket != null && !serverSocket.isClosed();
    }
}
