package Network;

import java.io.*;
import java.net.SocketException;
import java.util.concurrent.*;

public class ClientReceiver implements Runnable {
    private ObjectInputStream in;
    private BlockingQueue<Message> fileEntrante;
    private int clientId;
    private volatile boolean running = true;

    public ClientReceiver(ObjectInputStream in, BlockingQueue<Message> fileEntrante, int clientId) {
        this.in = in;
        this.fileEntrante = fileEntrante;
        this.clientId = clientId;
    }

    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    Object obj = in.readObject();
                    if (obj instanceof String) {
                        String messageStr = (String) obj;
                        fileEntrante.put(new Message(clientId, messageStr));
                        // 可以在这里添加调试日志
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
            // 正常断开连接时的处理
            System.out.println("ClientReceiver: Client " + clientId + " déconnecté: " + e.getMessage());
        } catch (EOFException e) {
            // 对端关闭流时的处理
            System.out.println("ClientReceiver: Client " + clientId + " a fermé la connexion");
        } catch (IOException e) {
            System.err.println("ClientReceiver: Erreur de lecture du client " + clientId + " - " + e.getMessage());
        } finally {
            System.out.println("ClientReceiver (" + clientId + "): Arrêt du thread de réception");
        }
    }
    
    public void stop() {
        running = false;
    }
}
