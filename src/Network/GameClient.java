package Network;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;
import java.awt.Point;

import Modele.Joueur;
import Modele.Piece;
import Modele.Plateau;

public class Client {
    private String ip;
    private GameState etat;

    public Client() {
        ip = "localhost";
    }
    public Client(String ip) {
        this.ip = ip;
    }
    public void demarrer() throws IOException, ClassNotFoundException {
        Socket socket = new Socket(ip, 1234);

        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

        Thread reception = new Thread(() -> {       // thread pour reçevoir les messages du serveur
            try {
                while (true) {
                    Object obj = in.readObject();
                    if (obj instanceof String) {
                        String data = (String) obj;
                        String[] lines = data.split("\n");  // On a le message ligne par ligne
                        System.out.println("Serveur: " + obj);  // Affichage brut dans le terminal
                        Code code = Code.valueOf(lines[0]); /* CODE CONTIENT TOUT POUR SAVOIR QUOI DEMANDER AU JOUEUR */
                        if (code == Code.ETAT) {
                            etat = fromGamePlayString(lines); // On traduit le String en Etat
                            etat.afficherEtat(); 
                            /* ETAT CONTIENT TOUTES LES INFORMATIONS POUR AFFICHER L'ETAT DU JEU !!! */
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
                System.out.println("Déconnecté du serveur.");
            }
        });
        reception.start();      // Démarrage du thread

        Scanner sc = new Scanner(System.in);
        while (true) {
            String ligne = sc.nextLine();           // Ecriture d'un message au serveur
            out.writeObject(ligne);
            out.flush();
        }
    }

    /* Traduction d'un message contenant l'état en jeu sous forme de
     * tableau chaîne de caractères en un objet de classe Etat
     */
    private static GameState fromGamePlayString(String[] lines) {
    
        // Récupération des types de plateau
        String[] infoJ1 = lines[1].split(" ");
        String type1Str = infoJ1[0];
        String [] infoJ2 = lines[2].split(" ");
        String type2Str = infoJ2[0];
    
        Plateau.TypePlateau type1 = Plateau.TypePlateau.valueOf(type1Str);
        Plateau.TypePlateau type2 = Plateau.TypePlateau.valueOf(type2Str);

        String clones1 = infoJ1[1];
        String clones2 = infoJ2[1];

        int nbClone1 = 0;
        for (int i = 0; i < clones1.length(); i++) {
            if (clones1.charAt(i) == '*') {
                nbClone1++;
            }
        }

        int nbClone2 = 0;
        for (int i = 0; i < clones2.length(); i++) {
            if (clones2.charAt(i) == '*') {
                nbClone2++;
            }
        }

        Joueur joueur1 = new Joueur("BLANC", 1, nbClone1, type1);
        Joueur joueur2 = new Joueur("NOIR", 2, nbClone2, type2);
    
        // Création des 3 plateaux
        int taille = 4;
        Plateau past = new Plateau(Plateau.TypePlateau.PAST, joueur1, joueur2);
        Plateau present = new Plateau(Plateau.TypePlateau.PRESENT, joueur1, joueur2);
        Plateau future = new Plateau(Plateau.TypePlateau.FUTURE, joueur1, joueur2);
    
        // Nettoyage des grilles (elles sont préremplies à la création)
        for (int i = 0; i < taille; i++) {
            for (int j = 0; j < taille; j++) {
                past.removePiece(i, j);
                present.removePiece(i, j);
                future.removePiece(i, j);
            }
        }
    
        // Parsing des grilles
        for (int i = 0; i < taille; i++) { 
            //System.out.println(i);
            for (int j = 0; j < taille; j++) {
                //System.out.println(j);
                past.setPiece(pieceFromCode(lines[i + 3].substring(j, j+1), joueur1, joueur2, i, j), i, j);
                present.setPiece(pieceFromCode(lines[i + taille + 4].substring(j, j+1), joueur1, joueur2, i, j), i, j);
                future.setPiece(pieceFromCode(lines[i + 2* taille + 5].substring(j, j+1), joueur1, joueur2, i, j), i, j);
            }
        }

        Joueur joueurCourant = null;
        Piece pieceCourante = null;
        int jCourant = 0;
        if (lines.length > 3 * taille + 6) {
            jCourant = Integer.parseInt(lines[3 * taille + 6]);

            if (jCourant == 1)
                joueurCourant = joueur1;
            else
                joueurCourant = joueur2;

            if (lines.length > 3 * taille + 7) {
                int x = Integer.parseInt(lines[lines.length-2]);
                int y = Integer.parseInt(lines[lines.length-1]);
    
                pieceCourante = new Piece(joueurCourant, new Point(x, y));
            }
        }
    
        // Création du jeu
        GameState etat = new GameState();
        etat.joueur1 = joueur1;
        etat.joueur2 = joueur2;
        etat.joueurCourant = joueurCourant;
        etat.past = past;
        etat.present = present;
        etat.future = future;
        etat.pieceCourante = pieceCourante;
    
        return etat;
    }
    
    // Méthode utilitaire pour créer une pièce
    private static Piece pieceFromCode(String code, Joueur j1, Joueur j2, int x, int y) {
        if (code.equals("B")) return new Piece(j1, new Point(x, y));
        if (code.equals("N")) return new Piece(j2, new Point(x, y));
        return null;
    }
    
}
