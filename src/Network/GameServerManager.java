package Network;

import Modele.Coup;
import Modele.Jeu;
import Modele.Joueur;
import Modele.Piece;
import Modele.Plateau;
import Modele.Plateau.TypePlateau;
import SceneManager.HostingScene;
import SceneManager.SinglePlayerLobbyScene;
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
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class GameServerManager {

    private static final int PORT = 12345;
    private static final int MAX_PORT_ATTEMPTS = 5;  // 最大端口尝试次数
    private int currentPort = PORT;  // 当前使用的端口
    private ServerSocket serverSocket;
    private Thread acceptConnectionsThread;
    private Thread gameEngineThread;
    private Jeu gameInstance; // L'instance de Jeu

    private final BlockingQueue<Message> incomingMessages = new LinkedBlockingQueue<>();
    private final Map<Integer, BlockingQueue<String>> outgoingQueues = new ConcurrentHashMap<>();
    // Utilisation de synchronizedList pour la sécurité des threads lorsque plusieurs clients se connectent en même temps
    private final List<Integer> connectedClientIds = Collections.synchronizedList(new ArrayList<>());
    private volatile int clientIdCounter = 0; // volatile car il peut être accédé depuis plusieurs threads client handler
    private int maxClients = 2;
    private int currentTurnPlayerId; // ID du joueur dont c'est le tour

    private HostingScene hostingSceneCallback; // Callback pour HostingScene (si l'hôte est en mode multijoueur)
    private SinglePlayerLobbyScene singlePlayerCallbackScene; // Callback pour SinglePlayerLobbyScene (si l'hôte est en mode solo)
    private volatile boolean isServerRunning = false;
    // Stocker les récepteurs client
    private final Map<Integer, ClientReceiver> clientReceivers = new ConcurrentHashMap<>();
    
    // Variables pour le mode AI
    private boolean aiMode = false;
    private String aiDifficulty = "Facile"; // Par défaut: facile
    private Thread aiPlayerThread;
    
    // Interface pour le callback de déconnexion
    public interface PlayerDisconnectionListener {
        void onPlayerDisconnected();
    }
    
    // Référence au listener de déconnexion (GameScene)
    private PlayerDisconnectionListener disconnectionListener;
    private volatile boolean notifiedDisconnection = false;

    /**
     * Constructeur standard pour le mode multijoueur
     * @param callback Le callback vers HostingScene
     */
    public GameServerManager(HostingScene callback) {
        this.hostingSceneCallback = callback;
        this.aiMode = false;
    }
    
    /**
     * Constructeur pour le mode AI
     * @param callback Le callback vers SinglePlayerLobbyScene
     * @param aiMode True si c'est un jeu avec AI
     */
    public GameServerManager(SinglePlayerLobbyScene callback, boolean aiMode) {
        this.singlePlayerCallbackScene = callback;
        this.aiMode = aiMode;
    }

    /**
     * Démarrer le serveur de jeu
     */
    public void startServer() throws IOException {
        if (isServerRunning) {
            System.out.println("GameServerManager: Le serveur est déjà en cours d'exécution.");
            return;
        }
        
        boolean serverStarted = false;
        IOException lastException = null;
        
        // Tenter de démarrer le serveur sur différents ports si nécessaire
        for (int attempt = 0; attempt < MAX_PORT_ATTEMPTS; attempt++) {
            currentPort = PORT + attempt;  // Essayer le port de base + tentative
            try {
                serverSocket = new ServerSocket(currentPort);
                serverStarted = true;
                break;  // Sortir de la boucle si le serveur démarre avec succès
            } catch (IOException e) {
                lastException = e;
                System.out.println("GameServerManager: Échec de démarrage sur le port " + currentPort + 
                                   ": " + e.getMessage() + " - Tentative sur un autre port...");
                // Continue to try the next port
            }
        }
        
        if (!serverStarted) {
            System.err.println("GameServerManager: Impossible de démarrer le serveur après " + 
                              MAX_PORT_ATTEMPTS + " tentatives.");
            throw lastException;  // Lancer la dernière exception si toutes les tentatives échouent
        }
        
        isServerRunning = true;
        notifiedDisconnection = false; // 重置断开连接通知标记
        System.out.println("GameServerManager: Serveur démarré sur le port " + currentPort);

        // Créer et démarrer un nouveau thread d'acceptation
        createAndStartAcceptThread("GameServerThread");
    }

    /**
     * Définir la difficulté de l'AI
     * @param difficulty Niveau de difficulté: "Facile", "Moyen", "Difficile"
     */
    public void setAIDifficulty(String difficulty) {
        this.aiDifficulty = difficulty;
        System.out.println("GameServerManager: Difficulté AI définie à " + difficulty);
    }
    
    /**
     * Connecter un joueur AI au serveur
     * @param difficulty Niveau de difficulté
     */
    public void connectAIPlayer(String difficulty) {
        if (!aiMode) {
            System.out.println("GameServerManager: Impossible de connecter l'AI, mode AI non activé.");
            return;
        }
        
        setAIDifficulty(difficulty);
        
        // Si l'AI est déjà connecté, ne rien faire
        if (connectedClientIds.contains(2)) {
            System.out.println("GameServerManager: L'AI est déjà connecté.");
            return;
        }
        
        // Créer un thread pour simuler la connexion de l'AI
        aiPlayerThread = new Thread(() -> {
            try {
                System.out.println("GameServerManager: Connexion de l'AI en cours...");
                Thread.sleep(500); // Simuler un délai de connexion
                
                // Ajouter l'AI comme joueur 2
                synchronized (this) {
                    clientIdCounter = 1; // S'assurer que l'AI aura l'ID 2
                }
                
                // Créer une file d'attente pour les messages sortants vers l'AI
                BlockingQueue<String> aiOutgoingQueue = new LinkedBlockingQueue<>();
                outgoingQueues.put(2, aiOutgoingQueue);
                connectedClientIds.add(2);
                
                System.out.println("GameServerManager: AI connecté avec ID 2");
                
                // Notifier le SinglePlayerLobbyScene que l'AI est connecté
                if (singlePlayerCallbackScene != null) {
                    singlePlayerCallbackScene.onAIPlayerConnected();
                }
                
                // Démarrer le thread d'AI qui va "lire" les messages et y répondre
                Thread aiResponseThread = new Thread(this::processAIResponses, "AI-ResponseThread");
                aiResponseThread.start();
                
            } catch (InterruptedException e) {
                System.err.println("GameServerManager: Interruption pendant la connexion de l'AI: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }, "AI-ConnectionThread");
        
        aiPlayerThread.start();
    }
    
    /**
     * Traiter les réponses de l'AI
     */
    private void processAIResponses() {
        try {
            while (isServerRunning && connectedClientIds.contains(2)) {
                // Attendre que ce soit le tour de l'AI
                if (gameInstance != null && gameInstance.getJoueurCourant().getId() == 2) {
                    // Simuler un temps de réflexion basé sur la difficulté
                    int thinkingTime;
                    switch (aiDifficulty) {
                        case "Facile":
                            thinkingTime = 1000 + new Random().nextInt(1000); // 1-2 secondes
                            break;
                        case "Moyen":
                            thinkingTime = 700 + new Random().nextInt(800); // 0.7-1.5 secondes
                            break;
                        case "Difficile":
                            thinkingTime = 300 + new Random().nextInt(700); // 0.3-1 seconde
                            break;
                        default:
                            thinkingTime = 1000;
                    }
                    Thread.sleep(thinkingTime);
                    
                    // Générer un coup pour l'AI
                    generateAIMove();
                }
                
                // Pause pour éviter une utilisation CPU excessive
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            System.err.println("GameServerManager: Interruption du thread de réponse AI: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Générer un coup pour l'AI
     */
    private void generateAIMove() {
        if (gameInstance == null || !connectedClientIds.contains(2)) {
            return;
        }
        
        try {
            Joueur aiJoueur = gameInstance.getJoueur2();
            Plateau plateauCourant = gameInstance.getPlateauCourant();
            int etapeCoup = gameInstance.getEtapeCoup();
            
            // En fonction de l'étape du coup, générer l'action appropriée
            if (etapeCoup == 0) {
                // Sélectionner une pièce
                ArrayList<Piece> piecesDispo = new ArrayList<>();
                for (Piece piece : plateauCourant.getPieces(aiJoueur)) {
                    ArrayList<Coup> coups = gameInstance.getCoupPossibles(plateauCourant, piece);
                    if (!coups.isEmpty()) {
                        piecesDispo.add(piece);
                    }
                }
                
                if (!piecesDispo.isEmpty()) {
                    // Sélectionner une pièce en fonction de la difficulté
                    Piece selectedPiece;
                    if (aiDifficulty.equals("Difficile")) {
                        // En difficulté difficile, essaie de choisir la pièce avec le plus de mouvements possibles
                        selectedPiece = getBestPiece(piecesDispo, plateauCourant);
                    } else {
                        // Sinon, sélection aléatoire
                        selectedPiece = piecesDispo.get(new Random().nextInt(piecesDispo.size()));
                    }
                    
                    // Créer et ajouter le message à la file d'attente
                    String message = "0:null:" + plateauCourant.getType() + ":" + selectedPiece.getPosition().x + ":" + selectedPiece.getPosition().y;
                    incomingMessages.add(new Message(2, message));
                    System.out.println("AI: Sélection de pièce - " + message);
                }
            } else if (etapeCoup == 1 || etapeCoup == 2) {
                // Jouer un coup
                Piece pieceCourante = gameInstance.getPieceCourante();
                if (pieceCourante != null) {
                    ArrayList<Coup> coupsPossibles = gameInstance.getCoupPossibles(plateauCourant, pieceCourante);
                    
                    if (!coupsPossibles.isEmpty()) {
                        // Sélectionner un coup en fonction de la difficulté
                        Coup selectedCoup;
                        if (aiDifficulty.equals("Difficile")) {
                            // Tente de choisir un coup avantageux
                            selectedCoup = getBestCoup(coupsPossibles);
                        } else if (aiDifficulty.equals("Moyen")) {
                            // Mélange entre aléatoire et intelligent
                            if (new Random().nextDouble() < 0.7) {
                                selectedCoup = getBestCoup(coupsPossibles);
                            } else {
                                selectedCoup = coupsPossibles.get(new Random().nextInt(coupsPossibles.size()));
                            }
                        } else {
                            // Purement aléatoire pour niveau facile
                            selectedCoup = coupsPossibles.get(new Random().nextInt(coupsPossibles.size()));
                        }
                        
                        // Préparer le message en fonction du type de coup
                        String targetPlateau = plateauCourant.getType().name();
                        int targetX = pieceCourante.getPosition().x;
                        int targetY = pieceCourante.getPosition().y;
                        
                        // Déterminer la cible en fonction du type de coup
                        switch (selectedCoup.getTypeCoup()) {
                            case UP:
                                targetX -= 1;
                                break;
                            case DOWN:
                                targetX += 1;
                                break;
                            case LEFT:
                                targetY -= 1;
                                break;
                            case RIGHT:
                                targetY += 1;
                                break;
                            case JUMP:
                                targetPlateau = (plateauCourant.getType() == Plateau.TypePlateau.PAST) ? "PRESENT" : "FUTURE";
                                break;
                            case CLONE:
                                targetPlateau = (plateauCourant.getType() == Plateau.TypePlateau.FUTURE) ? "PRESENT" : "PAST";
                                break;
                        }
                        
                        // Créer et ajouter le message
                        String message = "0:null:" + targetPlateau + ":" + targetX + ":" + targetY;
                        incomingMessages.add(new Message(2, message));
                        System.out.println("AI: Coup - " + message);
                    }
                }
            } else if (etapeCoup == 3) {
                // Choisir le prochain plateau
                Plateau.TypePlateau[] plateauOptions = {Plateau.TypePlateau.PAST, Plateau.TypePlateau.PRESENT, Plateau.TypePlateau.FUTURE};
                Plateau.TypePlateau currentPlateauType = plateauCourant.getType();
                
                // Filtrer pour exclure le plateau actuel
                ArrayList<Plateau.TypePlateau> validOptions = new ArrayList<>();
                for (Plateau.TypePlateau option : plateauOptions) {
                    if (option != currentPlateauType) {
                        validOptions.add(option);
                    }
                }
                
                // Choisir un plateau
                Plateau.TypePlateau nextPlateau;
                if (aiDifficulty.equals("Difficile")) {
                    // Logique plus avancée pourrait être implémentée ici
                    // Pour l'instant, simplement aléatoire
                    nextPlateau = validOptions.get(new Random().nextInt(validOptions.size()));
                } else {
                    nextPlateau = validOptions.get(new Random().nextInt(validOptions.size()));
                }
                
                // Créer et ajouter le message
                String message = "0:" + nextPlateau + ":" + plateauCourant.getType() + ":0:0";
                incomingMessages.add(new Message(2, message));
                System.out.println("AI: Choix de plateau - " + message);
            }
        } catch (Exception e) {
            System.err.println("GameServerManager: Erreur lors de la génération du coup AI: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Obtenir la meilleure pièce à jouer (stratégie simple)
     */
    private Piece getBestPiece(ArrayList<Piece> pieces, Plateau plateau) {
        Piece bestPiece = pieces.get(0);
        int maxMoves = 0;
        
        for (Piece piece : pieces) {
            int moves = gameInstance.getCoupPossibles(plateau, piece).size();
            if (moves > maxMoves) {
                maxMoves = moves;
                bestPiece = piece;
            }
        }
        
        return bestPiece;
    }
    
    /**
     * Obtenir le meilleur coup à jouer (stratégie simple)
     */
    private Coup getBestCoup(ArrayList<Coup> coups) {
        // Pour l'instant, une stratégie simple: priorité aux actions spéciales (JUMP/CLONE)
        
        // Préférer les actions spéciales (JUMP/CLONE) si disponibles
        for (Coup coup : coups) {
            if (coup.getTypeCoup() == Coup.TypeCoup.JUMP || coup.getTypeCoup() == Coup.TypeCoup.CLONE) {
                return coup;
            }
        }
        
        // Sinon, choisir un coup aléatoire
        return coups.get(new Random().nextInt(coups.size()));
    }

    public synchronized void startGameEngine() { // synchronized pour éviter plusieurs appels
        if (gameEngineThread != null && gameEngineThread.isAlive()) {
            System.out.println("GameServerManager: Le moteur de jeu est déjà en cours d'exécution.");
            return;
        }
        if (connectedClientIds.size() == maxClients) {
            System.out.println("GameServerManager: Démarrage du moteur de jeu...");
            // Initialiser l'instance de jeu
            gameInstance = new Jeu(); // => Liaison entre le reseau et le modèle Jeu  

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

                // Vérifier si la partie est terminée
                if (checkGameOver()) break;

                System.out.println("\nGameServerManager: Boucle de jeu en cours...");
                int etapeCoup = gameInstance.getEtapeCoup();
                System.out.println("GameServerManager: En attente de messages des clients (étape du coup: " + etapeCoup + ")...");
                Joueur joueurCourant = gameInstance.getJoueurCourant();
                Plateau plateauCourant = gameInstance.getPlateauCourant();

                // Verifier si l'etapeCoup est 0, si oui, verifier si sur le plateau il y a des pieces de joueurCourant
                if (etapeCoup == 0) {
                    if (joueurCourant.getId() == 1 && plateauCourant.getNbBlancs() == 0  || joueurCourant.getId() == 2 && plateauCourant.getNbNoirs() == 0) {
                        // Si le joueur n'a plus de pièces, passer à l'étape 3
                        gameInstance.setEtapeCoup(3);
                        System.out.println("GameServerManager: Aucun coup possible pour le joueur " + joueurCourant.getId() + ", passage à l'étape 3.");
                        sendMessageToClient(joueurCourant.getId(), Code.ETAT.name() + ":" + gameInstance.getGameStateAsString());
                        continue; // Passer à l'étape suivante sans attendre de message
                    } else {
                        // Attendre un message du joueur courant
                        System.out.println("GameServerManager: En attente du message du joueur " + joueurCourant.getId() + "...");
                    }
                }

                Message msg = incomingMessages.take();
                int clientId = msg.clientId;
                System.out.println("GameServerManager: Traitement du message du client " + clientId + ": " + msg.contenu);

                // Vérifier si c'est le tour du joueur actuel
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

                System.out.println("GameServerManager: Étape du coup: " + etapeCoup);

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
                    gameInstance.Undo();
                    System.out.println("GameServerManager: Undo effectué par le joueur " + clientId);
                    gameInstance.setEtapeCoup(0); // Retour à l'étape 0
                    gameInstance.setPieceCourante(null);
                    sendMessageToClient(clientId, Code.DESELECT.name() + ":" + "Désélection de la pièce courante.");
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

                // verifie si le piece choisi correspont au plateau actuel pour ce joueur
                Plateau.TypePlateau selectedPlateauType = null;
                try {
                    selectedPlateauType = Plateau.TypePlateau.valueOf(parts[2]);
                } catch (IllegalArgumentException e) {
                    System.err.println("GameServerManager: Type de plateau invalide: " + parts[2]);
                    sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Type de plateau invalide.");
                    continue;
                }

                switch (etapeCoup) {
                    case 0: // Étape initiale: sélection d'une pièce

                        if (selectedPlateauType != null && selectedPlateauType != plateauCourant.getType()) {
                            System.err.println("GameServerManager: Plateau sélectionné " + selectedPlateauType + " ne correspond pas au plateau courant " + plateauCourant.getType());
                            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Le plateau sélectionné ne correspond pas au plateau courant.");
                            continue;
                        }
                        
                        if (selectedPiece == null) {
                            sendMessageToClient(clientId, Code.ACTION.name() + ":" + "Aucune pièce à cette position.");
                            continue;
                        }

                        // Vérifier que la pièce appartient au joueur courant
                        if (!selectedPiece.getOwner().equals(joueurCourant)) {
                            sendMessageToClient(clientId, Code.ACTION.name() + ":" + "Cette pièce ne vous appartient pas.");
                            continue;
                        }

                        // Vérifier s'il y a des coups possibles pour cette pièce
                        ArrayList<Coup> coupsPossibles = gameInstance.getCoupPossibles(plateauCourant, selectedPiece);
                        if (coupsPossibles.isEmpty()) {
                            sendMessageToClient(clientId, Code.PIECE.name() + ":" + selectedPiece.getPosition().x + ":" + selectedPiece.getPosition().y + ";null" );
                            continue;
                        }

                        String possibleMovesStr = getPossibleMovesString(gameInstance, plateauCourant, selectedPiece);

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
                        Plateau.TypePlateau currentPieceBoard;
                        Point piecePosition; 
                        Point clickedPosition = new Point(x, y);
                        Coup.TypeCoup typeCoup ;
                        Piece pieceCourante = gameInstance.getPieceCourante();

                        if (pieceCourante == null) {
                            System.err.println("GameServerManager: Pièce courante null à l'étape 1");
                            gameInstance.setEtapeCoup(0); // Retour à l'étape 0
                            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Erreur: sélectionnez d'abord une pièce.");
                            continue;
                        }

                        currentPieceBoard = plateauCourant.getType();
                        
                        piecePosition = pieceCourante.getPosition();
                        
                        if (selectedPlateauType != null && selectedPlateauType == plateauCourant.getType() && selectedPiece != null && selectedPiece.getOwner() == joueurCourant && selectedPiece.getPosition() != pieceCourante.getPosition()) {
                            // Si le joueur click sur un autre pion de son équipe
                            // => selectionner la piece courante
                            // Deselectionner la pièce courante
                            sendMessageToClient(clientId, Code.DESELECT.name() + ":" + "Désélection de la pièce courante.");

                            // Sélectionner la nouvelle pièce
                            gameInstance.setPieceCourante(selectedPiece);
                            gameInstance.setEtapeCoup(1); // Rester à l'étape 1

                            System.out.println("GameServerManager: Pièce sélectionnée à " + selectedPiece.getPosition().x + "," + selectedPiece.getPosition().y + 
                                              " sur plateau " + plateauCourant.getType());

                            possibleMovesStr = getPossibleMovesString(gameInstance, plateauCourant, selectedPiece);
                            sendMessageToClient(clientId, Code.PIECE.name() + ":" + selectedPiece.getPosition().x + ":" + selectedPiece.getPosition().y + ";" + possibleMovesStr);

                            sendGameStateToAllClients();

                            continue;
                            
                        }

                        typeCoup = determineMovementType(piecePosition, clickedPosition, 
                                                      currentPieceBoard, selectedPlateauType, clientId);
                        if (typeCoup == null) {
                            gameInstance.setEtapeCoup(0); // Retour à l'étape 0
                            gameInstance.setPieceCourante(null);
                            sendMessageToClient(clientId, Code.DESELECT.name() + ":" + "Désélection de la pièce courante.");
                            sendGameStateToAllClients();
                            continue;
                        }
                        

                        System.out.println("GameServerManager: DEBUGGING: appeler provessMove dans etapeCoup ");
                        if (processMove(pieceCourante, plateauCourant, typeCoup, clientId, 2)) {
                            plateauCourant = gameInstance.getPlateauCourant();
                            possibleMovesStr = getPossibleMovesString(gameInstance, plateauCourant, pieceCourante);
                            sendMessageToClient(clientId, Code.PIECE.name() + ":" + pieceCourante.getPosition().x + ":" + pieceCourante.getPosition().y + ";" + possibleMovesStr);
                            sendGameStateToAllClients();
                            continue; // Si le coup est réussi, continuer à l'étape 2
                        } else {
                            gameInstance.setEtapeCoup(0); // Retour à l'étape 0
                            gameInstance.setPieceCourante(null);
                            sendMessageToClient(clientId, Code.DESELECT.name() + ":" + "Désélection de la pièce courante.");
                            sendGameStateToAllClients();
                            break;
                        }


                    case 2: // Deuxième coup: déplacement ou action spéciale
                        pieceCourante = gameInstance.getPieceCourante();
                        if (pieceCourante == null) {
                            System.err.println("GameServerManager: Pièce courante null à l'étape 2");
                            gameInstance.setEtapeCoup(0); // Retour à l'étape 0
                            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Erreur: sélectionnez d'abord une pièce.");
                            continue;
                        }


                        currentPieceBoard = plateauCourant.getType();
                        

                        clickedPosition = new Point(x, y);
                        piecePosition = pieceCourante.getPosition();
                        

                        typeCoup = determineMovementType(piecePosition, clickedPosition, 
                                                      currentPieceBoard, selectedPlateauType, clientId);
                        if (typeCoup == null) {
                            continue;
                        }
                        

                        System.out.println("GameServerManager: DEBUGGING: appeler provessMove dans etapeCoup ");
                        if (!processMove(pieceCourante, plateauCourant, typeCoup, clientId, 3)) {
                            gameInstance.setEtapeCoup(2); // Rester à l'étape 2
                        }
                        break;

                    case 3: // Sélection du prochain plateau
                        if (prochainPlateau == null) {
                            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Vous devez sélectionner un plateau (PAST, PRESENT ou FUTURE).");
                            continue;
                        }

                        System.out.println("GameServerManager: Sélection du prochain plateau: " + prochainPlateau);

                        // Vérifier si le prochain plateau est valide
                        TypePlateau currentPlateauJoueur = joueurCourant.getProchainPlateau();

                        if (prochainPlateau == currentPlateauJoueur) {
                            System.err.println("GameServerManager: Le prochain plateau sélectionné est le même que le plateau actuel.");
                            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Le prochain plateau doit être différent du plateau actuel.");
                            continue;
                        }

                        // Définir le prochain plateau pour le joueur
                        joueurCourant.setProchainPlateau(prochainPlateau);

                        sendMessageToClient(clientId, Code.PLATEAU.name() + ":" + prochainPlateau + ":success");
                        gameInstance.setPieceCourante(null); // Réinitialiser la pièce courante

                        System.out.println("FOR DEBUGGING 1: " + gameInstance.getGameStateAsString());
                        // Réinitialiser pour le prochain joueur
                        gameInstance.setEtapeCoup(0);
                        gameInstance.joueurSuivant();
                        gameInstance.majPlateauCourant();
                        gameInstance.updateHistoriqueJeu();
                        // Mettre à jour le joueur courant
                        currentTurnPlayerId = gameInstance.getJoueurCourant().getId();

                        System.out.println("FOR DEBUGGING 2: " + gameInstance.getGameStateAsString());

                        if (gameInstance.getJoueurCourant().getId() == 1) {
                            if (gameInstance.getPlateauCourant().getNbBlancs() == 0) {
                                gameInstance.setEtapeCoup(3);
                            }
                        } else if (gameInstance.getJoueurCourant().getId() == 2) {
                            if (gameInstance.getPlateauCourant().getNbNoirs() == 0) {
                                gameInstance.setEtapeCoup(3);
                            }
                        }
                        sendGameStateToAllClients();


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


    private Coup.TypeCoup determineMovementType(Point piecePosition, Point clickedPosition, 
                                               Plateau.TypePlateau currentPieceBoard, Plateau.TypePlateau selectedPlateauType,
                                               int clientId) {
        
        // verifie si c'est jump ou clone
        boolean isSamePosition = (piecePosition.x == clickedPosition.x && piecePosition.y == clickedPosition.y);
        boolean isDifferentPlateau = (currentPieceBoard != selectedPlateauType);
        

        Coup.TypeCoup typeCoup = null;
        
        if (isSamePosition && isDifferentPlateau) {
            if ((currentPieceBoard == Plateau.TypePlateau.PAST && selectedPlateauType == Plateau.TypePlateau.PRESENT) ||
                (currentPieceBoard == Plateau.TypePlateau.PRESENT && selectedPlateauType == Plateau.TypePlateau.FUTURE)) {
                // PAST→PRESENT ou PRESENT→FUTURE :JUMP
                typeCoup = Coup.TypeCoup.JUMP;
                System.out.println("GameServerManager: Détection automatique d'une opération JUMP");
            } else if ((currentPieceBoard == Plateau.TypePlateau.PRESENT && selectedPlateauType == Plateau.TypePlateau.PAST) ||
                      (currentPieceBoard == Plateau.TypePlateau.FUTURE && selectedPlateauType == Plateau.TypePlateau.PRESENT)) {
                // PRESENT→PAST ou FUTURE→PRESENT : CLONE
                typeCoup = Coup.TypeCoup.CLONE;
                System.out.println("GameServerManager: Détection automatique d'une opération CLONE");
            } else {
                // cas ou le changement de plateau interdit
                System.err.println("GameServerManager: Transition de plateau non autorisée: " + currentPieceBoard + " -> " + selectedPlateauType);
                sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Transition de plateau non autorisée.");
                return null;
            }
        } else if (currentPieceBoard == selectedPlateauType) {
            //mouvement dans le meme plateau
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
                // direction invalide
                System.err.println("GameServerManager: Déplacement invalide: dx=" + dx + ", dy=" + dy);
                sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Déplacement invalide.");
                return null;
            }
        } else {
            // mouvemnt invalide dans le plateau different
            System.err.println("GameServerManager: Opération invalide entre plateaux différents à des positions différentes.");
            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Pour JUMP/CLONE, cliquez sur la même position dans un plateau adjacent.");
            return null;
        }
        
        return typeCoup;
    }
    
    // traitement de move et verifie si cela marche
    private boolean processMove(Piece pieceCourante, Plateau plateauCourant, Coup.TypeCoup typeCoup, 
                               int clientId, int nextEtape) {

        Coup coup = new Coup(pieceCourante, plateauCourant, typeCoup);
        

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
        
        // executer le mouvement
        boolean coupReussi = gameInstance.jouerCoup(coup);
        if (coupReussi) {

            // confirme la validation de move
            if (nextEtape == 3) {
                gameInstance.setEtapeCoup(3);
                System.out.println("GameServerManager: Deuxième coup réussi, etapeCoup passé à 3");
            }
            
            // recuperer la nouvelle position de piece et le plateau
            Point newPosition = pieceCourante.getPosition();
            Plateau currentPlateau = gameInstance.getPlateauCourant();
            
            //retourne le message de reussite
            String responseMessage = Code.COUP.name() + ":" + typeCoup.name() + ":" 
                                   + newPosition.x + ":" + newPosition.y + ":" 
                                   + currentPlateau.getType().name() + ":success";
            sendMessageToClient(clientId, responseMessage);

            if (nextEtape == 2) {
                plateauCourant = gameInstance.getPlateauCourant();
                String possibleMovesStr = getPossibleMovesString(gameInstance, plateauCourant, pieceCourante);
                if (!"null".equals(possibleMovesStr)) {
                    gameInstance.setEtapeCoup(2);
                    sendMessageToClient(clientId, Code.PIECE.name() + ":" + pieceCourante.getPosition().x + ":" + pieceCourante.getPosition().y + ";" + possibleMovesStr);
                } else {
                    gameInstance.setEtapeCoup(3);
                }
            }
            sendGameStateToAllClients();
            return true;
        } else {
            sendMessageToClient(clientId, Code.ADVERSAIRE.name() + ":" + "Erreur lors de l'application du coup.");
            return false;
        }
    }

    // Vérifie si le jeu est terminé et envoie les messages appropriés aux clients
    private boolean checkGameOver() {
        // Vérifier l'état du jeu
        int gameState = gameInstance.gameOver(gameInstance.getJoueurCourant());
        int winnerId = 0;

        System.out.println("GameServerManager: Game state: " + gameState);

        // Déterminer le gagnant en fonction de l'état du jeu
        if (gameState == 1) {
            winnerId = gameInstance.getJoueur1().getId();  
        } else if (gameState == 2) {
            winnerId = gameInstance.getJoueur2().getId(); 
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

            // Envoyer les messages de victoire et de défaite à tous les clients connectés
            for (Integer clientId : connectedClientIds) {
                if (clientId == winnerId) {
                    sendMessageToClient(clientId, winnerMsg);
                } else {
                    sendMessageToClient(clientId, loserMsg);
                }
            }

            System.out.println("GameServerManager: Jeu terminé! Gagnant: " + winnerId);
        }

        return winnerId != 0;
    }

    // Envoie l'état du jeu à tous les clients connectés
    private void sendGameStateToAllClients() {
        if (gameInstance == null) {
            return;
        }

        String gameStateString = gameInstance.getGameStateAsString();

        // etapeCoup
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
        
        // Envoyer un message SERVER_SHUTDOWN à tous les clients connectés avant de fermer
        if (!connectedClientIds.isEmpty()) {
            System.out.println("GameServerManager: Envoi de message SERVER_SHUTDOWN à " + connectedClientIds.size() + " clients");
            String shutdownMessage = Code.SERVER_SHUTDOWN.name() + ":Le serveur est en cours de fermeture.";
            sendStateToAllClients(shutdownMessage);
            
            // Attendre un court instant pour permettre l'envoi des messages
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        isServerRunning = false; // Définir ce drapeau en premier
        notifiedDisconnection = false; // Réinitialiser l'indicateur de notification

        // Fermer les threads client
        closeAllClientConnections();

        try {
            // Interrompre les threads
            if (acceptConnectionsThread != null && acceptConnectionsThread.isAlive()) {
                acceptConnectionsThread.interrupt(); // Essayer d'interrompre le thread d'acceptation
                System.out.println("GameServerManager: Thread d'acceptation interrompu.");
            }
            
            // Attendre un peu avant de fermer le socket
            Thread.sleep(100);
            
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // Fermer le server socket fera que accept() lève une exception et sort de la boucle
                System.out.println("GameServerManager: ServerSocket fermé.");
                // Tentative de libération forcée du port
                serverSocket = null;
                System.gc(); // Suggestion de collecte des déchets
            }
        } catch (IOException e) {
            System.err.println("GameServerManager: Erreur lors de la fermeture du ServerSocket: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (gameEngineThread != null && gameEngineThread.isAlive()) {
            gameEngineThread.interrupt(); // Essayer d'interrompre le thread du moteur de jeu
            System.out.println("GameServerManager: Thread du moteur de jeu interrompu.");
        }
        
        // Arrêter le thread AI s'il est en cours
        if (aiPlayerThread != null && aiPlayerThread.isAlive()) {
            aiPlayerThread.interrupt();
            System.out.println("GameServerManager: Thread AI interrompu.");
        }

        // Supprimer les clients connectés et leurs files d'attente
        connectedClientIds.clear();
        outgoingQueues.clear(); // Les ClientSenders se termineront automatiquement lorsque la file d'attente sera vidée ou qu'une exception sera levée
        incomingMessages.clear(); // Les ClientReceivers se termineront lorsque le socket sera fermé
        clientReceivers.clear(); // Nettoyer explicitement les récepteurs client

        System.out.println("GameServerManager: Serveur arrêté.");
        
        // Attendre un peu plus pour s'assurer que toutes les ressources réseau sont libérées
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Réinitialiser le compteur pour le prochain démarrage du serveur (le cas échéant)
        clientIdCounter = 0;
    }
    
    /**
     * Ferme proprement toutes les connexions client
     */
    private void closeAllClientConnections() {
        System.out.println("GameServerManager: Fermeture de toutes les connexions client...");
        // Fermer tous les récepteurs client pour libérer leurs sockets
        for (Map.Entry<Integer, ClientReceiver> entry : clientReceivers.entrySet()) {
            ClientReceiver receiver = entry.getValue();
            if (receiver != null) {
                System.out.println("GameServerManager: Fermeture de la connexion du client " + entry.getKey());
                receiver.closeConnection();
            }
        }
        
        // Attendre un instant pour que les connexions se ferment proprement
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Envoie l'état du jeu à tous les clients connectés
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

    // Envoie un message spécifique à un client
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

    /**
     * Retourne le port actuellement utilisé par le serveur
     * @return Le numéro de port actuel
     */
    public int getCurrentPort() {
        return currentPort;
    }

    public boolean isServerRunning() {
        return isServerRunning;
    }

    private String getPossibleMovesString(Jeu gameInstance, Plateau plateauCourant, Piece selectedPiece) {
        ArrayList<Coup> coupsPossibles = gameInstance.getCoupPossibles(plateauCourant, selectedPiece);

        if (coupsPossibles.isEmpty()) {
            return "null";
        }

        StringBuilder possibleMovesStr = new StringBuilder();
        
        for (Coup coup : coupsPossibles) {
            Point currentPos = selectedPiece.getPosition();
            Point targetPos = new Point(currentPos.x, currentPos.y); 
            String nextPlateau = plateauCourant.getType().name();

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
                    nextPlateau = (coup.getPltCourant().getType() == Plateau.TypePlateau.PAST) ? "PRESENT" : "FUTURE";
                    break;
                case CLONE:
                    nextPlateau = (coup.getPltCourant().getType() == Plateau.TypePlateau.FUTURE) ? "PRESENT" : "PAST";
                    break;
            }

            possibleMovesStr.append(nextPlateau).append(":").append(targetPos.x).append(":").append(targetPos.y).append(";");
        }
        
        return possibleMovesStr.toString();
    }

    /**
     * Définit le listener de déconnexion pour la phase de jeu
     * @param listener L'objet qui implémente PlayerDisconnectionListener (GameScene)
     */
    public void setDisconnectionListener(PlayerDisconnectionListener listener) {
        this.disconnectionListener = listener;
        System.out.println("GameServerManager: Listener de déconnexion défini");
    }

    /**
     * Gestion de la déconnexion d'un client
     * @param clientId ID du client qui s'est déconnecté
     */
    public synchronized void handleClientDisconnection(int clientId) {
        System.out.println("GameServerManager: Client " + clientId + " s'est déconnecté.");
        
        connectedClientIds.remove(Integer.valueOf(clientId));
        outgoingQueues.remove(clientId);
        clientReceivers.remove(clientId);

        // Si nous sommes en phase de jeu (gameInstance existe)
        if (gameInstance != null) {
            // Notifier l'autre joueur que son adversaire s'est déconnecté
            int otherPlayerId = (clientId == 1) ? 2 : 1;
            
            if (connectedClientIds.contains(otherPlayerId)) {
                String disconnectionMessage = Code.ADVERSAIRE.name() + ":Votre adversaire (Joueur " + clientId + ") s'est déconnecté.";
                sendMessageToClient(otherPlayerId, disconnectionMessage);
                System.out.println("GameServerManager: Notification envoyée au joueur " + otherPlayerId + " que le joueur " + clientId + " s'est déconnecté.");
            }
            
            // Notifier GameScene qu'un joueur s'est déconnecté, seulement si nous n'avons pas déjà notifié
            if (disconnectionListener != null && !notifiedDisconnection) {
                notifiedDisconnection = true; // Marquer comme déjà notifié
                System.out.println("GameServerManager: Notification à GameScene qu'un joueur s'est déconnecté");
                disconnectionListener.onPlayerDisconnected();
            }
        } else {
            // Si nous sommes encore en phase de Lobby (gameInstance n'existe pas encore)
            System.out.println("GameServerManager: Déconnexion en phase de Lobby, nettoyage des ressources du joueur " + clientId);
            
            // Si c'est le joueur 2 qui s'est déconnecté, on redémarre le thread d'acceptation pour permettre 
            // à un nouveau joueur 2 de se connecter
            if (clientId == 2 && isServerRunning) {
                // Réinitialisation du compteur d'ID pour que le prochain client ait l'ID 2
                synchronized (this) {
                    clientIdCounter = 1; // Réinitialiser à 1, le prochain ID sera 2
                }
                System.out.println("GameServerManager: Réinitialisation du compteur d'ID client à 1 (prochain ID sera 2)");
                System.out.println("GameServerManager: Redémarrage du thread d'acceptation pour permettre une nouvelle connexion.");
                restartAcceptConnectionsThread();
            }
        }
        
        // Si le joueur 2 s'est déconnecté et nous avons un callback pour HostingScene
        if (clientId == 2 && hostingSceneCallback != null) {
            notifyPlayerTwoDisconnected();
        }
        
        System.out.println("GameServerManager: Clients restants connectés: " + connectedClientIds.size());
    }
    
    /**
     * Notifier HostingScene que le joueur 2 s'est déconnecté
     */
    private void notifyPlayerTwoDisconnected() {
        if (hostingSceneCallback != null) {
            System.out.println("GameServerManager: Notification à HostingScene que le joueur 2 s'est déconnecté.");
            hostingSceneCallback.onPlayerTwoDisconnected();
        }
    }

    /**
     * Redémarre le thread d'acceptation des connexions après la déconnexion d'un client
     */
    private void restartAcceptConnectionsThread() {
        // Vérifier si un thread existe déjà et s'il est toujours en cours d'exécution
        if (acceptConnectionsThread != null && acceptConnectionsThread.isAlive()) {
            System.out.println("GameServerManager: Le thread d'acceptation est déjà en cours d'exécution.");
            return;
        }
        
        // 使用封装方法创建并启动线程
        createAndStartAcceptThread("GSM-AcceptConnectionsThread-Restarted");
    }
    
    /**
     * Crée et démarre un thread d'acceptation des connexions
     * @param threadName Nom du thread à créer
     */
    private void createAndStartAcceptThread(String threadName) {
        acceptConnectionsThread = new Thread(() -> {
            try {
                System.out.println("GameServerManager: Thread d'acceptation " + threadName + " démarré.");
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
                       // Suivre l'ordre de protocole unifié entre le client et le serveur :
                       // 1. Le serveur crée un flux de sortie et le flush
                        ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        out.flush();

                        // 2. Le client crée un flux d'entrée
                        // 3. Le client crée un flux de sortie et le flush
                        // 4. Le serveur crée un flux d'entrée
                        ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());

                        // 5. Le serveur envoie ID
                        out.writeObject("ID:" + newClientId);
                        out.flush();
                        System.out.println("GameServerManager: ID " + newClientId + " envoyé au client.");

                        BlockingQueue<String> clientOutgoingQueue = new LinkedBlockingQueue<>();
                        outgoingQueues.put(newClientId, clientOutgoingQueue);
                        connectedClientIds.add(newClientId);


                        // Créer et démarrer ClientReceiver et ClientSender avec l'ID du client
                        ClientReceiver receiver = new ClientReceiver(in, incomingMessages, newClientId, this);
                        // 设置客户端Socket，以便在关闭连接时能够正确关闭Socket
                        receiver.setClientSocket(clientSocket);
                        Thread receiverThread = new Thread(
                                receiver,
                                "ClientReceiver-" + newClientId
                        );
                        receiverThread.start();
                        
                        // Stocker le récepteur client
                        clientReceivers.put(newClientId, receiver);

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
                            // ignore les erreurs dans la fermeture
                        }
                        continue; // sortie d'iteration acutelle
                    }

                    if (connectedClientIds.size() == maxClients) {
                        System.out.println("GameServerManager: Nombre maximum de " + maxClients + " joueurs atteint.");
                        if (hostingSceneCallback != null) {
                            // Si HostingScene a un callback, le notifier.
                            // HostingScene décidera quand appeler startGameEngine().
                            hostingSceneCallback.onPlayerTwoConnected();
                            System.out.println("GameServerManager: Notifié HostingScene que le joueur 2 est connecté.");
                        } else if (singlePlayerCallbackScene != null && aiMode) {
                            // Notifier SinglePlayerLobbyScene que l'AI est connecté
                            singlePlayerCallbackScene.onAIPlayerConnected();
                            System.out.println("GameServerManager: Notifié SinglePlayerLobbyScene que l'AI est connecté.");
                        } else {
                            // Si aucun callback (mode solo hébergé par GameScene),
                            // démarrer automatiquement GameEngineServer.
                            System.out.println("GameServerManager: Pas de callback HostingScene, démarrage automatique du moteur de jeu.");
                            startGameEngine();
                        }
                    }
                }
                System.out.println("GameServerManager: Thread d'acceptation terminé.");
            } catch (IOException e) {
                if (isServerRunning && serverSocket != null && !serverSocket.isClosed()) {
                    System.err.println("GameServerManager: Erreur lors de l'acceptation d'une connexion: " + e.getMessage());
                } else {
                    System.out.println("GameServerManager: Le socket serveur a été fermé, arrêt de l'acceptation des connexions.");
                }
            }
        });
        acceptConnectionsThread.setName(threadName);
        acceptConnectionsThread.start();
    }

}