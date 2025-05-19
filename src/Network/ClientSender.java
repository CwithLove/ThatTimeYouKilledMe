package Network;

import java.io.*;
import java.util.concurrent.*;

public class ClientSender implements Runnable {
    private ObjectOutputStream out;
    private BlockingQueue<String> fileSortante;
    private volatile boolean running = true;
    private final int clientId; // Ajouter l'id de client pour consulter le log

    public ClientSender(ObjectOutputStream out, BlockingQueue<String> fileSortante) {
        this(out, fileSortante, -1); // -1 : id non connu
    }
    
    public ClientSender(ObjectOutputStream out, BlockingQueue<String> fileSortante, int clientId) {
        this.out = out;
        this.fileSortante = fileSortante;
        this.clientId = clientId;
    }

    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    // Utiliser une version avec timeout de take,
                    // de cette façon on peut vérifier régulièrement le drapeau running
                    String msg = fileSortante.poll(500, TimeUnit.MILLISECONDS);
                    if (msg != null) {
                        out.writeObject(msg);
                        out.flush();
                        // System.out.println("ClientSender (" + clientId + "): Envoyé -> " + msg);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("ClientSender (" + clientId + "): Thread interrompu");
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("ClientSender (" + clientId + "): Erreur d'envoi - " + e.getMessage());
        } finally {
            System.out.println("ClientSender (" + clientId + "): Arrêt du thread d'envoi");
        }
    }
    
    public void stop() {
        running = false;
    }
}
