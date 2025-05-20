package Network;

import Modele.Jeu;
import java.io.*;
import java.net.*;
// import Modele.Plateau; // Ne pas utiliser Plateau directement
// import Modele.Piece; // Ne pas utiliser Piece directement

public class GameClient {
    private String serverIpAddress;
    private int serverPort = 12345; // Port par défaut
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
            // Établir la connexion socket
            socket = new Socket(serverIpAddress, serverPort);
            isConnected = true;
            
            // L'ordre du protocole doit correspondre exactement au serveur:
            // 1. Le serveur crée un flux de sortie et le vide
            // 2. Le client crée un flux d'entrée
            inputStream = new ObjectInputStream(socket.getInputStream());
            
            // 3. Le client crée un flux de sortie et le vide
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();
            
            // 4. Le serveur crée un flux d'entrée
            // 5. Le serveur envoie un message ID, le client le lit
            System.out.println("GameClient: Flux établis. En attente de l'ID du joueur...");
            
            try {
                Object idMessageObj = inputStream.readObject();
                if (idMessageObj instanceof String) {
                    String idMessage = (String) idMessageObj;
                    if (idMessage.startsWith("ID:")) {
                        this.myPlayerId = Integer.parseInt(idMessage.substring(3));
                        System.out.println("GameClient: Mon ID de joueur est: " + this.myPlayerId);
                    } else {
                        disconnect();
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

            // Démarrer le thread de réception
            setupReceptionThread();
            
        } catch (IOException e) {
            isConnected = false;
            System.err.println("GameClient: Échec de la connexion initiale au serveur: " + e.getMessage());
            // S'assurer que les ressources sont nettoyées
            try {
                if (outputStream != null) outputStream.close();
                if (inputStream != null) inputStream.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException closeEx) {
                // Ignorer les exceptions lors de la fermeture
            }
            throw e; // Relancer l'exception pour que l'appelant puisse la gérer
        }
    }
    
    /**
     * Tente de reconnecter au serveur si la connexion a été perdue.
     * Conserve l'ID du joueur et autres informations de session si possible.
     * @return true si la reconnexion a réussi, false sinon
     */
    public boolean reconnect() {
        if (isConnected()) {
            System.out.println("GameClient: Déjà connecté, pas besoin de reconnexion");
            return true;
        }
        
        System.out.println("GameClient: Tentative de reconnexion avec ID: " + myPlayerId);
        
        // Nettoyage des ressources existantes
        try {
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            // Ignorer les erreurs de fermeture
        }
        
        outputStream = null;
        inputStream = null;
        socket = null;
        
        try {
            // Établir une nouvelle connexion socket
            socket = new Socket(serverIpAddress, serverPort);
            isConnected = true;
            
            // Recréer les flux dans le bon ordre (comme pour connect())
            inputStream = new ObjectInputStream(socket.getInputStream());
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();
            
            // Lire l'ID envoyé par le serveur
            Object idMessageObj = inputStream.readObject();
            if (idMessageObj instanceof String) {
                String idMessage = (String) idMessageObj;
                if (idMessage.startsWith("ID:")) {
                    int newPlayerId = Integer.parseInt(idMessage.substring(3));
                    System.out.println("GameClient: Reconnexion réussie. Nouvel ID: " + newPlayerId);
                    
                    // Mise à jour de l'ID si nécessaire
                    this.myPlayerId = newPlayerId;
                } else {
                    throw new IOException("Message ID invalide lors de la reconnexion: " + idMessage);
                }
            } else {
                throw new IOException("Type de message d'ID invalide lors de la reconnexion");
            }
            
            // Recréer le thread de réception
            setupReceptionThread();
            
            if (listener != null) {
                listener.onGameMessage("RECONNECTED", "Reconnexion réussie avec ID: " + myPlayerId);
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println("GameClient: Échec de la tentative de reconnexion: " + e.getMessage());
            isConnected = false;
            
            // Nettoyage en cas d'échec
            try {
                if (outputStream != null) outputStream.close();
                if (inputStream != null) inputStream.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException closeEx) {
                // Ignorer les erreurs de fermeture
            }
            
            if (listener != null) {
                listener.onGameMessage("ERROR", "Échec de la reconnexion: " + e.getMessage());
            }
            
            return false;
        }
    }
    
    // Déplacer la configuration du thread de réception dans une méthode séparée pour améliorer la clarté du code
    private void setupReceptionThread() {
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
                            code = Code.valueOf(parts[0].toUpperCase()); // Convertir en majuscules pour assurer la correspondance
                        } catch (IllegalArgumentException e) {
                            System.err.println("GameClient (ID: " + myPlayerId + "): Code inconnu du serveur: " + parts[0]);
                            if (listener != null) listener.onGameMessage("UNKNOWN_CODE", "Code serveur inconnu: " + parts[0]);
                            continue;
                        }
                        
                        String content = parts[1];

                        // Utiliser SwingUtilities pour s'assurer que les mises à jour sont faites sur le thread EDT
                        final String finalContent = content; // Variable finale nécessaire dans l'expression lambda
                        final Code finalCode = code;

                        javax.swing.SwingUtilities.invokeLater(() -> {
                            if (listener == null) return;

                            System.out.println("GameClient (ID: " + myPlayerId + "): Received message from server: " + finalCode.name() + " " + finalContent);

                            switch (finalCode) {
                                case ETAT:
                                    // dans la reponse du serveur on a deja etapeCoup
                                    System.out.println("GameClient: Received game state: " + finalContent);
                                    
                                    // decoder l'etat du jeu et la mise a jour
                                    GameStateParser.parseAndUpdateJeu(gameInstance, finalContent);

                                    System.out.println("GameClient: Current etapeCoup after parsing: " + gameInstance.getEtapeCoup());
                                    
                                    listener.onGameStateUpdate(gameInstance);
                                    break;

                                case GAGNE:
                                    listener.onGameMessage("WIN", finalContent);
                                    break;
                                case PERDU:
                                    listener.onGameMessage("LOSE", finalContent);
                                    break;
                                    
                                case PIECE:
                                    // nouveau format：PIECE:x:y:possibleMoves
                                    // possibleMoves format：TYPE_COUP:x:y;TYPE_COUP:x:y;...
                                    if (listener != null) {
                                        listener.onGameMessage("PIECE", finalContent);
                                    }
                                    break;
                                case COUP:
                                    // nouveau format：COUP:TYPE_COUP:success
                                    if (listener != null) {
                                        listener.onGameMessage("COUP", finalContent);
                                    }
                                    break;
                                case PLATEAU:
                                    // nouveau format：PLATEAU:TYPE_PLATEAU:success
                                    if (listener != null) {
                                        listener.onGameMessage("PLATEAU", finalContent);
                                    }
                                    break;                                
                                case ADVERSAIRE: // Exemple："Ce n'est pas votre tour" ou d'autres messages d'erreurs
                                    listener.onGameMessage("ADVERSAIRE", finalContent);
                                    break;
                                case ACTION:     //
                                case DIRECTION:  // Le serveur en attente du choix de l'action et de la direction
                                    listener.onGameMessage(finalCode.name(), finalContent);
                                    break;
                                case DESELECT:   // Le joueur a désélectionné une pièce
                                    listener.onGameMessage("DESELECT", finalContent);
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
                if (isConnected) { // Enregistrer uniquement lorsque la connexion est attendue
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
                disconnect(); // S'assurer de se déconnecter lorsque le thread se termine
            }
        });
        receptionThread.setName("GameClient-ReceptionThread-ID-" + myPlayerId);
        receptionThread.start();
        System.out.println("GameClient (ID: " + myPlayerId + "): Thread de réception démarré.");
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
    

    private String extractEtapeCoupFromState(String stateContent) {
        if (stateContent == null || stateContent.isEmpty()) {
            return null;
        }
        
        try {
            // etapeCoup:value;key1:value1;key2:value2;...
            String[] parts = stateContent.split(";");
            for (String part : parts) {
                if (part.startsWith("etapeCoup:")) {
                    return part.substring("etapeCoup:".length());
                }
            }
            
            // si on arrive pas a trouver etapeCoup，on le recupere dans gameInstance
            return String.valueOf(gameInstance.getEtapeCoup());
        } catch (Exception e) {
            System.err.println("GameClient: erreur de recuperer etatpeCoup: " + e.getMessage());
            return "0"; //valeur de default
        }
    }
}