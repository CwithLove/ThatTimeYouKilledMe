package Modele;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * La classe Jeu contient les méthodes pour intéragir avec le modèle du jeu.
 *
 * @author The MACKYZ Protocol
 * @version 2.0
 */
public class Jeu {

    private final static int TAILLE = 4;
    private Plateau past; // plateau passé
    private Plateau present; // plateau présent
    private Plateau future; // plateau futur
    private Joueur joueur1; // joueur 1
    private Joueur joueur2; // joueur 2
    private Joueur joueurCourant;
    private Piece pieceCourante; // pièce courante
    private Plateau plateauCourant; // plateau courant
    private int etapeCoup; // étape du coup, 0 = choisir pièce, 1 = coup 1, 2 = coup 2, 3 = choisir plateau
    private int gameState = 0; // 0 = en cours, 1 = joueur 1 gagne, 2 = joueur 2 gagne
    private HistoriqueJeu historiqueJeu;
    Scanner sc = new Scanner(System.in);
    private ArrayList<IAFields<Couple<Integer, Integer>, String, String, String>> historique = new ArrayList<>();

    // Constructeur => Fini
    /**
     * Crée une nouvelle instance du jeu. Initialise les joueurs, les plateaux
     * et l'état du jeu
     */
    public Jeu() {
        // Initialiser les joueurs
        joueur1 = new Joueur("Blanc", 1, 4, Plateau.TypePlateau.PAST);
        joueur2 = new Joueur("Noir", 2, 4, Plateau.TypePlateau.FUTURE);
        //IAminmax ia = new IAminmax(1);

        // Initialiser les plateaux
        past = new Plateau(Plateau.TypePlateau.PAST, joueur1, joueur2);
        present = new Plateau(Plateau.TypePlateau.PRESENT, joueur1, joueur2);
        future = new Plateau(Plateau.TypePlateau.FUTURE, joueur1, joueur2);
        etapeCoup = 0; // Par défaut, on commence par choisir une pièce

        joueurCourant = joueur1;
        pieceCourante = null;
        gameState = 0;
        plateauCourant = past; // Par défaut, on commence par le plateau passé
        historiqueJeu = new HistoriqueJeu(past, present, future, joueur1, joueur2);
    }

    /**
     * Donne l'étape du coup en cours
     *
     * @return l'étape du coup en cours
     */
    public int getEtape() {
        return etapeCoup;
    }

    // Fini
    /**
     * Change le joueur courant
     */
    public void joueurSuivant() {
        if (joueurCourant.equals(joueur1)) {
            joueurCourant = joueur2;
        } else if (joueurCourant.equals(joueur2)) {
            joueurCourant = joueur1;
        }
    }

