package Network;

import Modele.Coup;
import Modele.IAFields;
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
    private boolean calculatedIn0 = false;
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
    // private int currentPosX = -1;
    // private int currentPosY = -1;
    IAFields<Piece,String,String,Plateau.TypePlateau> AImove = null; 
    private IAminimax ia;

    public AIClient(String serverIpAddress, int difficuletAI) {
        this.serverIpAddress = serverIpAddress;
        this.gameInstance = new Jeu(); // Initialiser l'instance de jeu
        ia = new IAminimax(difficuletAI, gameInstance);
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
            disconnect();
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
                    //System.out.println(aiName + " - Server Raw: " + data); // Debug

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
                        if (this.gameInstance.getJoueurCourant().getId() == this.myPlayerId) {
                                System.out.println("BOT Adversaire ETAT:" + content);
                                onGameStateUpdate(this.gameInstance); // Mettre à jour l'état du jeu
                            }
                            break;
                        case GAGNE:
                        case PERDU:
                        case ADVERSAIRE:
                        case ACTION:
                        case COUP:
                        case REDOABLE:
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
        String cmd = null;
        Jeu clone = new Jeu(newGameState);
        // this.gameInstance a été mis à jour par GameStateParser avant l'appel de cette fonction.
        if (newGameState == null || newGameState.getJoueurCourant() == null) {
            System.out.println(aiName + " (ID: " + myPlayerId + "): État du jeu invalide reçu pour décision.");
            return;
        }

        //System.out.println("----- DEBUG MODE ------");
        if (newGameState.getJoueurCourant().getId() != myPlayerId) {
            System.out.println(aiName + " (ID: " + myPlayerId + "): Ce n'est pas mon tour, je ne fais rien.");
            return;
        }
        
        System.out.println(aiName + " (ID: " + myPlayerId + "): C'est mon tour ! Prise de décision...");
        try {
            
            Thread.sleep(500); // L'IA "réfléchit" pendant 0,5-2 secondes
            //L'ia joue un coup
            switch (newGameState.getEtape()) {
                case 0: // AI peut calculer le coup ici
                    calculatedIn0 = true;
                    try {
                        // IA choisit un coup
                        System.out.println(aiName + "Calculer a l'etape 0");
                        AImove = ia.coupIA(clone);
                    } catch (Exception e) {
                        System.err.println(aiName + ": Exception lors du calcul du coup IA: " + e.getMessage());
                    }
                    if (AImove == null) {
                        System.out.println("Erreur, le coup de l'IA est null");
                        return;
                    }
                    cmd = joueCoup(newGameState, 0);
                    System.out.println("Jai calcule a 0");
                    break;
                case 1:
                    cmd = joueCoup(newGameState, 1);
                    System.out.println("Jai calcule a 1");
                    break;
                case 2:
                    cmd = joueCoup(newGameState, 2);
                    System.out.println("Jai calcule a 2");
                    break;
                case 3:
                    // L'IA joue le coup de la phase 3
                    if (!calculatedIn0) {
                        System.out.println(aiName + "Calculer a l'etape 3");
                        try {
                            // IA choisit un coup
                            AImove = ia.coupIA(newGameState);
                        } catch (Exception e) {
                            System.err.println(aiName + ": Exception lors du calcul du coup IA: " + e.getMessage());
                        }
                        if (AImove == null) {
                            System.out.println(aiName + ": Erreur, le coup de l'IA est null a etape 3");
                            System.out.println("Erreur, le coup de l'IA est null");
                            return;
                        }
                    }
                    cmd = joueCoup(newGameState, 3);
                    System.out.println("Jai calcule a 3");
                    calculatedIn0 = false; // Réinitialiser le calcul
                    AImove = null; // Réinitialiser le coup IA après l'avoir joué
                    
                    break;
                default:
                    System.out.println("Etape inconnue");
            }
            if (cmd == null) {
                System.out.println(aiName + ": Erreur, le coup de l'IA est null vi cmd est null");
                return;
            }
            System.out.println(aiName + " (ID: " + myPlayerId + "): Envoi du coup: " + cmd);    
            sendPlayerAction(cmd);
            if (gameInstance.getEtape() == 3) {
                cmd = null;
            }
            

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(aiName + ": Thread interrompu pendant la réflexion.");
            return;
        }
        
        //makeDecisionAndPlay();
        
    }

    protected String joueCoup(Jeu jeu,int numCoup){
        if (AImove == null) {
            System.out.println("Erreur, le coup de l'IA est null");
            return null;
        }
        if (numCoup == 0){
            return 0 +":"+ null +":"+ jeu.getJoueurCourant().getProchainPlateau().name() + ":" + AImove.getPremier().getPosition().x + ":" + AImove.getPremier().getPosition().y;
        }
        else if (numCoup == 1 || numCoup == 2){      
            return IAtoMessage(jeu,numCoup);
                   
        } else if (numCoup == 3) {
            return 0 +":"+ AImove.getQuatrieme().name() +":"+ jeu.getPlateauCourant().plateauToString()+ ":" + 0 + ":" + 0;
        }else{
            System.out.println("Numero du coup invalide !");
        }
        return null;
    }

    private String IAtoMessage(Jeu jeu, int numCoup){
        String coup = null;
        int currentPosX, currentPosY;
        // if (currentPosX < 0 || currentPosY < 0){
            currentPosX = AImove.getPremier().getPosition().x;
            currentPosY = AImove.getPremier().getPosition().y;
        // }

        if (numCoup == 1){
            coup = AImove.getSecond();
        } else if (numCoup == 2){
            if ("JUMP".equals(AImove.getSecond()) || "CLONE".equals(AImove.getSecond())) {
                // Met à jour le plateau courant dans l'instance de jeu selon le type de coup
                if ("JUMP".equals(AImove.getSecond())) {
                    Plateau.TypePlateau nextPlateau = jumpPlateau(jeu.getPlateauCourant().getType());
                    if (nextPlateau != null) {
                        jeu.setPlateauCourant(nextPlateau);
                    }
                } else if ("CLONE".equals(AImove.getSecond())) {
                    Plateau.TypePlateau nextPlateau = clonePlateau(jeu.getPlateauCourant().getType());
                    if (nextPlateau != null) {
                        jeu.setPlateauCourant(nextPlateau);
                    }
                }
            }
            coup = AImove.getTroisieme();
        }

                    
        if (coup == null) {
            System.out.println("Erreur, le coup de l'IA est null");
            return null;
        }

                    
        switch (coup) {
            case "UP":
                System.out.println("Coup IA UP 1: " + AImove.getPremier().getPosition().x + " " + AImove.getPremier().getPosition().y);
                currentPosX -=1;
                AImove.getPremier().setPosition(new Point(currentPosX, currentPosY));
                System.out.println("Coup IA UP 2: " + AImove.getPremier().getPosition().x + " " + AImove.getPremier().getPosition().y);
                return 0 +":"+ null +":"+ jeu.getPlateauCourant().getType()+ ":" + currentPosX + ":" + currentPosY;
                
            case "DOWN":
                System.out.println("Coup IA DOWN: " + AImove.getPremier().getPosition().x + " " + AImove.getPremier().getPosition().y); 
                currentPosX +=1;
                AImove.getPremier().setPosition(new Point(currentPosX, currentPosY));
                System.out.println("Coup IA DOWN: " + AImove.getPremier().getPosition().x + " " + AImove.getPremier().getPosition().y); 
                return 0 +":"+ null +":"+ jeu.getPlateauCourant().getType() + ":" + currentPosX + ":" + currentPosY;
                
            case "LEFT":
                System.out.println("Coup IA LEFT: " + AImove.getPremier().getPosition().x + " " + AImove.getPremier().getPosition().y);
                currentPosY -=1;
                AImove.getPremier().setPosition(new Point(currentPosX, currentPosY));
                System.out.println("Coup IA LEFT: " + AImove.getPremier().getPosition().x + " " + AImove.getPremier().getPosition().y);
                return 0 +":"+ null +":"+ jeu.getPlateauCourant().getType() + ":" + currentPosX + ":" + currentPosY;
                
            case "RIGHT":
                System.out.println("Coup IA RIGHT: " + AImove.getPremier().getPosition().x + " " + AImove.getPremier().getPosition().y);
                currentPosY +=1;
                AImove.getPremier().setPosition(new Point(currentPosX, currentPosY));
                System.out.println("Coup IA RIGHT: " + AImove.getPremier().getPosition().x + " " + AImove.getPremier().getPosition().y);
                return 0 +":"+ null +":"+ jeu.getPlateauCourant().getType() + ":" + currentPosX + ":" + currentPosY;

            case "JUMP":
                return 0 +":"+ null +":"+ jumpPlateau(jeu.getPlateauCourant().getType()) + ":" + currentPosX + ":" + currentPosY;
                
            case "CLONE":
                return 0 +":"+ null +":"+ clonePlateau(jeu.getPlateauCourant().getType()) + ":" + currentPosX + ":" + currentPosY;
                
            default:
                System.out.println("Erreur, le coup invalide !");
                return null;

        }

    }

    private Plateau.TypePlateau jumpPlateau(Plateau.TypePlateau plateau){
        if (plateau == null){
            System.out.println("Plateau null");
            return null;
        }

        if (plateau.equals(Plateau.TypePlateau.PAST)){
            return Plateau.TypePlateau.PRESENT;
        }
        else if (plateau.equals(Plateau.TypePlateau.PRESENT)){
            return Plateau.TypePlateau.FUTURE;
        }
        else if (plateau.equals(Plateau.TypePlateau.FUTURE)){
            System.out.println("JUMP DU FUTUR IMPOSSIBLE");
            return null;
        }
        return null;
    }

    private Plateau.TypePlateau clonePlateau(Plateau.TypePlateau plateau){
        if (plateau == null){
            System.out.println("Plateau null");
            return null;
        }

        if (plateau.equals(Plateau.TypePlateau.FUTURE)){
            return Plateau.TypePlateau.PRESENT;
        }
        else if (plateau.equals(Plateau.TypePlateau.PRESENT)){
            return Plateau.TypePlateau.PAST;
        }
        else if (plateau.equals(Plateau.TypePlateau.PAST)){
            System.out.println("CLONE DU PASSE IMPOSSIBLE");
            return null;
        }
        return null;
    }

    private void makeDecisionAndPlay() {
        if (gameInstance == null) return;

        Joueur aiPlayer = (gameInstance.getJoueur1().getId() == myPlayerId) ? gameInstance.getJoueur1() : gameInstance.getJoueur2();

        // La stratégie de l'IA est très basique
        List<Piece> aiPiecesPresent = findMyPiecesOnBoard(gameInstance.getPresent(), aiPlayer);
        List<Piece> aiPiecesPast = findMyPiecesOnBoard(gameInstance.getPast(), aiPlayer);
        List<Piece> aiPiecesFuture = findMyPiecesOnBoard(gameInstance.getFuture(), aiPlayer);

        // 1. Essayer un CLONE depuis le PRESENT
        if (aiPlayer.getNbClones() > 0 && !aiPiecesPresent.isEmpty()) {
            for (Piece p : aiPiecesPresent) {
               // Devrait choisir aléatoirement ou par priorité
               // Vérification simple : si la case cible sur PAST ne contient pas de pièce de l'IA
                Piece destPiecePast = gameInstance.getPast().getPiece(p.getPosition().x, p.getPosition().y);
                if (destPiecePast == null || destPiecePast.getOwner().getId() != myPlayerId) {
                    sendPlayerAction(Coup.TypeCoup.CLONE.name() + ":" + Plateau.TypePlateau.PRESENT.name() + ":" + p.getPosition().x + ":" + p.getPosition().y);
                    return;
                }
            }
        }

        // 2. Essayer un JUMP depuis le PAST
        if (!aiPiecesPast.isEmpty()) {
            for (Piece p : aiPiecesPast) {
                Piece destPiecePresent = gameInstance.getPresent().getPiece(p.getPosition().x, p.getPosition().y);
                if (destPiecePresent == null || destPiecePresent.getOwner().getId() != myPlayerId) {
                    sendPlayerAction(Coup.TypeCoup.JUMP.name() + ":" + Plateau.TypePlateau.PAST.name() + ":" + p.getPosition().x + ":" + p.getPosition().y);
                    return;
                }
            }
        }
        
        // 3. Essayer un CLONE depuis le FUTURE
        if (aiPlayer.getNbClones() > 0 && !aiPiecesFuture.isEmpty()) {
            for (Piece p : aiPiecesFuture) {
                Piece destPiecePresent = gameInstance.getPresent().getPiece(p.getPosition().x, p.getPosition().y);
                 if (destPiecePresent == null || destPiecePresent.getOwner().getId() != myPlayerId) {
                    sendPlayerAction(Coup.TypeCoup.CLONE.name() + ":" + Plateau.TypePlateau.FUTURE.name() + ":" + p.getPosition().x + ":" + p.getPosition().y);
                    return;
                }
            }
        }

       // 4. Essayer un MOVE aléatoire
        List<Piece> allMyPieces = new ArrayList<>();
        allMyPieces.addAll(aiPiecesPresent);
        allMyPieces.addAll(aiPiecesPast);
        allMyPieces.addAll(aiPiecesFuture);

        if (!allMyPieces.isEmpty()) {
            java.util.Collections.shuffle(allMyPieces); // Mélanger pour avoir plus d'aléatoire
            for(Piece pieceToMove : allMyPieces){
                Plateau.TypePlateau plateauOfPiece = getPlateauTypeOfPiece(pieceToMove, gameInstance);
                if (plateauOfPiece != null) {
                    Point[] directions = {new Point(0, 1), new Point(0, -1), new Point(1, 0), new Point(-1, 0)};
                    java.util.Collections.shuffle(java.util.Arrays.asList(directions));

                    for(Point dir : directions){
                       // L'IA peut vérifier préalablement si le mouvement sort de l'échiquier
                        int destX = pieceToMove.getPosition().x + dir.x;
                        int destY = pieceToMove.getPosition().y + dir.y;
                        int boardSize = 4; //La taille de plateau est fixe
                        if (destX >= 0 && destX < boardSize && destY >= 0 && destY < boardSize) {
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
        // À l'avenir, si aucun mouvement n'est possible, l'IA pourrait devoir envoyer une commande "SKIP" ou le serveur devra gérer lui-même le timeout.
        // Actuellement, ne rien envoyer fait que le serveur reste en attente.
    }

    private List<Piece> findMyPiecesOnBoard(Plateau board, Joueur myself) {
        List<Piece> myPieces = new ArrayList<>();
        if (board == null || myself == null) return myPieces;
        int boardSize = 4; //La taille du plateau est fixe
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
        if (pos == null) return null; // Ajouter une vérification de null pour la position

        Plateau past = currentGame.getPast();
        Plateau present = currentGame.getPresent();
        Plateau future = currentGame.getFuture();

        if (past != null && past.getPiece(pos.x, pos.y) == piece) return Plateau.TypePlateau.PAST;
        if (present != null && present.getPiece(pos.x, pos.y) == piece) return Plateau.TypePlateau.PRESENT;
        if (future != null && future.getPiece(pos.x, pos.y) == piece) return Plateau.TypePlateau.FUTURE;
        
        // Repli si la pièce n'est pas trouvée par comparaison de références (possiblement due à une copie)
        // Vérification basée sur le propriétaire et la position
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
            disconnect();
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

    public void setAIMove(IAFields<Piece,String,String,Plateau.TypePlateau> AImove){
        this.AImove = AImove;
    }
    
    /**
     * Permet de définir un port personnalisé pour la connexion au serveur
     * @param port Le numéro de port à utiliser
     */
    public void setServerPort(int port) {
        if (!isRunning) {
            this.serverPort = port;
            System.out.println(aiName + ": Port du serveur défini à " + port);
        } else {
            System.out.println(aiName + ": Impossible de changer le port une fois connecté");
        }
    }
    
    /**
     * Retourne le port utilisé pour la connexion au serveur
     * @return Le numéro de port
     */
    public int getServerPort() {
        return this.serverPort;
    }
}