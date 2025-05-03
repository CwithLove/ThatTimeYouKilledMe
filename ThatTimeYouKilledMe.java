import java.io.IOException;
import java.util.Scanner;

public class ThatTimeYouKilledMe {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("<--=== MENU ===-->");
        System.out.println("1 - Nouvelle partie");
        System.out.println("2 - Rejoindre partie");

        int choix = sc.nextInt();

        switch (choix) {
            case 1:
                Thread partie = new Thread(() -> {
                    try {
                        Serveur.main(args);
                    }
                    catch (IOException e) {
                        System.out.println(e.getMessage());
                    }
                });
                partie.start();
                try {
                    Client client = new Client();
                    client.demarrer();
                }
                catch (IOException | ClassNotFoundException e) {
                    System.out.println(e.getMessage());
                }
                break;
            case 2:
                System.out.println("Veuillez entrer l'adresse ip de l'hôte auquel vous souhaitez vous connecter :");
                sc.nextLine();
                String ip = sc.nextLine();
                try {
                    //System.out.println("ip: " + ip);
                    Client client = new Client(ip);
                    client.demarrer();
                }
                catch (IOException | ClassNotFoundException e) {
                    System.out.println(e.getMessage());
                }
            default:
                break;
        }
        sc.close();
        //Jeu jeu = new Jeu();
        //jeu.demarrer();
    }
}
