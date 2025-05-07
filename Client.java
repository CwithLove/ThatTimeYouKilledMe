import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.Point;

public class Client {
    private String ip;

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

        Thread reception = new Thread(() -> {
            try {
                while (true) {
                    Object obj = in.readObject();
                    //majAffichage();
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

    public static Etat fromGamePlayString(String data) {
        String[] lines = data.split("\n");
    
        // Récupération des types de plateau
        String[] infoJ1 = lines[0].split(" ");
        String type1Str = infoJ1[0];
        String [] infoJ2 = lines[1].split(" ");
        String type2Str = infoJ2[1];
    
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
            String line = lines[i + 2];
    
            for (int j = 0; j < taille; j++) {
                String code = line.substring(j * 3, j * 3 + 3);
                past.setPiece(pieceFromCode(code, joueur1, joueur2, i, j), i, j);
            }
    
            for (int j = 0; j < taille; j++) {
                int offset = (taille + 1) * 3 + j * 3;
                String code = line.substring(offset, offset + 3);
                present.setPiece(pieceFromCode(code, joueur1, joueur2, i, j), i, j);
            }
    
            for (int j = 0; j < taille; j++) {
                int offset = (taille * 2 + 2) * 3 + j * 3;
                String code = line.substring(offset, offset + 3);
                future.setPiece(pieceFromCode(code, joueur1, joueur2, i, j), i, j);
            }
        }

        Joueur joueurCourant = null;
        Piece pieceCourante = null;
        int jCourant = 0;
        if (lines.length > taille + 2) {
            jCourant = Integer.parseInt(lines[taille+ 2]);

            if (jCourant == 1)
                joueurCourant = joueur1;
            else
                joueurCourant = joueur2;

            if (lines.length > taille + 3) {
                int x = Integer.parseInt(lines[lines.length-2]);
                int y = Integer.parseInt(lines[lines.length-1]);
    
                pieceCourante = new Piece(joueurCourant, new Point(x, y));
            }
        }
    
        // Création du jeu
        Etat etat = new Etat();
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