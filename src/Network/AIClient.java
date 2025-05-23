package Network;

import Modele.IAFields;
import Modele.Jeu;
import Modele.Piece;
import Modele.Plateau;
import java.awt.Point;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
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
    private int currentPosX = -1;
    private int currentPosY = -1;
    IAFields<Piece,String,String,Plateau.TypePlateau> AImove = null; 
    private IAminimax ia = new IAminimax(5,gameInstance);

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
                        case PIECE:
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
        String command = null;
        try {
            
            Thread.sleep(500); // L'IA "réfléchit" pendant 0,5-2 secondes
            //L'ia joue un coup
            switch (newGameState.getEtape()) {
                case 0: // AI peut calculer le coup ici
                    calculatedIn0 = true;
                    try {
                        // IA choisit un coup
                        System.out.println(aiName + "Calculer a l'etape 0");
                        AImove = ia.coupIA(newGameState);
                        System.out.print("Coup IA: " + AImove.getPremier().getPosition().x + " " + AImove.getPremier().getPosition().y);
                        System.out.println(" " + AImove);
                    } catch (Exception e) {
                        System.err.println(aiName + ": Exception lors du calcul du coup IA: " + e.getMessage());
                    }
                    if (AImove == null) {
                        System.out.println("Erreur, le coup de l'IA est null");
                        return;
                    }
                    command = joueCoup(newGameState, 0);
                    System.out.print("Coup IA: " + AImove.getPremier().getPosition().x + " " + AImove.getPremier().getPosition().y);
                    System.out.println(" " + AImove);
                    break;
                case 1:
                    command = joueCoup(newGameState, 1);
                    break;
                case 2:
                    command = joueCoup(newGameState, 2);
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
                    command = joueCoup(newGameState, 3);
                    calculatedIn0 = false; // Réinitialiser le calcul
                    AImove = null; // Réinitialiser le coup IA après l'avoir joué
                    
                    break;
                default:
                    System.out.println("Etape inconnue");
            }
            sendPlayerAction(command);
            command = null; // Réinitialiser la commande après l'envoi

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(aiName + ": Thread interrompu pendant la réflexion.");
            return;
        }
        
    }

    protected String joueCoup(Jeu jeu, int numCoup){
        if (AImove == null) {
            System.out.println("Erreur, le coup de l'IA est null");
            return null;
        }
        if (numCoup == 0){
            return 0 + ":"+ null +":"+ jeu.getJoueurCourant().getProchainPlateau().name() + ":" + AImove.getPremier().getPosition().x + ":" + AImove.getPremier().getPosition().y;
       }
        else if (numCoup == 1 || numCoup == 2){
            return IAtoMessage(jeu,numCoup);
        } else if (numCoup == 3) {
            return 0 +":"+ AImove.getQuatrieme().name() +":"+ jeu.getPlateauCourant().plateauToString()+ ":" + 0 + ":" + 0;
        }else{
            System.out.println("Numero du coup invalide !");
            return null;
        }
    }

    private String IAtoMessage(Jeu jeu, int numCoup){
        String coup = null;
        if (currentPosX < 0 || currentPosY < 0){
            currentPosX = AImove.getPremier().getPosition().x;
            currentPosY = AImove.getPremier().getPosition().y;
        }
        System.out.println("Position initiale: " + currentPosX + " " + currentPosY);
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
                currentPosX -=1;
                AImove.getPremier().setPosition(new Point(currentPosX, currentPosY));
                System.out.println("UP: " + currentPosX + " " + currentPosY);
                return 0 +":"+ null +":"+ jeu.getPlateauCourant().getType()+ ":" + currentPosX + ":" + currentPosY;
            case "DOWN":
                currentPosX +=1;

                System.out.println("DOWN: " + currentPosX + " " + currentPosY);
                AImove.getPremier().setPosition(new Point(currentPosX, currentPosY));
                return 0 +":"+ null +":"+ jeu.getPlateauCourant().getType() + ":" + currentPosX + ":" + currentPosY;
            case "LEFT":
                currentPosY -=1;
                System.out.println("LEFT: " + currentPosX + " " + currentPosY);
                AImove.getPremier().setPosition(new Point(currentPosX, currentPosY));
                return 0 +":"+ null +":"+ jeu.getPlateauCourant().getType() + ":" + currentPosX + ":" + currentPosY;
            case "RIGHT":
                currentPosY +=1;
                System.out.println("RIGHT: " + currentPosX + " " + currentPosY);
                AImove.getPremier().setPosition(new Point(currentPosX, currentPosY));
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
}