    // Fini
    /**
     * Change le plateau courant
     */
    public void majPlateauCourant() {
        Plateau.TypePlateau prochainPlateau = joueurCourant.getProchainPlateau();
        switch (prochainPlateau) {
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
    }

    /**
     * Sélectionne la pièce à déplacer
     *
     * @param lig ligne de la pièce
     * @param col colonne de la pièce
     * @return 1 si le sélection a fonctionné, 0 sinon
     */
    public boolean choisirPiece(int lig, int col) {
        // Choisir la piece a deplacer
        if (etapeCoup != 0) {
            System.err.println("Erreur: etapeCoup != 0 => vous ne pouvez pas choisir une piece maintenant.");
            return false;
        }

        if (lig < 0 || col < 0 || lig > (plateauCourant.getSize() - 1) || col > (plateauCourant.getSize() - 1)) {
            System.out.println("Coordonées incorrectes.");
            return false;
        }

        if (plateauCourant.getPiece(lig, col) == null) {
            System.out.println("Il n'y a aucun piece. Veuillez choisir la piece disponoble.");
            return false;
        }

        if (!plateauCourant.getPiece(lig, col).getOwner().equals(joueurCourant)) {
            System.out.println("Piece invalide ou non possédée par le joueur courant. Veuillez réessayer : ");
            return false;
        }
        pieceCourante = plateauCourant.getPiece(lig, col);
        etapeCoup = 1; // Passer à l'étape 1 (choisir le coup)
        return true;
    }

    //Déplace la pièce dans la direction voulu
    private void deplacerPiece(Piece pieceactuelle, Point dir, Coup coup) {
        //4 cas si la piece arrive sur une case vide, 
        //si elle arrive sur une case occupée par un pion de la meme couleur,
        // si elle arrive sur une case occupée par un pion de l'autre couleur,
        // si elle sort du plateau
        Plateau plateauAC = plateauCourant;
        Point src = pieceactuelle.getPosition();

        int ligDes = src.x + dir.x;
        int colDes = src.y + dir.y;
        Piece newPiecePlace = plateauAC.getPiece(ligDes, colDes);
        //case vide et sort du plateau
        if (newPiecePlace == null) {
            plateauAC.setPiece(pieceactuelle, ligDes, colDes);
            pieceactuelle.setPosition(new Point(ligDes, colDes));
            plateauAC.removePiece(src.x, src.y);
            if (pieceactuelle.getPosition().x < 0 || pieceactuelle.getPosition().x > TAILLE - 1 || pieceactuelle.getPosition().y < 0 || pieceactuelle.getPosition().y > TAILLE - 1) {
                if (pieceactuelle.getOwner().equals(joueur1)) {
                    plateauAC.decBlancs();
                } else if (pieceactuelle.getOwner().equals(joueur2)) {
                    plateauAC.decNoirs();
                }

            }
        } //case occupée par un pion de l'autre joueur
        else if (!plateauAC.estPareilPion(newPiecePlace, pieceactuelle)) {
            deplacerPiece(newPiecePlace, dir, coup);
            plateauAC.setPiece(pieceactuelle, ligDes, colDes);
            pieceactuelle.setPosition(new Point(ligDes, colDes));
            plateauAC.removePiece(src.x, src.y);
        } //case occupée par un pion de la meme couleur
        else if (plateauAC.estPareilPion(newPiecePlace, pieceactuelle)) {
            plateauAC.removePiece(ligDes, colDes);
            plateauAC.removePiece(src.x, src.y);
            //decremente le nbblanc noir du plateau
            if (pieceactuelle.getOwner().equals(joueur1)) {
                plateauAC.decBlancs();
                plateauAC.decBlancs();
            } else if (pieceactuelle.getOwner().equals(joueur2)) {
                plateauAC.decNoirs();
                plateauAC.decNoirs();
            }

        }

    }

    // Deplacer la piece  => Fini
    private void deplacerPiece(Coup coup, Point dir, Piece piece) {
        Point src = piece.getPosition();
        // Position destination
        int ligDes = src.x + dir.x;
        int colDes = src.y + dir.y;
        Piece pieceDestination = plateauCourant.getPiece(ligDes, colDes);
        // Case vide
        if (pieceDestination == null) {
            plateauCourant.setPiece(pieceCourante, ligDes, colDes);
            piece.setPosition(new Point(ligDes, colDes));
            plateauCourant.removePiece(src.x, src.y);
        }
        // Case occupée par un pion de l'autre joueur
        else if (!pieceDestination.getOwner().equals(piece.getOwner())) {
            deplacerPiece(coup, dir, pieceDestination);
            plateauCourant.removePiece(ligDes, colDes);
            plateauCourant.setPiece(piece, ligDes, colDes);
            piece.setPosition(new Point(ligDes, colDes));
            plateauCourant.removePiece(src.x, src.y);
        }
        // Case occupée par un pion de la même couleur => Ce qui cause paradox
        else {
            plateauCourant.removePiece(src.x, src.y);
            plateauCourant.removePiece(ligDes, colDes);
            // Diminue le nombre de pions de l'autre joueur
            if (coup.getPiece().getOwner().equals(joueur1)) {
                coup.getPltCourant().decBlancs();
                coup.getPltCourant().decBlancs();
            } else if (coup.getPiece().getOwner().equals(joueur2)) {
                coup.getPltCourant().decNoirs();
                coup.getPltCourant().decNoirs();
            }
        }
    }
    // Jump - Travel forward => Fini
    /**
     * Fait une action jump
     *
     * @param coup le coup correspondant
     */
    public void jumping(Coup coup) {
        Piece pieceActuelle = coup.getPiece();
        switch (coup.getPltCourant().getType()) {
            case PAST:
                present.setPiece(pieceActuelle, pieceActuelle.getPosition().x, pieceActuelle.getPosition().y);
                past.removePiece(pieceActuelle.getPosition().x, pieceActuelle.getPosition().y);
                if (joueurCourant == joueur1) {
                    past.decBlancs();
                    present.incBlancs();
                } else if (joueurCourant == joueur2) {
                    past.decNoirs();
                    present.incNoirs();
                }
                plateauCourant = present;
                break;

            case PRESENT:
                future.setPiece(pieceActuelle, pieceActuelle.getPosition().x, pieceActuelle.getPosition().y);
                present.removePiece(pieceActuelle.getPosition().x, pieceActuelle.getPosition().y);
                if (joueurCourant == joueur1) {
                    present.decBlancs();
                    future.incBlancs();
                } else if (joueurCourant == joueur2) {
                    present.decNoirs();
                    future.incNoirs();
                }
                plateauCourant = future;
                break;

            default:
                System.err.println("Erreur: Jumping - Travel forward: le plateau n'est pas valide.");
                break;
        }
    }

    // Clone - Travel backward => Fini
    /**
     * Fait une action clone
     *
     * @param coup le coup correspondant
     */
    public void clonage(Coup coup) {
        Piece pieceActuelle = coup.getPiece();
        switch (coup.getPltCourant().getType()) {
            case PRESENT:
                past.setPiece(pieceActuelle, pieceActuelle.getPosition().x, pieceActuelle.getPosition().y);
                coup.getPiece().getOwner().declone();
                //joueurCourant.declone();
                if (joueurCourant == joueur1) {
                    past.incBlancs();
                } else if (joueurCourant == joueur2) {
                    past.incNoirs();
                }
                plateauCourant = past;
                break;

            case FUTURE:
                present.setPiece(pieceActuelle, pieceActuelle.getPosition().x, pieceActuelle.getPosition().y);
                coup.getPiece().getOwner().declone();
                //joueurCourant.declone();
                if (joueurCourant == joueur1) {
                    present.incBlancs();
                } else if (joueurCourant == joueur2) {
                    present.incNoirs();
                }
                plateauCourant = present;
                break;

            default:
                System.err.println("Erreur: Cloning - travel backward: le plateau n'est pas valide.");
                break;
        }
    }

    // Appliquer le coup => Fini
    /**
     * Applique un coup au jeu
     *
     * @param coup le coup voulu
     */
    public void appliquerCoup(Coup coup) {
        int index = -1;
        Point dir = null;
        Point[] directions = {
            new Point(-1, 0), // haut
            new Point(1, 0), // bas
            new Point(0, -1), // gauche
            new Point(0, 1) // droite
        };

        etapeCoup += 1;

        switch (coup.getTypeCoup()) {
            case UP:
                index = 0;
                break;

            case DOWN:
                index = 1;
                break;

            case LEFT:
                index = 2;
                break;

            case RIGHT:
                index = 3;
                break;

            case CLONE:
                clonage(coup);
                return;

            case JUMP:
                jumping(coup);
                return;

            default:
                break;
        }
        deplacerPiece(coup.getPiece(), directions[index], coup);
    }

    // Choisir le coup
    /**
     * Joue un coup si celui-ci est possible
     *
     * @param coup le coup voulu
     * @return 1 si le coup a été joué, 0 sinon
     */
    public boolean jouerCoup(Coup coup) {

        if (!estCoupValide(coup)) {
            System.out.println("Coup invalide. Veuillez réessayer : ");
            return false;
        }

        // Appliquer le coup
        appliquerCoup(coup);

        // Mettre a jour l'etat du jeu
        gameOver(joueurCourant);

        return true;
    }

    // 
    boolean choisirPlateau(Plateau.TypePlateau prochainPlateau) {
        try {
            if (gameOver(joueurCourant) != 0 && etapeCoup != 3) {
                return false;
            }

            //Verifie si on change de plateau
            if (joueurCourant.getProchainPlateau() != prochainPlateau) {
                joueurCourant.setProchainPlateau(prochainPlateau);
            } else {
                System.out.println("Vous etes déjà sur ce plateau ! Veuillez en sélectionner un autre :");
                return false;
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Entrée invalide. Veuillez entrer PAST, PRESENT ou FUTURE : ");
            return false;
        }
        etapeCoup = 0;
        historiqueJeu.add(past, present, future, joueur1, joueur2);
        return true;
    }

    /**
     * Lance le jeu sur les entrée et sortie standards
     */
    public void demarrer() {
        //IAminmax ia = new IAminmax(1);
        // Afficher le plateau initial
        System.out.println("Plateau initial :");
        printGamePlay();

        // Boucle de jeu
        pieceCourante = null;
        joueurCourant = joueur2;
        boolean breakFlag = false;
        int undo = 0;

        // Boucle de jeu
        while (gameState == 0 && !breakFlag) {
            int saute_tour = 0;
            if (undo == 0) {
                joueurSuivant();

            } else {
                undo = 0;
            }

            System.out.println("-------------------------------");
            System.out.println("tour Joueur: " + joueurCourant.getId());
            System.out.println("Nombre de copie restant :" + joueurCourant.getNbClones());

            // Mettre a jour le plateau suivant
            majPlateauCourant();

            if (joueurCourant.equals(joueur1)) {
                if (plateauCourant.getNbBlancs() == 0) {
                    System.out.println("Vous n'avez plus de pions !");
                    saute_tour = 1;
                }
            } else if (joueurCourant.equals(joueur2)) {
                if (plateauCourant.getNbNoirs() == 0) {
                    System.out.println("Vous n'avez plus de pions !");
                    saute_tour = 1;
                }
            }

            // Choisir la piece a deplacer
            try {
                int lig, col;
                do {
                    if (saute_tour == 1) {
                        break;
                    }
                    System.out.print("Veuillez entrez la piece que vous voulez deplacer (ligne colonne) : ");
                    lig = sc.nextInt();
                    col = sc.nextInt();
                    //undo test
                    if (lig == 33 && col == 33) {
                        Undo();

                        printGamePlay();
                    }
                } while (!choisirPiece(lig, col));

            } catch (Exception e) {
                System.out.println("Erreur : " + e.getMessage());
                sc.nextLine(); // Clear the scanner buffer
                continue; // Re-demander l'entrée
            }

            // Chaque tour le joueur doit faire 2 coups
            do {
                if (saute_tour == 1) {
                    break;
                }
                Coup coup;
                String input;
                do {
                    // Choisir le coup
                    ArrayList<Coup> coupsPossibles = getCoupPossibles(plateauCourant, pieceCourante);
                    System.out.println("Coup possibles : ");
                    for (Coup c : coupsPossibles) {
                        System.out.println(c.getTypeCoup());
                    }
                    System.out.print("\nVeuillez entrer le coup (UP, DOWN, LEFT, RIGHT, JUMP, CLONE) : ");
                    input = sc.next().toUpperCase();
                    //undo test
                    if (input.equals("UNDO")) {
                        Undo();
                        undo = 1;
                        saute_tour = 1;
                        printGamePlay();
                        break;
                    }
                    System.out.println(input);
                    while (!input.equals("UP") && !input.equals("DOWN") && !input.equals("LEFT") && !input.equals("RIGHT") && !input.equals("JUMP") && !input.equals("CLONE")) {
                        System.out.print("\nMauvaise saisie : Veuillez entrer le coup (UP, DOWN, LEFT, RIGHT, JUMP, CLONE) : ");
                        input = sc.next().toUpperCase();
                    }
                } while (jouerCoup(coup = new Coup(pieceCourante, plateauCourant, Coup.TypeCoup.valueOf(input))) == false);

                gameState = gameOver(joueurCourant);
                System.out.println("Game State : " + gameState);

                if (gameState != 0) {
                    breakFlag = true; // Si le jeu est terminé, sortir de la boucle
                    break; // Si le jeu est terminé, sortir de la boucle
                }

                // Affichage
                printGamePlay();
            } while (etapeCoup < 3 && etapeCoup >= 1);

            if (breakFlag) {
                break;
            }

            // Prochain plateau
            Plateau.TypePlateau prochainPlateau;
            if (undo == 0) {
                System.out.println("Veuillez entrer PAST, PRESENT ou FUTURE : ");
                String input = sc.next().toUpperCase();
                //test undo
                if (input.equals("UNDO")) {
                    Undo();

                    printGamePlay();
                    undo = 1;
                }
                if (undo == 0) {
                    if (!input.equals("PAST") && !input.equals("PRESENT") && !input.equals("FUTURE")) {
                        System.out.println("Mauvaise saisie : Veuillez entrer PAST, PRESENT ou FUTURE : ");
                        input = sc.next().toUpperCase();
                    }
                    while (!choisirPlateau(prochainPlateau = Plateau.TypePlateau.valueOf(input))) {
                        System.out.println("Veuillez choisir un plateau valide (PAST, PRESENT, FUTURE) : ");
                        input = sc.next().toUpperCase();
                    }
                }
            }
            printGamePlay();
        };

        if (gameState == 2) {
            System.out.println("Joueur 2 a gagné !"); 
        }else if (gameState == 1) {
            System.out.println("Joueur 1 a gagné !");
        }

    }

    /**
     * Vérifie si le coup est jouable
     *
     * @param coup le coup voulu
     * @return 1 si le coup est jouable, 0 sinon
     */
    public boolean estCoupValide(Coup coup) {
        System.out.println("Coup: " + coup.getTypeCoup());
        ArrayList<Coup> coupsPossibles = getCoupPossibles(coup.getPltCourant(), coup.getPiece());
        for (Coup possibleCoup : coupsPossibles) {
            System.out.println("Coup possible: " + possibleCoup.getTypeCoup());
        }
        for (Coup possibleCoup : coupsPossibles) {
            if (possibleCoup.equals(coup)) {
                return true; //attention peut etre overide le equals
            }
        }

        return false;
    }

    // condition d'arret
    public int gameOver(Joueur joueur) {
        if (joueur.equals(joueur1)) {
            System.out.println("Joueur 1: " + past.getNbNoirs() + " " + present.getNbNoirs() + " " + future.getNbNoirs());
            if (past.getNbNoirs() > 0 && present.getNbNoirs() == 0 && future.getNbNoirs() == 0) {
                gameState = 1;
                return 1; //joueur 1 a gagne
            }
            if (past.getNbNoirs() == 0 && present.getNbNoirs() > 0 && future.getNbNoirs() == 0) {
                gameState = 1;
                return 1;
            }
            if (past.getNbNoirs() == 0 && present.getNbNoirs() == 0 && future.getNbNoirs() > 0) {
                gameState = 1;
                return 1;
            }
        } else if (joueur.equals(joueur2)) {
            System.out.println("Joueur 2: " + past.getNbBlancs() + " " + present.getNbBlancs() + " " + future.getNbBlancs());
            if (past.getNbBlancs() > 0 && present.getNbBlancs() == 0 && future.getNbBlancs() == 0) {
                gameState = 2;
                return 2;
            }
            if (past.getNbBlancs() == 0 && present.getNbBlancs() > 0 && future.getNbBlancs() == 0) {
                gameState = 2;
                return 2;
            }
            if (past.getNbBlancs() == 0 && present.getNbBlancs() == 0 && future.getNbBlancs() > 0) {
                gameState = 2;
                return 2;
            }
        }
        // Si aucun joueur n'a gagné
        gameState = 0;
        return 0;
    }

    /**
     * Affiche le jeu sur la sortie standard
     */
    public void printGamePlay() {
        System.out.println("Next Area J1 : " + joueur1.getProchainPlateau().toString());
        System.out.println("Next Area J2 : " + joueur2.getProchainPlateau().toString());
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE * 3 + 2; j++) {
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

    //Liste des coups possibles => Fini
    /**
     * Donne la liste des coups possibles pour un pièce
     *
     * @param plateau plateau sur lequel la pièce se trouve
     * @param piece la pièce à partir de laquelle on veut savoir les coups
     * possibles
     * @return la liste des coups possibles
     */
    public ArrayList<Coup> getCoupPossibles(Plateau plateau, Piece piece) {
        ArrayList<Coup> coupsPossibles = new ArrayList<>();
        // Définir les directions possibles (haut, bas, gauche, droite)
        Point[] directions = {
            new Point(-1, 0), // haut
            new Point(1, 0), // bas
            new Point(0, -1), // gauche
            new Point(0, 1) // droite
        };

        // Pour chaque direction
        for (int i = 0; i < 4; i++) {
            Point newPos = new Point(piece.getPosition().x + directions[i].x, piece.getPosition().y + directions[i].y);
            // Vérifier si la nouvelle position est valide
            if (newPos.x < 0 || newPos.x >= plateau.getSize() || newPos.y < 0 || newPos.y >= plateau.getSize()) {
                continue; // Si la nouvelle position est en dehors du plateau, passer à la direction suivante
            }

            // il faut que le newPos est libre ou la piece qui se situe la est de l'autre joueur
            if (plateau.getPiece((int) newPos.getX(), (int) newPos.getY()) == null || !plateau.getPiece((int) newPos.getX(), (int) newPos.getY()).getOwner().equals(piece.getOwner())) {
                Coup.TypeCoup typeCoup = null;
                switch (i) {
                    case 0:
                        typeCoup = Coup.TypeCoup.UP;
                        break;

                    case 1:
                        typeCoup = Coup.TypeCoup.DOWN;
                        break;

                    case 2:
                        typeCoup = Coup.TypeCoup.LEFT;
                        break;

                    case 3:
                        typeCoup = Coup.TypeCoup.RIGHT;
                        break;

                    default:
                        break;
                }
                coupsPossibles.add(new Coup(piece, plateau, typeCoup));
            }
        }

        switch (plateau.getType()) {
            case PAST:
                if (present.getPiece(piece.getPosition().x, piece.getPosition().y) == null) {
                    coupsPossibles.add(new Coup(piece, plateau, Coup.TypeCoup.JUMP));
                }
                break;

            case FUTURE:
                if (present.getPiece(piece.getPosition().x, piece.getPosition().y) == null && joueurCourant.getNbClones() > 0) {
                    coupsPossibles.add(new Coup(piece, plateau, Coup.TypeCoup.CLONE));
                }
                break;

            case PRESENT:
                if (past.getPiece(piece.getPosition().x, piece.getPosition().y) == null && joueurCourant.getNbClones() > 0) {
                    coupsPossibles.add(new Coup(piece, plateau, Coup.TypeCoup.CLONE));
                }
                if (future.getPiece(piece.getPosition().x, piece.getPosition().y) == null) {
                    coupsPossibles.add(new Coup(piece, plateau, Coup.TypeCoup.JUMP));
                }

            default:
                break;
        }

        return coupsPossibles;
    }

    public int getTAILLE() {
        return TAILLE;
    }

    public Joueur getJoueurCourant() {
        return joueurCourant;
    }

    public void setJoueurCourant(Joueur joueur) {
        this.joueurCourant = joueur;
    }

    public Plateau getPast() {
        return past;
    }

    public Plateau getPresent() {
        return present;
    }

    public Plateau getFuture() {
        return future;
    }

    public Plateau getPlateauByType(Plateau.TypePlateau type) {
        switch (type) {
            case PAST:
                return past;
            case PRESENT:
                return present;
            case FUTURE:
                return future;
            default:
                return null;
        }
    }

    public Joueur getJoueur1() {
        return joueur1;
    }

    public Joueur getJoueur2() {
        return joueur2;
    }

    public int getGameState() {
        return gameState;
    }

    public void setPieceCourante(Piece piece) {
        this.pieceCourante = piece;
    }

    public Piece getPieceCourante() {
        return pieceCourante;
    }

    public int getEtapeCoup() {
        return etapeCoup;
    }

    public void setEtapeCoup(int etape) {
        this.etapeCoup = etape;
    }

    public Plateau getPlateauCourant() {
        return plateauCourant;
    }

    /**
     *
     * Process the command from the GameServerManager Command format:
     * <TYPE_COUP>:<PLATEAU_TYPE>:<ROW>:<COL>[:<DIR_DX>:<DIR_DY>]
     *
     * @param command command string
     * @param playerId joueur ID
     * @return True commande valide sinon false
     */
    public boolean processPlayerCommand(String command, int playerId) {
        if (command == null || command.isEmpty()) {
            System.err.println("Jeu: Commande vide reçue.");
            return false;
        }

        try {
            // etudier la commande
            String[] parts = command.split(":");
            if (parts.length < 4) {
                System.err.println("Jeu: Commande mal formatée: " + command);
                return false;
            }

            // etudier les parametres de la commande
            Coup.TypeCoup typeCoup = Coup.TypeCoup.valueOf(parts[0]);
            Plateau.TypePlateau plateauType = Plateau.TypePlateau.valueOf(parts[1]);
            int row = Integer.parseInt(parts[2]);
            int col = Integer.parseInt(parts[3]);

            // verifier si c'est le tour du joueur
            if (joueurCourant.getId() != playerId) {
                System.err.println("Jeu: Ce n'est pas le tour de joueur " + playerId);
                return false;
            }

            // obtenir le plateau et la piece correspondant
            Plateau selectedPlateau = getPlateauByType(plateauType);
            if (selectedPlateau == null) {
                System.err.println("Jeu: Plateau invalide: " + plateauType);
                return false;
            }

            // obtenir la piece selectionnee
            Piece selectedPiece = selectedPlateau.getPiece(row, col);
            System.out.println("Jeu: pieces possible sur le plateau :");
            for (Piece piece : selectedPlateau.getPieces(joueurCourant)) {
                System.out.println("Jeu: " + piece.getOwner().toString() + " " + piece.getPosition().toString());
            }
            if (selectedPiece == null) {
                System.err.println("Jeu: Aucune pièce à la position " + selectedPlateau.getType() + " (" + row + "," + col + ")");
                return false;
            }

            Joueur currentPlayer = (playerId == 1) ? joueur1 : joueur2;

            // verifier si la piece appartient au joueur
            if (!selectedPiece.getOwner().equals(currentPlayer)) {
                System.err.println("Jeu: La pièce n'appartient pas au joueur " + playerId);
                return false;
            }

            // Maj l'etat
            this.plateauCourant = selectedPlateau;
            this.pieceCourante = selectedPiece;

            if (etapeCoup == 0) {
                etapeCoup = 1; // 第一次选择棋子
            }

            // 创建动作并使用jouerCoup方法
            Coup playerCoup = new Coup(selectedPiece, selectedPlateau, typeCoup);
            boolean isValid = jouerCoup(playerCoup);

            if (!isValid) {
                System.err.println("Jeu: Coup invalide de type " + typeCoup);
                return false;
            }

            // // 如果玩家已经完成了两步操作，应该自动切换到下一个玩家
            // if (etapeCoup >= 3) {
            //     // 第三步选择新的棋盘
            //     Plateau.TypePlateau nextPlateau = null;
            //     if (plateauType != Plateau.TypePlateau.PAST) {
            //         nextPlateau = Plateau.TypePlateau.PAST;
            //     } else {
            //         nextPlateau = Plateau.TypePlateau.PRESENT;
            //     }
            //     joueurCourant.setProchainPlateau(nextPlateau);
            //     etapeCoup = 0;
            //     joueurSuivant(); // 切换到下一玩家
            //     majPlateauCourant();
            //     // 添加明确的日志，显示当前轮到谁
            //     System.out.println("Jeu: Joueur suivant - ID: " + joueurCourant.getId() +
            //                        " (" + (joueurCourant.equals(joueur1) ? "Blanc/Lemiel" : "Noir/Zarek") + ")");
            // }
            return true;

        } catch (IllegalArgumentException e) {
            System.err.println("Jeu: Erreur d'analyse ou valeur invalide: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Jeu: Erreur lors du traitement du message: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Convertit l'état du jeu en chaîne pour la sérialisation
    public String getGameStateAsString() {
        StringBuilder sb = new StringBuilder();

        // 添加etapeCoup信息
        sb.append("etapeCoup:").append(etapeCoup).append(";");

        // ID du joueur courant
        sb.append("JC:").append(joueurCourant != null ? joueurCourant.getId() : 0).append(";");

        // Nombre de clones pour chaque joueur
        sb.append("C1:").append(joueur1.getNbClones()).append(";");
        sb.append("C2:").append(joueur2.getNbClones()).append(";");

        // Prochain plateau pour chaque joueur
        sb.append("P1:").append(joueur1.getProchainPlateau()).append(";");
        sb.append("P2:").append(joueur2.getProchainPlateau()).append(";");

        // État des plateaux (représenté par des matrices 4x4)
        sb.append("P:"); // PAST
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE; j++) {
                Piece p = past.getPiece(i, j);
                if (p == null) {
                    sb.append('0');
                } else if (p.getOwner().equals(joueur1)) {
                    sb.append('1');
                } else {
                    sb.append('2');
                }
            }
        }
        sb.append(";");

        sb.append("PR:"); // PRESENT
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE; j++) {
                Piece p = present.getPiece(i, j);
                if (p == null) {
                    sb.append('0');
                } else if (p.getOwner().equals(joueur1)) {
                    sb.append('1');
                } else {
                    sb.append('2');
                }
            }
        }
        sb.append(";");

        sb.append("F:"); // FUTURE
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE; j++) {
                Piece p = future.getPiece(i, j);
                if (p == null) {
                    sb.append('0');
                } else if (p.getOwner().equals(joueur1)) {
                    sb.append('1');
                } else {
                    sb.append('2');
                }
            }
        }

        return sb.toString();
    }

