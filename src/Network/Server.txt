package Network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import Modele.Jeu;

public class Server {
    private static final int PORT = 1234;
    private static final BlockingQueue<Message> fileEntrante = new LinkedBlockingQueue<>();
    private static final Map<Integer, BlockingQueue<String>> filesSortantes = new ConcurrentHashMap<>();
    private static final List<Integer> ordreJoueurs = new ArrayList<>();
    private static int compteurClients = 0;

    public static void main(String[] args) throws IOException {
        ServerSocket serveurSocket = new ServerSocket(PORT);
        System.out.println("Serveur démarré sur le port " + PORT);

        Thread moteurGameEngine = null;

        while (compteurClients < 2) {
            Socket socket = serveurSocket.accept();
            compteurClients++;
            int clientId = compteurClients;

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            BlockingQueue<String> fileSortante = new LinkedBlockingQueue<>();
            filesSortantes.put(clientId, fileSortante);
            ordreJoueurs.add(clientId);

            new Thread(new ClientReceiver(in, fileEntrante, clientId)).start();
            new Thread(new ClientSender(out, fileSortante)).start();

            System.out.println("Client " + clientId + " connecté.");
        }

        moteurGameEngine = new Thread(new GameEngineServer(fileEntrante, filesSortantes, ordreJoueurs));
        moteurGameEngine.start();

        while (true) {
            
        }
    }
}
