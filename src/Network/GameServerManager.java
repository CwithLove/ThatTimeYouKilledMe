package Network;

import Modele.Coup;
import Modele.Jeu;
import Modele.Joueur;
import Modele.Piece;
import Modele.Plateau;
import Modele.Plateau.TypePlateau;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue; // Importer la classe Jeu

public class GameServerManager {

    private static final int PORT = 12345;
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

    public boolean isServerRunning() {
        return isServerRunning && serverSocket != null && !serverSocket.isClosed();
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
}