    private void majJoueurCourant() {
        if (joueurCourant.equals(joueur2)) {
            joueurCourant = joueur2;
        } else if (joueurCourant.equals(joueur1)) {
            joueurCourant = joueur1;
        }
    }

    public void Undo() {
        //si EtapeCoup == 0,
        if (historiqueJeu.getNbTours() > 0 && etapeCoup == 0) {
            historiqueJeu.pop();
            joueur1 = historiqueJeu.getJoueur1();
            joueur2 = historiqueJeu.getJoueur2();
            past = historiqueJeu.getPast(joueur1, joueur2);
            present = historiqueJeu.getPresent(joueur1, joueur2);
            future = historiqueJeu.getFuture(joueur1, joueur2);

            etapeCoup = 0;
            joueurSuivant();
            majPlateauCourant();
            majJoueurCourant();
        } //si EtapeCoup == 1 2 ou 3
        else if (historiqueJeu.getNbTours() >= 0) {
            // retourne le plateau a l'etat du debut du tour
            joueur1 = historiqueJeu.getJoueur1();
            joueur2 = historiqueJeu.getJoueur2();
            past = historiqueJeu.getPast(joueur1, joueur2);
            present = historiqueJeu.getPresent(joueur1, joueur2);
            future = historiqueJeu.getFuture(joueur1, joueur2);

            etapeCoup = 0;
            majPlateauCourant();
            majJoueurCourant();
        }
    }

}
