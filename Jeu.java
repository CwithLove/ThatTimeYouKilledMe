import java.awt.Point;
import java.util.Scanner;
import java.util.ArrayList;

public class Jeu {
    final static int TAILLE = 4;
    private Plateau past; // plateau past
    private Plateau present; // plateau present
    private Plateau future; // plateau future
    private Joueur joueur1; // joueur 1
    private Joueur joueur2; // joueur 2
    private Joueur joueurCourant;
    private Piece pieceCourante; // piece courante
    private Plateau plateauCourant; // plateau courant
    private int etapeCoup; // etape du coup, 0 = choisir piece , 1 = coup 1, 2 = coup 2, 3 = choisir plateau
    private int gameState = 0; // 0 = en cours, 1 = joueur 1 gagne, 2 = joueur 2 gagne
    Scanner sc = new Scanner(System.in);
    private ArrayList<IAFields<Couple<Integer,Integer>,String,String,String>> historique = new ArrayList<>();
    
    // Constructeur => Fini
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

        joueurCourant = joueur2;
        pieceCourante = null;
        gameState = 0;
        plateauCourant = past; // Par défaut, on commence par le plateau passé
    }

    int getEtape() {
        return etapeCoup;
    }

    // Fini
    public void joueurSuivant() {
        if (joueurCourant.equals(joueur1)) {
            joueurCourant = joueur2;
        } else if (joueurCourant.equals(joueur2)) {
            joueurCourant = joueur1;
        }
    }

    // Fini
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

    // Choisir la piece a deplacer => Fini
    public boolean choisirPiece(int lig, int col) {
        // Choisir la piece a deplacer
        if  (etapeCoup != 0) {
            System.err.println("Erreur: etapeCoup != 0 => vous ne pouvez pas choisir une piece maintenant.");
            return false;
        }

        if ( lig < 0 || col < 0 || lig > (plateauCourant.getSize()-1) || col > (plateauCourant.getSize()-1)){
            System.out.println("Coordonées incorrectes.");
            return false;
        }

        if (!plateauCourant.getPiece(lig, col).getOwner().equals(joueurCourant) ) {
            System.out.println("Piece invalide ou non possédée par le joueur courant. Veuillez réessayer : ");
            return false;
        }
        pieceCourante = plateauCourant.getPiece(lig, col);
        etapeCoup = 1; // Passer à l'étape 1 (choisir le coup)
        return true;          
    }

    private void deplacerPiece(Piece pieceactuelle, Point dir, Coup coup) {
        //4 cas si la piece arrive sur une case vide, 
        //si elle arrive sur une case occupée par un pion de la meme couleur,
        // si elle arrive sur une case occupée par un pion de l'autre couleur,
        // si elle sort du plateau
        Plateau plateauAC = plateauCourant;
        Point src = pieceactuelle.getPosition();
        
        int ligDes = src.x + dir.x;
        int colDes = src.y + dir.y;
        Piece newPiecePlace = plateauAC.getPiece(ligDes,colDes);
        //case vide et sort du plateau
        if (newPiecePlace == null) {
            plateauAC.setPiece(pieceactuelle, ligDes, colDes);
            pieceactuelle.setPosition(new Point(ligDes, colDes));
            plateauAC.removePiece(src.x, src.y);
            if (pieceactuelle.getPosition().x < 0 || pieceactuelle.getPosition().x > TAILLE-1 || pieceactuelle.getPosition().y < 0 || pieceactuelle.getPosition().y > TAILLE-1) {
                if (pieceactuelle.getOwner().equals(joueur1)) {
                    plateauAC.decBlancs();
                } else if (pieceactuelle.getOwner().equals(joueur2)) {
                    plateauAC.decNoirs();
                }
                
            }
        }
        //case occupée par un pion de l'autre joueur
        else if (!plateauAC.estPareilPion(newPiecePlace, pieceactuelle)) {
                deplacerPiece(newPiecePlace,dir,coup);
                plateauAC.setPiece(pieceactuelle, ligDes, colDes);
                pieceactuelle.setPosition(new Point(ligDes, colDes));
                plateauAC.removePiece(src.x, src.y);
                }
        //case occupée par un pion de la meme couleur
        else if (plateauAC.estPareilPion(newPiecePlace, pieceactuelle)) {
            plateauAC.removePiece(ligDes, colDes);
            plateauAC.removePiece(src.x, src.y);
            //decremente le nbblanc noir du plateau
            if (pieceactuelle.getOwner().equals(joueur1)) {
                plateauAC.decBlancs();
            } else if (pieceactuelle.getOwner().equals(joueur2)) {
                plateauAC.decNoirs();
            }

        }
            
    
    }

    // // Deplacer la piece  => Fini
    // private void deplacerPiece(Coup coup, Point dir, Piece piece) {
    //     Point src = piece.getPosition();
    //     // Position destination
    //     int ligDes = src.x + dir.x;
    //     int colDes = src.y + dir.y;

    //     Piece pieceDestination = plateauCourant.getPiece(ligDes, colDes);

    //     // Case vide
    //     if (pieceDestination == null) {
    //         plateauCourant.setPiece(pieceCourante, ligDes, colDes);
    //         piece.setPosition(new Point(ligDes, colDes));
    //         plateauCourant.removePiece(src.x, src.y);
    //     }
    //     // Case occupée par un pion de l'autre joueur
    //     else if (!pieceDestination.getOwner().equals(piece.getOwner())) {
    //         deplacerPiece(coup, dir, pieceDestination);
    //         plateauCourant.removePiece(ligDes, colDes);
    //         plateauCourant.setPiece(piece, ligDes, colDes);
    //         piece.setPosition(new Point(ligDes, colDes));
    //         plateauCourant.removePiece(src.x, src.y);
    //     }
    //     // Case occupée par un pion de la même couleur => Ce qui cause paradox
    //     else {
    //         plateauCourant.removePiece(src.x, src.y);
    //         plateauCourant.removePiece(ligDes, colDes);
    //         // Diminue le nombre de pions de l'autre joueur
    //         if (coup.getPiece().getOwner().equals(joueur1)) {
    //             coup.getPltCourant().decBlancs();
    //             coup.getPltCourant().decBlancs();
    //         } else if (coup.getPiece().getOwner().equals(joueur2)) {
    //             coup.getPltCourant().decNoirs();
    //             coup.getPltCourant().decNoirs();
    //         }
    //     }
    // }

    // Jump - Travel forward => Fini
    public void jumping(Coup coup) {
        Piece pieceActuelle = coup.getPiece();
        switch (coup.getPltCourant().getType()) {
            case PAST:
                present.setPiece(pieceActuelle, pieceActuelle.getPosition().x, pieceActuelle.getPosition().y);
                past.removePiece(pieceActuelle.getPosition().x, pieceActuelle.getPosition().y);        
                plateauCourant = present;
                break;

            case PRESENT:
                future.setPiece(pieceActuelle, pieceActuelle.getPosition().x, pieceActuelle.getPosition().y);
                present.removePiece(pieceActuelle.getPosition().x, pieceActuelle.getPosition().y);
                plateauCourant = future;
                break;

            default:
                System.err.println("Erreur: Jumping - Travel forward: le plateau n'est pas valide.");
                break;
        }
    }

    // Clone - Travel backward => Fini
    public void clonage(Coup coup){
        Piece pieceActuelle = coup.getPiece();
        switch (coup.getPltCourant().getType()) {
            case PRESENT:
                past.setPiece(pieceActuelle, pieceActuelle.getPosition().x, pieceActuelle.getPosition().y);   
                coup.getPiece().getOwner().declone();
                plateauCourant = past;
                break;

            case FUTURE:
                present.setPiece(pieceActuelle, pieceActuelle.getPosition().x, pieceActuelle.getPosition().y);
                coup.getPiece().getOwner().declone();
                plateauCourant = present;
                break;

            default:
                System.err.println("Erreur: Cloning - travel backward: le plateau n'est pas valide.");
                break;
        }
    }

    // Appliquer le coup => Fini
    public void appliquerCoup(Coup coup) {
        int index = -1;
        Point dir = null;
        Point[] directions = {
            new Point(-1, 0), // haut
            new Point(1, 0),  // bas
            new Point(0, -1), // gauche
            new Point(0, 1)   // droite
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
        deplacerPiece(coup.getPiece(), directions[index], coup );
    }

    // Choisir le coup
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
            if (gameOver(joueurCourant) != 0 && etapeCoup != 3)
                return false;
            
            //Verifie si on change de plateau
            if (joueurCourant.getProchainPlateau() != prochainPlateau) {
                joueurCourant.setProchainPlateau(prochainPlateau);
            }
            else {
                System.out.println("Vous etes déjà sur ce plateau ! Veuillez en sélectionner un autre :");
                return false;
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Entrée invalide. Veuillez entrer PAST, PRESENT ou FUTURE : ");
            return false;
        }
        etapeCoup = 0; 
        return true;
    }

    
        

    public void demarrer() {
        //IAminmax ia = new IAminmax(1);
        // Afficher le plateau initial
        System.out.println("Plateau initial :");
        printGamePlay();

        // Boucle de jeu
        pieceCourante = null;
        joueurCourant = joueur2;
        boolean breakFlag = false;

        // Boucle de jeu
        while (gameState == 0 && !breakFlag) {
            joueurSuivant();
            System.out.println("-------------------------------");
            System.out.println("tour Joueur: " + joueurCourant.getId());

            // Mettre a jour le plateau suivant
            majPlateauCourant();
            if (joueurCourant.equals(joueur1)) {
                if (plateauCourant.getNbBlancs() == 0) {
                    System.out.println("Vous n'avez plus de pions !");
                    break;
                }
            } else if (joueurCourant.equals(joueur2)) {
                if (plateauCourant.getNbNoirs() == 0) {
                    System.out.println("Vous n'avez plus de pions !");
                    break;
                }
            }

            // Choisir la piece a deplacer
            try {
                int lig, col;
                do {
                    if ((joueurCourant.equals(joueur1) && plateauCourant.getNbBlancs() == 0) || (joueurCourant.equals(joueur2) && plateauCourant.getNbNoirs() == 0)){
                        break;
                    }
                    System.out.print("Veuillez entrez la piece que vous voulez deplacer (ligne colonne) : ");
                    lig = sc.nextInt();
                    col = sc.nextInt();
                } while (!choisirPiece(lig, col));

            } catch (Exception e) {
                System.out.println("Erreur : " + e.getMessage());
                sc.nextLine(); // Clear the scanner buffer
                continue; // Re-demander l'entrée
            }
            
            // Chaque tour le joueur doit faire 2 coups
            do {
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
                }
                while(jouerCoup(coup = new Coup(pieceCourante, plateauCourant, Coup.TypeCoup.valueOf(input))) == false);

                if (gameState != 0) {
                    breakFlag = true; // Si le jeu est terminé, sortir de la boucle
                    break; // Si le jeu est terminé, sortir de la boucle
                }

                // Affichage
                printGamePlay();
            } while (etapeCoup < 3 && etapeCoup >= 1);

            if (breakFlag) break;

            // Prochain plateau
            Plateau.TypePlateau prochainPlateau;
            System.out.println("Entrée invalide. Veuillez entrer PAST, PRESENT ou FUTURE : ");
            while(!choisirPlateau(prochainPlateau = Plateau.TypePlateau.valueOf(sc.next().toUpperCase()))){
                System.out.println("Veuillez choisir un plateau valide (PAST, PRESENT, FUTURE) : ");
            }

            printGamePlay();
        };

        if (gameState == 2)
            System.out.println("Joueur 2 a gagné !");
        else if (gameState == 1)
            System.out.println("Joueur 1 a gagné !");

    }

    public boolean estCoupValide(Coup coup) {
        ArrayList<Coup> coupsPossibles = getCoupPossibles(coup.getPltCourant(), coup.getPiece());
        for (Coup possibleCoup : coupsPossibles) {
            if (possibleCoup.equals(coup)) {
                return true; //attention peut etre overide le equals
            }
        }

        return false;
    }
    
    // condition d'arret
    private int gameOver(Joueur joueur) {
        //System.out.println("Nombre de blancs: passe "+past.getNbBlancs()+", present "+present.getNbBlancs()+", future "+future.getNbBlancs());
        //System.out.println("Nombre de Noirs: passe "+past.getNbNoirs()+", present "+present.getNbNoirs()+", future "+future.getNbNoirs());
        if (joueur.equals(joueur1)){
            if (past.getNbNoirs() > 0 && present.getNbNoirs() == 0 && future.getNbNoirs() == 0){
                gameState = 1;
                return 1; //joueur 1 a gagne
            }
            if (past.getNbNoirs() == 0 && present.getNbNoirs() > 0 && future.getNbNoirs() == 0){
                gameState = 1;
                return 1;
            }
            if (past.getNbNoirs() == 0 && present.getNbNoirs() == 0 && future.getNbNoirs() > 0){
                gameState = 1;
                return 1;
            }
        } else if (joueur.equals(joueur2)){
            if (past.getNbBlancs() > 0 && present.getNbBlancs() == 0 && future.getNbBlancs() == 0){
                gameState = 2;
                return 2;
            }
            if (past.getNbBlancs() == 0 && present.getNbBlancs() > 0 && future.getNbBlancs() == 0){
                gameState = 2;
                return 2;
            }
            if (past.getNbBlancs() == 0 && present.getNbBlancs() == 0 && future.getNbBlancs() > 0){
                gameState = 2;
                return 2;
            }
        }
        // Si aucun joueur n'a gagné
        gameState = 0;
        return 0;
    }




    // afficher le jeu => Fini
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

    //Liste des coups possibles => Fini
    public ArrayList<Coup> getCoupPossibles(Plateau plateau, Piece piece) {
        ArrayList<Coup> coupsPossibles = new ArrayList<>();
        // Définir les directions possibles (haut, bas, gauche, droite)
        Point[] directions = {
            new Point(-1, 0), // haut
            new Point(1, 0),  // bas
            new Point(0, -1), // gauche
            new Point(0, 1)   // droite
        };

        // Pour chaque direction
        for (int i = 0; i < 4; i++) {
            Point newPos = new Point(piece.getPosition().x + directions[i].x, piece.getPosition().y + directions[i].y); 
            // Vérifier si la nouvelle position est valide
            if (newPos.x < 0 || newPos.x >= plateau.getSize() || newPos.y < 0 || newPos.y >= plateau.getSize()) {
                continue; // Si la nouvelle position est en dehors du plateau, passer à la direction suivante
            }

            // il faut que le newPos est libre ou la piece qui se situe la est de l'autre joueur
            if (plateau.getPiece((int)newPos.getX(), (int)newPos.getY()) == null || !plateau.getPiece((int)newPos.getX(), (int)newPos.getY()).getOwner().equals(piece.getOwner())) {
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
                if (present.getPiece(piece.getPosition().x, piece.getPosition().y) == null) {
                    coupsPossibles.add(new Coup(piece, plateau, Coup.TypeCoup.CLONE));
                }
                break;

            case PRESENT:
                if (past.getPiece(piece.getPosition().x, piece.getPosition().y) == null) {
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
    
    
}
