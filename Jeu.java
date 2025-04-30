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
                int lig, col;
                do {
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
                        //Verifie si il y a des pions dans le nouveau plateau choisi
                        switch(prochainPlateau){
                            case PAST:
                                if (joueurCourant.existePion(past)){
                                    joueurCourant.setProchainPlateau(prochainPlateau);
                                    breakloop = false;
                                }
                                else {
                                    System.out.println("Le plateau choisi ne contient pas de pion de votre couleur. Réessayez: ");
                                }
                                break;
                            case PRESENT:
                                if (joueurCourant.existePion(present)){
                                    joueurCourant.setProchainPlateau(prochainPlateau);
                                    breakloop = false;
                                }
                                else {
                                    System.out.println("Le plateau choisi ne contient pas de pion de votre couleur. Réessayez: ");
                                }
                                break;
                            case FUTURE:
                                if (joueurCourant.existePion(future)){
                                    joueurCourant.setProchainPlateau(prochainPlateau);
                                    breakloop = false;
                                }
                                else {
                                    System.out.println("Le plateau choisi ne contient pas de pion de votre couleur. Réessayez: ");
                                }
                                break;
                        }


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

    public void appliquerCoup(Coup coup) {
        // A COMPLETER
        System.err.println("Le coup a bien été appliqué.");

        Plateau plateau = coup.getPltCourant();
        Point src;
        Point dir;
        switch (coup.getTypeCoup()) {
            case MOVE:

                src = pieceCourante.getPosition();
                dir = coup.getDirection();
                int ligDes = src.x + dir.x;
                int colDes = src.y + dir.y;
                Piece newPiecePlace = plateau.getPiece(ligDes,colDes);
                if (newPiecePlace == null) { //cas ou il n'y a pas de piece à la destination
                    plateau.setPiece(pieceCourante, ligDes, colDes);
                    pieceCourante.setPosition(new Point(ligDes, colDes));
                    plateau.removePiece(src.x, src.y);
                }
                else {
                    if (!plateau.estPareilPion(newPiecePlace,pieceCourante)) {
                        plateau.setPiece(pieceCourante, ligDes, colDes);
                        pieceCourante.setPosition(new Point(ligDes, colDes));
                        if ((ligDes + dir.x) >= 0 && (ligDes + dir.x) < plateau.getSize() && (colDes + dir.y) >= 0 && (colDes + dir.y) < plateau.getSize()) {
                            Piece lastPiece = plateau.getPiece(ligDes + dir.x, colDes + dir.y);
                            if (lastPiece == null) {
                                plateau.setPiece(newPiecePlace, (ligDes + dir.x), (colDes + dir.y));
                                newPiecePlace.setPosition(new Point((ligDes + dir.x), (colDes + dir.y)));
                            } else {
                                if (plateau.estPareilPion(newPiecePlace, lastPiece)) {
                                    plateau.removePiece(ligDes + dir.x, colDes + dir.y);
                                    if (joueurCourant.equals(joueur1)) {
                                        plateau.decNoirs();
                                        plateau.decNoirs();
                                    } else if (joueurCourant.equals(joueur2)) {
                                        plateau.decBlancs();
                                        plateau.decBlancs();
                                    }
                                } else {
                                    plateau.setPiece(newPiecePlace, (ligDes + dir.x), (colDes + dir.y));
                                    newPiecePlace.setPosition(new Point((ligDes + dir.x), (colDes + dir.y)));
                                    if (joueurCourant.equals(joueur1)) {
                                        plateau.decBlancs();
                                    } else if (joueurCourant.equals(joueur2)) {
                                        plateau.decNoirs();
                                    }
                                }
                            }

                        } else {
                            if (joueurCourant.equals(joueur1)) {
                                plateau.decNoirs();
                            } else if (joueurCourant.equals(joueur2)) {
                                plateau.decBlancs();
                            }
                        }
                        plateau.removePiece(src.x, src.y);
                    }
                    if (plateau.estPareilPion(newPiecePlace,pieceCourante)) {
                        plateau.removePiece(ligDes, colDes);
                        plateau.removePiece(src.x, src.y);
                        if (joueurCourant.equals(joueur1)) {
                            plateau.decBlancs();
                            plateau.decBlancs();
                        } else if (joueurCourant.equals(joueur2)) {
                            plateau.decNoirs();
                            plateau.decNoirs();
                        }
                    }
                }



                // AJOUTER LE PUSH (A VERIFIER COMMENT DEC nbBlancs et nbNoirs
                break;
            case CLONE:
                src = pieceCourante.getPosition();
                Piece clone = new Piece(joueurCourant, src);
                //decremente le nb de pion dans l'inventaire
                boolean sortPion = joueurCourant.declone();
                switch (plateau.getType()) {
                    case PRESENT:
                        joueurCourant.setProchainPlateau(Plateau.TypePlateau.PAST);
                        if (past.paradoxe(past.getPiece(src.x,src.y),present.getPiece(src.x,src.y),0,0)){
                            System.out.println("Paradoxe !");
                            past.removePiece(src.x, src.y);
                            if (joueurCourant.equals(joueur1)) {
                                past.decBlancs();
                            } else if (joueurCourant.equals(joueur2)) {
                                past.decNoirs();
                            }
                            joueurCourant.setProchainPlateau(Plateau.TypePlateau.PRESENT);
                        }
                        else {
                            if (past.getPiece(src.x,src.y) != null){
                                if (joueurCourant.equals(joueur1)) {
                                    present.decNoirs();
                                } else if (joueurCourant.equals(joueur2)) {
                                    present.decBlancs();
                                }
                            }
                            past.setPiece(pieceCourante, src.x, src.y);
                            //laisse une copie ou non du pion cloné en fonction de ce qu'il reste dans l'inventaire
                            if (sortPion) {
                                present.setPiece(clone, src.x, src.y);
                            } else {
                                present.removePiece(src.x, src.y);
                                if (joueurCourant.equals(joueur1)) {
                                    present.decBlancs();
                                } else if (joueurCourant.equals(joueur2)) {
                                    present.decNoirs();
                                }
                            }
                            if (joueurCourant.equals(joueur1)) {
                                past.incBlancs();
                            } else if (joueurCourant.equals(joueur2)) {
                                past.incNoirs();
                            }
                        }

                        break;

                    case FUTURE:
                        joueurCourant.setProchainPlateau(Plateau.TypePlateau.PRESENT);
                        if (present.paradoxe(present.getPiece(src.x,src.y),future.getPiece(src.x,src.y),0,0)){
                            System.out.println("Paradoxe !");
                            present.removePiece(src.x, src.y);
                            if (joueurCourant.equals(joueur1)) {
                                present.decBlancs();
                            } else if (joueurCourant.equals(joueur2)) {
                                present.decNoirs();
                            }
                            joueurCourant.setProchainPlateau(Plateau.TypePlateau.FUTURE);
                        }
                        else {
                            if (present.getPiece(src.x,src.y) != null){
                                if (joueurCourant.equals(joueur1)) {
                                    present.decNoirs();
                                } else if (joueurCourant.equals(joueur2)) {
                                    present.decBlancs();
                                }
                            }
                            present.setPiece(pieceCourante, src.x, src.y);
                            //laisse une copie ou non du pion cloné en fonction de ce qu'il reste dans l'inventaire
                            if (sortPion) {
                                future.setPiece(clone, src.x, src.y);
                            } else {
                                future.removePiece(src.x, src.y);
                                if (joueurCourant.equals(joueur1)) {
                                    future.decBlancs();
                                } else if (joueurCourant.equals(joueur2)) {
                                    future.decNoirs();
                                }
                            }
                            if (joueurCourant.equals(joueur1)) {
                                present.incBlancs();
                            } else if (joueurCourant.equals(joueur2)) {
                                present.incNoirs();
                            }
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
                        if (present.paradoxe(present.getPiece(src.x,src.y),future.getPiece(src.x,src.y),0,0)){
                            System.out.println("Paradoxe !");
                            present.removePiece(src.x, src.y);
                            future.removePiece(src.x, src.y);
                            if (joueurCourant.equals(joueur1)) {
                                present.decBlancs();
                                future.decBlancs();
                            } else if (joueurCourant.equals(joueur2)) {
                                present.decNoirs();
                                future.decNoirs();
                            }
                        } else {
                            if (future.getPiece(src.x,src.y) != null){
                                if (joueurCourant.equals(joueur1)) {
                                    present.decNoirs();
                                } else if (joueurCourant.equals(joueur2)) {
                                    present.decBlancs();
                                }
                            }
                            future.setPiece(pieceCourante, src.x, src.y);
                            present.removePiece(src.x, src.y);
                            if (joueurCourant.equals(joueur1)) {
                                present.decBlancs();
                                future.incBlancs();
                            } else if (joueurCourant.equals(joueur2)) {
                                present.decBlancs();
                                future.incBlancs();
                            }
                            break;
                        }


                    case PAST:
                        // VERIFIER LE PRESENT POUR PARADOX
                        joueurCourant.setProchainPlateau(Plateau.TypePlateau.PRESENT);
                        if (past.paradoxe(past.getPiece(src.x,src.y),present.getPiece(src.x,src.y),0,0)){
                            System.out.println("Paradoxe !");
                            past.removePiece(src.x, src.y);
                            present.removePiece(src.x, src.y);
                            if (joueurCourant.equals(joueur1)) {
                                past.decBlancs();
                                present.decBlancs();
                            } else if (joueurCourant.equals(joueur2)) {
                                past.decNoirs();
                                present.decNoirs();
                            }
                        }
                        else {
                            if (present.getPiece(src.x,src.y) != null){
                                if (joueurCourant.equals(joueur1)) {
                                    present.decNoirs();
                                } else if (joueurCourant.equals(joueur2)) {
                                    present.decBlancs();
                                }
                            }
                            present.setPiece(pieceCourante, src.x, src.y);
                            past.setPiece(null, src.x, src.y);
                            if (joueurCourant.equals(joueur1)) {
                                past.decBlancs();
                                present.incBlancs();
                            } else if (joueurCourant.equals(joueur2)) {
                                past.decNoirs();
                                present.incNoirs();
                            }
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
    
    public int gameOver(Joueur j) {
        //System.out.println("Nombre de blancs: passe "+past.getNbBlancs()+", present "+present.getNbBlancs()+", future "+future.getNbBlancs());
        //System.out.println("Nombre de Noirs: passe "+past.getNbNoirs()+", present "+present.getNbNoirs()+", future "+future.getNbNoirs());
        if (past.getNbNoirs() > 0 && present.getNbNoirs() == 0 && future.getNbNoirs() == 0){
            return 1; //joueur 1 a gagne
        }
        if (past.getNbNoirs() == 0 && present.getNbNoirs() > 0 && future.getNbNoirs() == 0){
            return 1;
        }
        if (past.getNbNoirs() == 0 && present.getNbNoirs() == 0 && future.getNbNoirs() > 0){
            return 1;
        }
        if (past.getNbBlancs() > 0 && present.getNbBlancs() == 0 && future.getNbBlancs() == 0){
            return 2;
        }
        if (past.getNbBlancs() == 0 && present.getNbBlancs() > 0 && future.getNbBlancs() == 0){
            return 2;
        }
        if (past.getNbBlancs() == 0 && present.getNbBlancs() == 0 && future.getNbBlancs() > 0){
            return 2;
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

}   
