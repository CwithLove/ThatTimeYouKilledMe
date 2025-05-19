package Network;

import java.io.*;
import java.net.SocketException;
import java.util.concurrent.*;
import java.util.Map;
import SceneManager.HostingScene;

public class ClientReceiver implements Runnable {
    private ObjectInputStream in;
    private BlockingQueue<Message> fileEntrante;
    private int clientId;
    private volatile boolean running = true;
    private Map<Integer, String> playerNames;
    private HostingScene hostingSceneCallback;


    public ClientReceiver(ObjectInputStream in, BlockingQueue<Message> fileEntrante, int clientId,
                          Map<Integer, String> playerNames, HostingScene hostingSceneCallback) {
        this.in = in;
        this.fileEntrante = fileEntrante;
        this.clientId = clientId;
        this.playerNames = playerNames;
        this.hostingSceneCallback = hostingSceneCallback;
    }


    public ClientReceiver(ObjectInputStream in, BlockingQueue<Message> fileEntrante, int clientId) {
        this(in, fileEntrante, clientId, null, null);
    }

    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Object obj = in.readObject();
                    if (obj instanceof String) {
                        String messageStr = (String) obj;

                        if (messageStr.startsWith("NAME:")) {
                            handleNameMessage(messageStr);
                        } else {

                            fileEntrante.put(new Message(clientId, messageStr));
                            // System.out.println("ClientReceiver (" + clientId + "): Reçu -> " + messageStr);
                        }
                    }
                } catch (ClassNotFoundException e) {
                    System.err.println("ClientReceiver (" + clientId + "): Type d'objet non reconnu - " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("ClientReceiver (" + clientId + "): Thread interrompu");
                    break;
                }
            }
        } catch (SocketException e) {
            // traitement de deconexion normale
            System.out.println("ClientReceiver: Client " + clientId + " déconnecté: " + e.getMessage());
        } catch (EOFException e) {
            // traitement de femeture de port
            System.out.println("ClientReceiver: Client " + clientId + " a fermé la connexion");
        } catch (IOException e) {
            System.err.println("ClientReceiver: Erreur de lecture du client " + clientId + " - " + e.getMessage());
        } finally {
            System.out.println("ClientReceiver (" + clientId + "): Arrêt du thread de réception");
        }
    }


    private void handleNameMessage(String message) {
        try {
            String playerName = message.substring(5);


            if (playerNames != null) {
                playerNames.put(clientId, playerName);
                System.out.println("ClientReceiver: Nom du joueur " + clientId + " défini: " + playerName);
            }

            if (clientId == 2 && hostingSceneCallback != null) {
                System.out.println("ClientReceiver: Notification à HostingScene du nom du joueur 2: " + playerName);
                hostingSceneCallback.onPlayerTwoNameReceived(playerName);
            }
        } catch (Exception e) {
            System.err.println("ClientReceiver: Erreur lors du traitement du message NAME: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
    }
}