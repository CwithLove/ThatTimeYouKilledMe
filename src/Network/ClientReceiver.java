package Network;

import java.io.*;
import java.net.SocketException;
import java.util.concurrent.*;

public class ClientReceiver implements Runnable {
    private ObjectInputStream in;
    private BlockingQueue<Message> fileEntrante;
    private int clientId;
    private volatile boolean running = true;
    private GameServerManager serverManager;

    public ClientReceiver(ObjectInputStream in, BlockingQueue<Message> fileEntrante, int clientId) {
        this.in = in;
        this.fileEntrante = fileEntrante;
        this.clientId = clientId;
        this.serverManager = null;
    }
    
    public ClientReceiver(ObjectInputStream in, BlockingQueue<Message> fileEntrante, int clientId, GameServerManager serverManager) {
        this.in = in;
        this.fileEntrante = fileEntrante;
        this.clientId = clientId;
        this.serverManager = serverManager;
    }

    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Object obj = in.readObject();
                    if (obj instanceof String) {
                        String messageStr = (String) obj;
                        fileEntrante.put(new Message(clientId, messageStr));
                        // System.out.println("ClientReceiver (" + clientId + "): Reçu -> " + messageStr);
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
            notifyDisconnection();
        } catch (EOFException e) {
            // traitement de femeture de port
            System.out.println("ClientReceiver: Client " + clientId + " a fermé la connexion");
            notifyDisconnection();
        } catch (IOException e) {
            System.err.println("ClientReceiver: Erreur de lecture du client " + clientId + " - " + e.getMessage());
            notifyDisconnection();
        } finally {
            System.out.println("ClientReceiver (" + clientId + "): Arrêt du thread de réception");
        }
    }
    
    private void notifyDisconnection() {
        if (serverManager != null) {
            serverManager.handleClientDisconnection(clientId);
        }
    }
    
    public void stop() {
        running = false;
    }
}
