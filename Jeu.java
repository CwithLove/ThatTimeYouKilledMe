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
        Plateau plateauTraitant = null;
        pieceCourante = null;
        joueurCourant = joueur2;
        do {
            if (joueurCourant.equals(joueur1)) {
                joueurCourant = joueur2;
            } else if (joueurCourant.equals(joueur2)) {
                joueurCourant = joueur1;
            }
            System.out.println("-------------------------------");
            System.out.println("tour Joueur: " + joueurCourant.getId());

            // Mettre a jour le plateau suivant
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
            plateauTraitant = plateauCourant;

            // Choisir la piece a deplacer

            // A FIXER SI ON TAPE CLONE DANS CE CAS LA ET CA SE TERMINE LE TOUR
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
            int i = 2;
            do {
                Coup coup;
                do {
                    coup = joueurCourant.choisirCoup(plateauTraitant, pieceCourante);
                } while (estCoupValide(coup) == false);
                appliquerCoup(coup);
                // Mettre a jour le plateau suivant
                switch (joueurCourant.getProchainPlateau()) {
                    case PAST:
                        plateauTraitant = past;
                        break;
                    case PRESENT:
                        plateauTraitant = present;
                        break;
                    case FUTURE:
                        plateauTraitant = future;
                        break;
                }
                joueurCourant.setProchainPlateau(plateauCourant.getType());
                printGamePlay();
                i-=1;
            } while (i > 0);


            // Prochain plateau
            System.out.print("Veuillez entrer le prochain plateau (PAST, PRESENT, FUTURE) : ");
            Plateau.TypePlateau prochainPlateau;
            // A AJOUTER: IL FAUT LE JOUEUR CHOISIR UN AREA DIFFERENT QUE CELUI COURANT
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

        Plateau plateau = coup.getPltCourant();
        Point src;
        Point dir;
        switch (coup.getTypeCoup()) {
            case MOVE:
                src = pieceCourante.getPosition();
                dir = coup.getDirection();
                int ligDes = src.x + dir.x;
                int colDes = src.y + dir.y;
                plateau.setPiece(pieceCourante, ligDes, colDes);
                pieceCourante.setPosition(new Point(ligDes, colDes));
                plateau.setPiece(null, src.x, src.y);

                // AJOUTER LE PUSH (A VERIFIER COMMENT DEC nbBlancs et nbNoirs
                break;
            case CLONE:
                src = pieceCourante.getPosition();
                Piece clone = new Piece(joueurCourant, src);
                switch (plateau.getType()) {
                    case PRESENT:
                        // VERIFIER LE PASSE POUR PARADOX
                        joueurCourant.setProchainPlateau(Plateau.TypePlateau.PAST);
                        past.setPiece(pieceCourante, src.x, src.y);
                        present.setPiece(clone, src.x, src.y);
                        if (joueurCourant.equals(joueur1)) {
                            past.incBlancs();
                        } else if (joueurCourant.equals(joueur2)) {
                            past.incNoirs();
                        }
                        break;

                    case FUTURE:
                        // VERIFIER LE PRESENT POUR PARADOX
                        joueurCourant.setProchainPlateau(Plateau.TypePlateau.PRESENT);
                        future.setPiece(clone, src.x, src.y);
                        present.setPiece(pieceCourante, src.x, src.y);
                        if (joueurCourant.equals(joueur1)) {
                            present.incBlancs();
                        } else if (joueurCourant.equals(joueur2)) {
                            present.incNoirs();
                        }
                        break;
                    default:
                        // ON PEUT PAS ETRE DANS CET ETAT LA
                        System.err.println("Erreur: CLONE DEPUIS LE PASSE");
                        return;
                }
                break;

            case JUMP:
                src = pieceCourante.getPosition();
                switch (plateau.getType()) {
                    case PRESENT:
                        // VERIFIER LE PASSE POUR PARADOX
                        joueurCourant.setProchainPlateau(Plateau.TypePlateau.FUTURE);
                        future.setPiece(pieceCourante, src.x, src.y);
                        present.setPiece(null, src.x, src.y);
                        if (joueurCourant.equals(joueur1)) {
                            present.decBlancs();
                            future.incBlancs();
                        } else if (joueurCourant.equals(joueur2)) {
                            present.decBlancs();
                            future.incBlancs();
                        }
                        break;

                    case PAST:
                        // VERIFIER LE PRESENT POUR PARADOX
                        joueurCourant.setProchainPlateau(Plateau.TypePlateau.PRESENT);
                        present.setPiece(pieceCourante, src.x, src.y);
                        past.setPiece(null, src.x, src.y);
                        if (joueurCourant.equals(joueur1)) {
                            past.decBlancs();
                            present.incBlancs();
                        } else if (joueurCourant.equals(joueur2)) {
                            past.decNoirs();
                            present.incNoirs();
                        }
                        break;
                    default:
                        // ON PEUT PAS ETRE DANS CET ETAT LA
                        System.err.println("Erreur: CLONE DEPUIS LE PASSE");
                        return;
                }
                break;
        }
    
    }

    // A AJOUTER UNE REGLE, SI TU CHOIS UN PLATEAU OU IL N'Y A PAS DE TES PIONS
    public boolean estCoupValide(Coup coup) {
        // A COMPLETER
        // POUR LE MOVE
        // VERIFIER SI GET OUT YOURSELF OF THE BOARD IS A VALID COUP
        // OR YOU CAN ONLY ELIMINATE ANOTHER ONE BY PUSHING THEM

        // POUR LE JUMP (TRAVEL FORWARD)
        // VERIFIER SI C'EST FUTURE => coup invalide
        // VERIFIER SI LE PROCHAIN POSITION EST BIEN LE TYPE DE LA PIECE
        // SI OUI: PARADOX => coup valide
        // SI L'AUTRE TYPE: => coup invalide
        // SI NON: coup valide

        // POUR LE CLONE (TRAVEL BACKWARD)
        // VERIFIER SI C'EST LE PASSE => coup invalide
        // VERIFIER EXACTEMENT COMME CEUX DE JUMP
        // VERIFIER AUSSI SI LE NOMBRE CLONE EST > 0

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
