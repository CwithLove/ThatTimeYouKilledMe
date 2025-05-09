package Network;

import Modele.Jeu; // Importer la classe Jeu
import Modele.Coup; // Importer la classe Coup
import Modele.Plateau; // Importer la classe Plateau
import Modele.Piece; // Importer la classe Piece
import Modele.Joueur; // Importer la classe Joueur

import java.awt.Point;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List; // Ajouter cet import
import java.util.ArrayList; // Ajouter cet import

public class GameEngineServer implements Runnable {
    private Jeu game; // Instance de Modele.Jeu
    private BlockingQueue<Message> incomingClientMessages; // File des messages entrants de tous les clients
    private Map<Integer, BlockingQueue<String>> outgoingClientQueues; // Map des files de messages sortants pour chaque client
    private int currentTurnPlayerId; // ID du joueur dont c'est le tour
    private Map<Integer, Joueur> playerMap; // Association entre l'ID du client et l'objet Joueur dans le jeu
    private List<Integer> connectedClientIds; // Liste des IDs des clients connectés, dans l'ordre

    public GameEngineServer(BlockingQueue<Message> incomingMessages,
            Map<Integer, BlockingQueue<String>> outgoingQueues,
            List<Integer> connectedClientIds) {
        this.incomingClientMessages = incomingMessages;
        this.outgoingClientQueues = outgoingQueues;
        this.connectedClientIds = connectedClientIds;
        this.game = new Jeu(); // Initialiser Modele.Jeu

        playerMap = new ConcurrentHashMap<>();
        // Associer Joueur 1 au premier client connecté, Joueur 2 au deuxième client
        if (connectedClientIds.size() >= 1) {
            playerMap.put(connectedClientIds.get(0), game.getJoueur1()); // Le premier client contrôle J1
        }
        if (connectedClientIds.size() >= 2) {
            playerMap.put(connectedClientIds.get(1), game.getJoueur2()); // Le deuxième client contrôle J2
        }

        this.currentTurnPlayerId = game.getJoueurCourant().getId(); // Tour du premier joueur
    }

