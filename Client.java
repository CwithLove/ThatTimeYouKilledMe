import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Socket socket = new Socket("localhost", 1234);

        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        Thread reception = new Thread(() -> {
            try {
                while (true) {
                    Object obj = in.readObject();
                    if (obj instanceof String) {
                        System.out.println("Serveur: " + obj);
                    }
                }
            } catch (Exception e) {
                System.out.println("Déconnecté du serveur.");
            }
        });
        reception.start();

        Scanner sc = new Scanner(System.in);
        while (true) {
            String ligne = sc.nextLine();
            out.writeObject(ligne);
            out.flush();
        }
    }
}