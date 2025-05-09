package Network;

import java.io.*;
import java.util.concurrent.*;

public class ClientReceiver implements Runnable {
    private ObjectInputStream in;
    private BlockingQueue<Message> fileEntrante;
    private int clientId;

    public ClientReceiver(ObjectInputStream in, BlockingQueue<Message> fileEntrante, int clientId) {
        this.in = in;
        this.fileEntrante = fileEntrante;
        this.clientId = clientId;
    }

    public void run() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (obj instanceof String) {
                    fileEntrante.put(new Message(clientId, (String) obj));
                }
            }
        } catch (Exception e) {
            System.out.println("Client " + clientId + " déconnecté.");
        }
    }
}
