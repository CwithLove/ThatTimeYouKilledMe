package Network;

import java.io.*;
import java.util.concurrent.*;

public class ClientSender implements Runnable {
    private ObjectOutputStream out;
    private BlockingQueue<String> fileSortante;
    private volatile boolean running = true;
    private final int clientId; // 添加客户端ID以便更好的日志记录

    public ClientSender(ObjectOutputStream out, BlockingQueue<String> fileSortante) {
        this(out, fileSortante, -1); // 使用-1表示未知ID
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
                    // 使用超时版本的take，这样可以定期检查running标志
                    String msg = fileSortante.poll(500, TimeUnit.MILLISECONDS);
                    if (msg != null) {
                        out.writeObject(msg);
                        out.flush();
                        // 可以在这里添加调试日志
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
