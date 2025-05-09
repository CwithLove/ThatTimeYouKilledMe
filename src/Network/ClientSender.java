package Network;

import java.io.*;
import java.util.concurrent.*;

public class ClientSender implements Runnable {
    private ObjectOutputStream out;
    private BlockingQueue<String> fileSortante;

    public ClientSender(ObjectOutputStream out, BlockingQueue<String> fileSortante) {
        this.out = out;
        this.fileSortante = fileSortante;
    }

    public void run() {
        try {
            while (true) {
                String msg = fileSortante.take();
                out.writeObject(msg);
                out.flush();
            }
        } catch (Exception e) {
            System.out.println("Erreur d'envoi au client : " + e.getMessage());
        }
    }
}
