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
    private Joueur joueurCourant; // nombre de tours
    private Piece pieceCourante; // piece courante
    Scanner sc = new Scanner(System.in);
    private ArrayList<IAFields<Couple<Integer,Integer>,String,String,String>> historique = new ArrayList<>();
    
    public Jeu() {
        // Initialiser les joueurs
        joueur1 = new Joueur("Blanc", 1, 4, Plateau.TypePlateau.PAST);
        joueur2 = new Joueur("Noir", 2, 4, Plateau.TypePlateau.FUTURE);
        //IAminmax ia = new IAminmax(1);

        // Initialiser les plateaux
        past = new Plateau(Plateau.TypePlateau.PAST, joueur1, joueur2); 
        present = new Plateau(Plateau.TypePlateau.PRESENT, joueur1, joueur2);
        future = new Plateau(Plateau.TypePlateau.FUTURE, joueur1, joueur2);
    }

    public void demarrer() {
        // Initialiser les joueurs

        joueur1 = new Joueur("Blanc", 1, 4, Plateau.TypePlateau.PAST);
        joueur2 = new Joueur("Noir", 2, 4, Plateau.TypePlateau.FUTURE);
        //IAminmax ia = new IAminmax(1);

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
            //System.out.println("Coup meta du joueur 2:"+ia.choisitCoupIa(joueur1,joueur2,plateauCourant,past,present,future));
            try {
                int lig, col;
                do {
                    if ((joueurCourant.equals(joueur1) && plateauCourant.getNbBlancs() == 0) || (joueurCourant.equals(joueur2) && plateauCourant.getNbNoirs() == 0)){
                        break;
                    }
                    System.out.print("Veuillez entrez la piece que vous voulez deplacer (ligne colonne) : ");
                    lig = sc.nextInt();
                    col = sc.nextInt();
                    //Verifie si les coordonnees sont dans le plateau
                    if ( lig < 0 || col < 0 || lig > (plateauCourant.getSize()-1) || col > (plateauCourant.getSize()-1)){
                        System.out.println("Coordonées incorrectes.");
                        continue;
                    }
                    else{
                        pieceCourante = plateauCourant.getPiece(lig, col);
                    }

                    if (pieceCourante == null||!pieceCourante.getOwner().equals(joueurCourant) ) {
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
                    coup = joueurCourant.choisirCoup(plateauTraitant, pieceCourante, past, present, future);
                } while (estCoupValide(coup) == false);
                if (coup == null) {
                    break;
                }
                appliquerCoup(coup,joueurCourant,past,present,future);
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
            } while (i > 0 && gameOver(joueurCourant) == 0);


            // Prochain plateau
            Plateau.TypePlateau prochainPlateau;
            boolean breakloop = true;
            // A AJOUTER: IL FAUT LE JOUEUR CHOISIR UN AREA DIFFERENT QUE CELUI COURANT
            do {
                try {
                    if (gameOver(joueurCourant) != 0)
                        break;
                    System.out.print("Veuillez entrer le prochain plateau (PAST, PRESENT, FUTURE) : ");
                    String input = sc.next().toUpperCase();
                    //Verifie si on change de plateau
                    if (plateauCourant.getType() != Plateau.TypePlateau.valueOf(input)) {
                        prochainPlateau = Plateau.TypePlateau.valueOf(input);
                        joueurCourant.setProchainPlateau(prochainPlateau);
                        breakloop = false;
                    }
                    else {
                        System.out.println("Vous etes déjà sur ce plateau ! Veuillez en sélectionner un autre :");
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("Entrée invalide. Veuillez entrer PAST, PRESENT ou FUTURE : ");
                }
            } while (breakloop && gameOver(joueurCourant) == 0);

            printGamePlay();
        } while (gameOver(joueurCourant) == 0);
        if (gameOver(joueurCourant) == 2)
            System.out.println("Joueur 2 a gagné !");
        else if (gameOver(joueurCourant) == 1)
            System.out.println("Joueur 1 a gagné !");

    }

    private void deplacerPiece(Piece pieceactuelle, Plateau plateauAC, Coup coup) {
        //4 cas si la piece arrive sur une case vide, 
        //si elle arrive sur une case occupée par un pion de la meme couleur,
        // si elle arrive sur une case occupée par un pion de l'autre couleur,
        // si elle sort du plateau

        Point src = pieceactuelle.getPosition();
        Point dir = coup.getDirection();
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
                deplacerPiece(newPiecePlace,plateauAC,coup);
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

    public void clonage(Plateau passe, Plateau futur, Joueur player, Point src, Piece pieceactuelle, Piece clone, Boolean sortPion){
        if (passe.getPiece(src.x, src.y) == null){
            passe.setPiece(pieceactuelle, src.x, src.y);
            //laisse une copie ou non du pion cloné en fonction de ce qu'il reste dans l'inventaire
            if (sortPion) {
                futur.setPiece(clone, src.x, src.y);
            } else {
                futur.removePiece(src.x, src.y);
                if (player.equals(joueur1)) {
                    futur.decBlancs();
                } else if (player.equals(joueur2)) {
                    futur.decNoirs();
                }
            }
            if (player.equals(joueur1)) {
                passe.incBlancs();
            } else if (player.equals(joueur2)) {
                passe.incNoirs();
            }
        } else {
            System.out.println("Il y a quelque chose à l'endroit ou vous voulez vous déplacer");
        }
    }

    public void jumping(Plateau passe, Plateau futur, Joueur player, Point src, Piece pieceactuelle) {
        if (passe.getPiece(src.x,src.y) == null){
            futur.setPiece(pieceactuelle, src.x, src.y);
            passe.removePiece(src.x, src.y);
            if (player.equals(joueur1)) {
                passe.decBlancs();
                futur.incBlancs();
            } else if (player.equals(joueur2)) {
                passe.decBlancs();
                futur.incBlancs();
            }
        }
    }

    public void appliquerCoup(Coup coup, Joueur player, Plateau pastAC, Plateau presentAC, Plateau futureAC) {
        // A COMPLETER
        //System.err.println("Le coup a bien été appliqué.");

        Plateau plateauAC = coup.getPltCourant();
        Piece pieceactuelle = coup.getPiece();
        Point src;
        Point dir;
        switch (coup.getTypeCoup()) {
            case MOVE:

                deplacerPiece(pieceactuelle, plateauAC, coup);

                // AJOUTER LE PUSH (A VERIFIER COMMENT DEC nbBlancs et nbNoirs
                break;
            case CLONE:
                src = pieceactuelle.getPosition();
                Piece clone = new Piece(player, src);
                //decremente le nb de pion dans l'inventaire
                boolean sortPion = player.declone();
                switch (plateauAC.getType()) {
                    case PRESENT:
                        player.setProchainPlateau(Plateau.TypePlateau.PAST);
                        clonage(pastAC,presentAC,player,src,pieceactuelle,clone,sortPion);
                        break;

                    case FUTURE:
                        player.setProchainPlateau(Plateau.TypePlateau.PRESENT);
                        clonage(presentAC,futureAC,player,src,pieceactuelle,clone,sortPion);
                        break;
                    default:
                        // ON PEUT PAS ETRE DANS CET ETAT LA
                        System.err.println("Erreur: on ne peut pas clone depuis: "+plateauAC.getType());
                        return;
                }
                break;

            case JUMP:
                src = pieceactuelle.getPosition();
                switch (plateauAC.getType()) {
                    case PRESENT:
                        // VERIFIER LE PASSE POUR PARADOX
                        player.setProchainPlateau(Plateau.TypePlateau.FUTURE);
                        jumping(presentAC,futureAC,player,src,pieceactuelle);
                        break;


                    case PAST:
                        // VERIFIER LE PRESENT POUR PARADOX
                        player.setProchainPlateau(Plateau.TypePlateau.PRESENT);
                        jumping(pastAC,presentAC,player,src,pieceactuelle);
                        break;
                    default:
                        // ON PEUT PAS ETRE DANS CET ETAT LA
                        System.err.println("Erreur: on ne peut pas jump depuis: "+plateauAC.getType());
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
    
    private int gameOver(Joueur joueur) {
        //System.out.println("Nombre de blancs: passe "+past.getNbBlancs()+", present "+present.getNbBlancs()+", future "+future.getNbBlancs());
        //System.out.println("Nombre de Noirs: passe "+past.getNbNoirs()+", present "+present.getNbNoirs()+", future "+future.getNbNoirs());
        if (joueur.equals(joueur1)){
            if (past.getNbNoirs() > 0 && present.getNbNoirs() == 0 && future.getNbNoirs() == 0){
                return 1; //joueur 1 a gagne
            }
            if (past.getNbNoirs() == 0 && present.getNbNoirs() > 0 && future.getNbNoirs() == 0){
                return 1;
            }
            if (past.getNbNoirs() == 0 && present.getNbNoirs() == 0 && future.getNbNoirs() > 0){
                return 1;
            }
        } else if (joueur.equals(joueur2)){
            if (past.getNbBlancs() > 0 && present.getNbBlancs() == 0 && future.getNbBlancs() == 0){
                return 2;
            }
            if (past.getNbBlancs() == 0 && present.getNbBlancs() > 0 && future.getNbBlancs() == 0){
                return 2;
            }
            if (past.getNbBlancs() == 0 && present.getNbBlancs() == 0 && future.getNbBlancs() > 0){
                return 2;
            }
        }
        return 0;
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

    //Liste des coups possibles
    public ArrayList<Coup> getCoupPossibles(Plateau plateau, Piece piece) {
        //TO DO
        return null;
    }

}   
