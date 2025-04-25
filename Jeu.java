import java.awt.Point;
import java.util.Scanner;

public class Jeu {
    final static int TAILLE = 4;
    private Plateau past; // plateau past
    private Plateau present; // plateau present
    private Plateau future; // plateau future
    private Joueur joueur1; // joueur 1
    private Joueur joueur2; // joueur 2
    private Joueur joueurCourant; // nombre de tours
    private Piece pieceCourante; // piece courante
    Scanner sc = new Scanner(System.in);
    // les methodes a completer

    public void demarrer() {
        // Initialiser les joueurs
        joueur1 = new Joueur("Blanc", 1, 4, Plateau.TypePlateau.PAST);
        joueur2 = new Joueur("Noir", 2, 4, Plateau.TypePlateau.FUTURE);

        // Initialiser les plateaux
        past = new Plateau(Plateau.TypePlateau.PAST, joueur1, joueur2); 
        present = new Plateau(Plateau.TypePlateau.PRESENT, joueur1, joueur2);
        future = new Plateau(Plateau.TypePlateau.FUTURE, joueur1, joueur2);

        // Afficher le plateau initial
        System.out.println("Plateau initial :");
        printGamePlay();

        // Boucle de jeu
        Plateau plateauCourant = null;
        pieceCourante = null;
        joueurCourant = joueur2;
        do { 
            if (joueurCourant.equals(joueur1)) {
                joueurCourant = joueur2;
            } else if (joueurCourant.equals(joueur2)) {
                joueurCourant = joueur1;
            }

            // Mettre a jour le plateau courant
            switch (joueurCourant.getProchainPlateau()) {
                case PAST:
                    plateauCourant = past;
                    break;
                case PRESENT:
                    plateauCourant = present;
                    break;
                case FUTURE:
                    plateauCourant = future;
                    break;
            }

            // Choisir la piece a deplacer
            try {
                System.out.print("Veuillez entrez la piece que vous voulez deplacer (ligne colonne) : ");
                int lig, col;
                do {
                    lig = sc.nextInt();
                    col = sc.nextInt();
                    pieceCourante = plateauCourant.getPiece(lig, col);
                    if (pieceCourante == null||!pieceCourante.getOwner().equals(joueurCourant)) {
                        System.out.println("Piece invalide ou non possédée par le joueur courant. Veuillez réessayer : ");
                    }
                } while (pieceCourante == null|| !pieceCourante.getOwner().equals(joueurCourant));
            } catch (Exception e) {
                System.out.println("Erreur : " + e.getMessage());
                sc.nextLine(); // Clear the scanner buffer
                continue; // Re-demander l'entrée
            }
            
            // Chaque tour le joueur doit faire 2 coups
            for (int i = 0; i < 2; i++) {
                Coup coup;
                do { 
                    coup = joueurCourant.choisirCoup(plateauCourant, pieceCourante);
                } while (estCoupValide(coup) == false);
                appliquerCoup(coup);
            }

            // Prochain plateau
            System.out.print("Veuillez entrer le prochain plateau (PAST, PRESENT, FUTURE) : ");
            Plateau.TypePlateau prochainPlateau;
            do {
                try {
                    String input = sc.next().toUpperCase();
                    prochainPlateau = Plateau.TypePlateau.valueOf(input);
                    joueurCourant.setProchainPlateau(prochainPlateau);
                    break;
                } catch (IllegalArgumentException e) {
                    System.out.println("Entrée invalide. Veuillez entrer PAST, PRESENT ou FUTURE : ");
                }
            } while (true);

            printGamePlay();
        } while (!gameOver(joueurCourant));
        

    }

    public void appliquerCoup(Coup coup) {
        // A COMPLETER
        System.err.println("Bien appliquer le coup");

        switch (coup.getTypeCoup()) {
            Plateau plateau = coup.getPltCourant();
            case MOVE:
                Point src = pieceCourante.getPosition();
                Point dir = coup.getDirection();
                int ligDes = src.x + dir.x;
                int colDes = src.y + dir.y;
                p.setPiece(pieceCourante, ligDes, colDes);
                plateauCourant.setPiece(null, src.x, src.y);
                break;
            case CLONE:
                break;
            case JUMP:
                break;
        }
    
    }

    public boolean estCoupValide(Coup coup) {
        // A COMPLETER
        return true;
    }
    
    public boolean gameOver(Joueur j) {
        if (j.equals(joueur1)) {
            return (past.getNbNoirs() == 0 ? (present.getNbNoirs() == 0 ? true : future.getNbNoirs() == 0) : present.getNbNoirs() == future.getNbBlancs() && future.getNbNoirs() == 0);
        } else if (j.equals(joueur2)) {
            return (past.getNbBlancs() == 0 ? (present.getNbBlancs() == 0 ? true : future.getNbBlancs() == 0) : present.getNbBlancs() == future.getNbNoirs() && future.getNbBlancs() == 0);
        } else {
            return false;
        }
    }

    public void printGamePlay() {
        System.out.println("Next Area J1 : " + joueur1.getProchainPlateau().toString());
        System.out.println("Next Area J2 : " + joueur2.getProchainPlateau().toString());
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE*3 + 2; j++) {
                if (j < TAILLE) {
                    if (past.getPiece(i, j) != null) {
                        if (past.getPiece(i, j).getOwner().equals(joueur1)) {
                            System.out.print("[B]");
                        } else {
                            System.out.print("[N]");
                        }
                    } else {
                        System.out.print("[ ]");
                    }
                }

                if (j == TAILLE || j == TAILLE * 2 + 1) {
                    System.out.print("  ");
                }

                if (j > TAILLE && j < TAILLE * 2 + 1) {
                    Piece p = present.getPiece(i, j - TAILLE - 1);
                    if (p != null) {
                        if (p.getOwner().equals(joueur1)) {
                            System.out.print("[B]");
                        } else {
                            System.out.print("[N]");
                        }
                    } else {
                        System.out.print("[ ]");
                    }
                }

                if (j > TAILLE * 2 + 1) {
                    Piece p = future.getPiece(i, j - TAILLE * 2 - 2);
                    if (p != null) {
                        if (p.getOwner().equals(joueur1)) {
                            System.out.print("[B]");
                        } else {
                            System.out.print("[N]");
                        }
                    } else {
                        System.out.print("[ ]");
                    }
                }
            }
            System.out.println();
        }
    }

}   