    @Override
    public void run() {
        System.out.println("GameEngineServer : Boucle de jeu démarrée.");
        try {
            // Envoyer l'état initial du jeu à tous les clients
            // Attendre un peu pour s'assurer que les clients ont reçu les flux et sont prêts
            Thread.sleep(500);
            sendGameStateToAllClients();

            while (true) {
                // Attendre un message du client (appel bloquant)
                Message msg = incomingClientMessages.take();

                // Récupérer le Joueur correspondant à l'ID du client qui a envoyé le message
                Joueur senderJoueur = playerMap.get(msg.clientId);

                // Vérifier si le message provient du joueur dont c'est le tour et si cela concerne ses pièces
                if (senderJoueur == null || !senderJoueur.equals(game.getJoueurCourant())) {
                    System.out.println("GameEngineServer : Message du joueur " + msg.clientId
                            + " ignoré car ce n'est pas son tour ou il est invalide.");
                    sendMessageToClient(msg.clientId,
                            Code.ADVERSAIRE.name() + ":" + "Ce n'est pas votre tour ou la commande est invalide.");
                    continue;
                }

                System.out.println("GameEngineServer : Reçu du client " + msg.clientId + " : " + msg.contenu);

                // Analyser la commande du client et appliquer le coup
                // Format de la commande du client :
                // <TYPE_COUP>:<PLATEAU_TYPE>:<ROW>:<COL>[:<DIR_DX>:<DIR_DY>]
                // Exemple : MOVE:PRESENT:1:2:0:1 (déplacer une pièce de (1,2) sur Present vers la droite de 1 case)
                // CLONE:PRESENT:0:0 (cloner une pièce à (0,0) sur Present)
                // JUMP:PAST:2:1 (sauter une pièce à (2,1) sur Past)

                String[] parts = msg.contenu.split(":");
                if (parts.length < 4) { // Une commande minimale doit contenir TypeCoup, PlateauType, Row, Col
                    System.err.println("GameEngineServer : Commande mal formatée : " + msg.contenu);
                    sendMessageToClient(msg.clientId, Code.ADVERSAIRE.name() + ":" + "Commande mal formatée.");
                    continue;
                }

                try {
                    Coup.TypeCoup typeCoup = Coup.TypeCoup.valueOf(parts[0]);
                    Plateau.TypePlateau plateauType = Plateau.TypePlateau.valueOf(parts[1]);
                    int row = Integer.parseInt(parts[2]);
                    int col = Integer.parseInt(parts[3]);

                    Point direction = new Point(0, 0); // Par défaut pour CLONE/JUMP
                    if (typeCoup == Coup.TypeCoup.MOVE) {
                        if (parts.length == 6) { // MOVE nécessite dx, dy
                            int dx = Integer.parseInt(parts[4]);
                            int dy = Integer.parseInt(parts[5]);
                            direction = new Point(dx, dy);
                        } else {
                            System.err.println("GameEngineServer : Commande MOVE incomplète : " + msg.contenu);
                            sendMessageToClient(msg.clientId,
                                    Code.ADVERSAIRE.name() + ":" + "Commande MOVE incomplète.");
                            continue;
                        }
                    }

                    // Récupérer la pièce et le joueur correspondant
                    Plateau selectedPlateau = game.getPlateauByType(plateauType);
                    if (selectedPlateau == null) {
                        System.err.println("GameEngineServer : Plateau invalide : " + plateauType);
                        sendMessageToClient(msg.clientId, Code.ADVERSAIRE.name() + ":" + "Plateau invalide.");
                        continue;
                    }

                    Piece selectedPiece = selectedPlateau.getPiece(row, col);
                    // Vérifier si la pièce appartient bien au joueur actuel
                    if (selectedPiece == null || !selectedPiece.getOwner().equals(senderJoueur)) {
                        System.out.println("GameEngineServer : Le joueur " + msg.clientId
                                + " a sélectionné une pièce qui ne lui appartient pas ou qui n'existe pas à cette position.");
                        sendMessageToClient(msg.clientId, Code.ADVERSAIRE.name() + ":" + "Pièce invalide.");
                        continue;
                    }

                    // Créer un Coup et l'appliquer
                    Coup playerCoup = new Coup(selectedPiece, direction, selectedPlateau, typeCoup);

                    boolean isValid = game.estCoupValide(playerCoup); // Vérifier la validité du coup

                    if (isValid) {
                        game.appliquerCoup(playerCoup, senderJoueur, game.getPast(), game.getPresent(),
                                game.getFuture());
                        // Le tour a été changé dans appliquerCoup.
                        // Mettre à jour currentTurnPlayerId dans GameEngineServer
                        currentTurnPlayerId = game.getJoueurCourant().getId();
                        System.out.println(
                                "GameEngineServer : Coup valide. Nouveau tour pour le joueur : " + currentTurnPlayerId);
                    } else {
                        System.out.println("GameEngineServer : Coup invalide du joueur " + msg.clientId
                                + ". Veuillez réessayer.");
                        sendMessageToClient(msg.clientId, Code.ADVERSAIRE.name() + ":" + "Coup invalide.");
                    }

                    // Envoyer le nouvel état du jeu à tous les clients
                    sendGameStateToAllClients();

                    // Vérifier si le jeu est terminé (pour les deux joueurs)
                    int winnerId = game.gameOver(game.getJoueur1());
                    if (winnerId == 0) {
                        winnerId = game.gameOver(game.getJoueur2());
                    }

                    if (winnerId != 0) {
                        String winnerMsg = Code.GAGNE.name() + ":" + winnerId; // Utiliser le code GAGNE pour informer les clients
                        if (winnerId == game.getJoueur1().getId()) {
                            winnerMsg += ":Félicitations " + game.getJoueur1().getNom() + " pour votre victoire !";
                        } else {
                            winnerMsg += ":Félicitations " + game.getJoueur2().getNom() + " pour votre victoire !";
                        }
                        sendStateToAllClients(winnerMsg);
                        System.out.println("GameEngineServer : Jeu terminé ! Gagnant : " + winnerId);
                        // Ajouter une logique pour arrêter le serveur ou réinitialiser le jeu si nécessaire
                        break;
                    }
                } catch (IllegalArgumentException e) {
                    System.err.println("GameEngineServer : Erreur d'analyse ou valeur invalide : " + e.getMessage());
                    e.printStackTrace();
                } catch (Exception e) { // Capturer les erreurs dues à valueOf ou parseInt
                    System.err.println("GameEngineServer : Erreur lors du traitement du message : " + e.getMessage());
                    e.printStackTrace(); // Afficher la trace pour le débogage
                    // Envoyer une erreur au client si nécessaire
                    // sendMessageToClient(msg.clientId, Code.ADVERSAIRE.name() + ":" + "Erreur serveur : " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("GameEngineServer : Erreur dans la boucle de jeu : " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    // Méthode utilitaire pour envoyer l'état du jeu à tous les clients
    private void sendGameStateToAllClients() {
        String gameStateString = game.getGameStateAsString();
        String messageToSend = Code.ETAT.name() + ":" + gameStateString; // Ajouter le code ETAT au début
        sendStateToAllClients(messageToSend);
        System.out.println("GameEngineServer : Nouvel état du jeu envoyé : " + messageToSend);
    }

    // Envoyer un message à tous les clients actifs
    private void sendStateToAllClients(String message) {
        for (Integer clientId : connectedClientIds) { // Utiliser la liste des IDs des clients pour garantir l'ordre
            BlockingQueue<String> queue = outgoingClientQueues.get(clientId);
            if (queue != null) {
                try {
                    queue.put(message);
                } catch (InterruptedException e) {
                    System.err.println(
                            "GameEngineServer : Erreur lors de l'envoi du message au client " + clientId + " : " + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    // Envoyer un message spécifique à un client particulier
    private void sendMessageToClient(int clientId, String message) {
        BlockingQueue<String> clientQueue = outgoingClientQueues.get(clientId);
        if (clientQueue != null) {
            try {
                clientQueue.put(message);
            } catch (InterruptedException e) {
                System.err.println("GameEngineServer : Erreur lors de l'envoi du message au client " + clientId + " : " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }
}
