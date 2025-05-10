package Network;

import java.io.*;
import java.net.*;
// import java.util.*; // Ne pas utiliser util.* directement
// import java.awt.Point; // Ne pas utiliser Point directement

import Modele.Jeu;
// import Modele.Plateau; // Ne pas utiliser Plateau directement
// import Modele.Piece; // Ne pas utiliser Piece directement

public class GameClient {
    private String serverIpAddress;
    private int serverPort = 1234; // Port par défaut
    private Jeu gameInstance;
    private ObjectOutputStream outputStream;
    private Socket socket;
    private GameStateUpdateListener listener;
    private int myPlayerId = -1; // ID de ce client, attribué par le serveur
    private Thread receptionThread;
    private volatile boolean isConnected = false; // Pour vérifier l'état de la connexion
    private ObjectInputStream inputStream;

    public GameClient(String ipAddress, GameStateUpdateListener listener) {
        this.serverIpAddress = ipAddress;
        this.listener = listener;
        this.gameInstance = new Jeu(); // Initialiser une copie locale du jeu
                                      // Joueur 1 et Joueur 2 sont créés avec des ID par défaut 1 et 2 dans Jeu
    }

    public void connect() throws IOException {
        System.out.println("GameClient: Tentative de connexion à " + serverIpAddress + ":" + serverPort + "...");
        try {
            socket = new Socket(serverIpAddress, serverPort);
            isConnected = true; // Marquer la socket comme connectée
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush(); // Très important !

            // InputStream doit être créé APRÈS que outputStream soit flush côté client
            // et APRÈS que outputStream soit créé côté serveur.
            InputStream socketInputStream = socket.getInputStream();
            inputStream = new ObjectInputStream(socketInputStream);
            System.out.println("GameClient: Connecté. En attente de l'ID du joueur...");

            // 1. Lire le message d'ID attribué par le serveur
            try {
                Object idMessageObj = inputStream.readObject();
                if (idMessageObj instanceof String) {
                    String idMessage = (String) idMessageObj;
                    if (idMessage.startsWith("ID:")) {
                        this.myPlayerId = Integer.parseInt(idMessage.substring(3));
                        System.out.println("GameClient: Mon ID de joueur est: " + this.myPlayerId);
                    } else {
                        disconnect(); // Fermer la connexion si le message d'ID est invalide
                        throw new IOException("GameClient: Message d'ID initial invalide du serveur: " + idMessage);
                    }
                } else {
                    disconnect();
                    throw new IOException("GameClient: Type de message d'ID initial invalide.");
                }
            } catch (ClassNotFoundException e) {
                disconnect();
                throw new IOException("GameClient: Erreur lors de la lecture du message d'ID.", e);
            } catch (NumberFormatException e){
                disconnect();
                throw new IOException("GameClient: Erreur de format de l'ID reçu.", e);
            }


            // 2. Démarrer le thread de réception des données du jeu
            receptionThread = new Thread(() -> {
                try {
                    while (isConnected && socket != null && !socket.isClosed()) {
                        Object obj = inputStream.readObject(); // Lire les données du serveur
                        if (obj instanceof String) {
                            String data = (String) obj;
                             // System.out.println("GameClient (ID: " + myPlayerId + ") - Server Raw: " + data); // Debug

                            String[] parts = data.split(":", 2);
                            if (parts.length < 2) {
                                System.err.println("GameClient (ID: " + myPlayerId + "): Message serveur mal formaté: " + data);
                                if (listener != null) listener.onGameMessage("ERROR", "Message serveur invalide.");
                                continue;
                            }
                            Code code;
                            try {
                                code = Code.valueOf(parts[0].toUpperCase()); // Convertir en majuscules pour être sûr
                            } catch (IllegalArgumentException e) {
                                System.err.println("GameClient (ID: " + myPlayerId + "): Code inconnu du serveur: " + parts[0]);
                                if (listener != null) listener.onGameMessage("UNKNOWN_CODE", "Code serveur inconnu: " + parts[0]);
                                continue;
                            }
                            
                            String content = parts[1];

                            // Utiliser SwingUtilities pour s'assurer que le listener est mis à jour sur l'EDT
                            final String finalContent = content; // Nécessaire pour utiliser dans une lambda
                            final Code finalCode = code;

                            javax.swing.SwingUtilities.invokeLater(() -> {
                                if (listener == null) return;

                                switch (finalCode) {
                                    case ETAT:
                                        GameStateParser.parseAndUpdateJeu(gameInstance, finalContent);
                                        listener.onGameStateUpdate(gameInstance);
                                        break;
                                    case GAGNE:
                                        listener.onGameMessage("WIN", finalContent); // "WIN" est le messageType
                                        break;
                                    case PERDU:
                                        listener.onGameMessage("LOSE", finalContent);
                                        break;
                                    case ADVERSAIRE: // Exemple : "C'est le tour de l'adversaire", ou erreur "Pas votre tour"
                                    case PIECE:      // Notification que le serveur attend la sélection d'une PIECE
                                    case ACTION:     // Notification que le serveur attend la sélection d'une ACTION
                                    case DIRECTION:  // Notification que le serveur attend la sélection d'une DIRECTION
                                    case PLATEAU:    // Notification que le serveur attend la sélection d'un PLATEAU
                                        listener.onGameMessage(finalCode.name(), finalContent); // Utiliser le nom du Code comme messageType
                                        break;
                                    default:
                                        System.out.println("GameClient (ID: " + myPlayerId + "): Commande serveur non gérée par listener: " + finalCode.name());
                                        listener.onGameMessage("UNHANDLED_SERVER_CMD", finalContent);
                                        break;
                                }
                            });
                        }
                    }
                } catch (SocketException | EOFException se) {
                    if (isConnected) { // Log uniquement si on attend une connexion
                        System.out.println("GameClient (ID: " + myPlayerId + "): Connexion serveur perdue ou fermée. " + se.getMessage());
                        final GameStateUpdateListener currentListener = listener;
                        if (currentListener != null) {
                             javax.swing.SwingUtilities.invokeLater(() -> currentListener.onGameMessage("DISCONNECTED", "Connexion au serveur perdue."));
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    if (isConnected) {
                        System.err.println("GameClient (ID: " + myPlayerId + "): Erreur de réception des données: " + e.getMessage());
                        e.printStackTrace();
                        final GameStateUpdateListener currentListener = listener;
                        if (currentListener != null) {
                            final String errorMsg = e.getMessage();
                             javax.swing.SwingUtilities.invokeLater(() -> currentListener.onGameMessage("ERROR", "Erreur de réception: " + errorMsg));
                        }
                    }
                } finally {
                    disconnect(); // Assurer la déconnexion lorsque le thread se termine
                }
            });
            receptionThread.setName("GameClient-ReceptionThread-ID-" + myPlayerId);
            receptionThread.start();
            System.out.println("GameClient (ID: " + myPlayerId + "): Thread de réception démarré.");

        } catch (IOException e) {
            isConnected = false; // Assurer que isConnected est false si la connexion échoue
            System.err.println("GameClient: Échec de la connexion initiale au serveur: " + e.getMessage());
            throw e; // Relancer l'exception pour que l'appelant la gère
        }
    }

    public void sendPlayerAction(String actionString) {
        if (!isConnected || outputStream == null || socket == null || socket.isClosed()) {
            System.err.println("GameClient (ID: " + myPlayerId + "): Impossible d'envoyer l'action, non connecté.");
            if(listener != null) listener.onGameMessage("ERROR", "Non connecté, impossible d'envoyer l'action.");
            return;
        }
        try {
            outputStream.writeObject(actionString);
            outputStream.flush();
            // System.out.println("GameClient (ID: " + myPlayerId + "): Action envoyée -> " + actionString);
        } catch (IOException e) {
            System.err.println("GameClient (ID: " + myPlayerId + "): Erreur lors de l'envoi de l'action: " + e.getMessage());
            // On peut envisager de déconnecter ici si l'erreur est critique
            final GameStateUpdateListener currentListener = listener;
            if (currentListener != null) {
                final String errorMsg = e.getMessage();
                javax.swing.SwingUtilities.invokeLater(() -> currentListener.onGameMessage("ERROR", "Erreur d'envoi: " + errorMsg));
            }
            disconnect(); // Déconnecter en cas d'erreur d'envoi
        }
    }

    public Jeu getGameInstance() {
        return gameInstance;
    }

    public int getMyPlayerId() {
        return myPlayerId;
    }
    public boolean isConnected() { return isConnected && socket != null && !socket.isClosed(); }


    public void disconnect() {
        if (!isConnected && socket == null) return; // Déjà déconnecté ou jamais connecté

        isConnected = false; // Définir avant pour arrêter les boucles
        System.out.println("GameClient (ID: " + myPlayerId + "): Déconnexion...");

        if (receptionThread != null && receptionThread.isAlive()) {
            receptionThread.interrupt(); // Essayer d'interrompre le thread de réception
        }
        try {
            if (outputStream != null) {
                outputStream.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("GameClient (ID: " + myPlayerId + "): Erreur lors de la fermeture des flux/socket: " + e.getMessage());
        } finally {
            socket = null;
            outputStream = null;
            inputStream = null;
            System.out.println("GameClient (ID: " + myPlayerId + "): Ressources réseau libérées.");
            final GameStateUpdateListener currentListener = listener;
            // Notifier le listener que la déconnexion a eu lieu, uniquement si la connexion avait réussi auparavant
            // (éviter le cas où disconnect est appelé depuis le constructeur à cause d'une erreur)
            // if (this.myPlayerId != -1 && currentListener != null) { // myPlayerId != -1 signifie que l'ID a été attribué
            //    javax.swing.SwingUtilities.invokeLater(() -> currentListener.onGameMessage("INFO", "Vous avez été déconnecté."));
            // }
        }
    }
    // Permet de mettre à jour le listener (par exemple lors du passage de LobbyScene à GameScene)
    public void setListener(GameStateUpdateListener newListener){
        this.listener = newListener;
    }
}