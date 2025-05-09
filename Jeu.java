import java.awt.Point;
import java.util.Map;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;

public class Jeu implements Runnable {
    private BlockingQueue<Message> fileEntrante;
    private Map<Integer, BlockingQueue<String>> filesSortantes;
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
    // les methodes a completer

    public Jeu(BlockingQueue<Message> fileEntrante, Map<Integer, BlockingQueue<String>> filesSortantes) {
        this.fileEntrante = fileEntrante;
        this.filesSortantes = filesSortantes;
    }

    public void demarrer() {
        run();
    }

    private void envoyer(int id, Code code) {
        String msg = "";
        if (code == Code.ETAT)
            msg += code + "\n" + getGamePlayString();
        else
            msg += code;
        try {
            filesSortantes.get(id).put(msg);
        } catch (InterruptedException ignored) {}
    }

    private void envoyerATous(Code code) {
        String msg = "";
        if (code == Code.ETAT)
            msg += code + "\n" + getGamePlayString();
        else
            msg += code;
        for (BlockingQueue<String> q : filesSortantes.values()) {
            try {
                q.put(msg);
            } catch (InterruptedException ignored) {}
        }
    }

    public void run() {
        // Initialiser les joueurs

        joueur1 = new Joueur("Blanc", 1, 4, Plateau.TypePlateau.PAST, fileEntrante, filesSortantes);
        joueur2 = new Joueur("Noir", 2, 4, Plateau.TypePlateau.FUTURE, fileEntrante, filesSortantes);

        // Initialiser les plateaux
        past = new Plateau(Plateau.TypePlateau.PAST, joueur1, joueur2); 
        present = new Plateau(Plateau.TypePlateau.PRESENT, joueur1, joueur2);
        future = new Plateau(Plateau.TypePlateau.FUTURE, joueur1, joueur2);

        // Afficher le plateau initial
        // System.out.println("Plateau initial :");
        printGamePlay();
        envoyerATous(Code.ETAT);

        // Boucle de jeu
        Plateau plateauCourant = null;
        Plateau plateauTraitant = null;
        pieceCourante = null;
        joueurCourant = joueur2;
        do {
            if (joueurCourant.equals(joueur1)) {
                joueurCourant = joueur2;
                envoyer(joueur1.getId(), Code.ADVERSAIRE);
            } else if (joueurCourant.equals(joueur2)) {
                joueurCourant = joueur1;
                envoyer(joueur2.getId(), Code.ADVERSAIRE);
            }
            //envoyer(joueurCourant.getId(), "A ton tour !");

            // System.out.println("-------------------------------");
            // System.out.println("tour Joueur: " + joueurCourant.getId());


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
                    // System.out.print("Veuillez entrer la piece que vous voulez deplacer (ligne colonne) : ");
                    envoyer(joueurCourant.getId(), Code.ETAT);
                    envoyer(joueurCourant.getId(), Code.PIECE);
                    Message msg = fileEntrante.take();
                    int id = msg.clientId;

                    if (id != joueurCourant.getId()) {
                        //envoyer(id, "Ce n'est pas votre tour.");
                        continue;
                    }
                    String[] parts = msg.contenu.split(" ");
                    if (parts.length >= 2) {
                        lig = Integer.parseInt(parts[0]);
                        col = Integer.parseInt(parts[1]);
                    }
                    else {
                        //envoyer(id, "Format : [lig] [col]");
                        continue;
                    }
                    // System.out.println("choix du joueur : " + lig + " " + col);
                    
                    //Verifie si les coordonnees sont dans le plateau
                    if ( lig < 0 || col < 0 || lig > (plateauCourant.getSize()-1) || col > (plateauCourant.getSize()-1)){
                        // System.out.println("Coordonées incorrectes.");
                        //envoyer(id, "Coordonées incorrectes.");
                        continue;
                    }
                    else{
                        pieceCourante = plateauCourant.getPiece(lig, col);
                    }

                    if (pieceCourante == null||!pieceCourante.getOwner().equals(joueurCourant) ) {
                        // System.out.println("Piece invalide ou non possédée par le joueur courant. Veuillez réessayer : ");
                        //envoyer(id, "Piece invalide ou non possédée par le joueur courant. Veuillez réessayer : ");
                    }

                } while (pieceCourante == null|| !pieceCourante.getOwner().equals(joueurCourant));
            } catch (Exception e) {
                // System.out.println("Erreur : " + e.getMessage());
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
                envoyerATous(Code.ETAT);
                i-=1;
            } while (i > 0 && gameOver() == 0);


            // Prochain plateau
            Plateau.TypePlateau prochainPlateau;
            boolean breakloop = true;
            // A AJOUTER: IL FAUT LE JOUEUR CHOISIR UN AREA DIFFERENT QUE CELUI COURANT
            do {
                try {
                    if (gameOver() != 0)
                        break;
                    // System.out.print("Veuillez entrer le prochain plateau (PAST, PRESENT, FUTURE) : ");
                    envoyer(joueurCourant.getId(), Code.PLATEAU);
                    
                    Message msg = fileEntrante.take();
                    int id = msg.clientId;

                    if (id != joueurCourant.getId()) {
                        //envoyer(id, "Ce n'est pas votre tour.");
                        continue;
                    }

                    String input = msg.contenu;
                    //String input = sc.next().toUpperCase();
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
                                    // System.out.println("Le plateau choisi ne contient pas de pion de votre couleur. Réessayez: ");
                                }
                                break;
                            case PRESENT:
                                if (joueurCourant.existePion(present)){
                                    joueurCourant.setProchainPlateau(prochainPlateau);
                                    breakloop = false;
                                }
                                else {
                                    // System.out.println("Le plateau choisi ne contient pas de pion de votre couleur. Réessayez: ");
                                }
                                break;
                            case FUTURE:
                                if (joueurCourant.existePion(future)){
                                    joueurCourant.setProchainPlateau(prochainPlateau);
                                    breakloop = false;
                                }
                                else {
                                    // System.out.println("Le plateau choisi ne contient pas de pion de votre couleur. Réessayez: ");
                                }
                                break;
                        }


                    }
                    else {
                        // System.out.println("Vous etes déjà sur ce plateau ! Veuillez en sélectionner un autre :");
                    }
                } catch (Exception e) {
                    // System.out.println(e.getMessage());
                }
            } while (breakloop && gameOver() == 0);

            printGamePlay();
            envoyerATous(Code.ETAT);
        } while (gameOver() == 0);
        if (gameOver() == 2) {
            // System.out.println("Joueur 2 a gagné !");
            envoyer(joueur2.getId(), Code.GAGNE);
            envoyer(joueur1.getId(), Code.PERDU);
        }
        else if (gameOver() == 1) {
            // System.out.println("Joueur 1 a gagné !");
            envoyer(joueur1.getId(), Code.GAGNE);
            envoyer(joueur2.getId(), Code.PERDU);
        }
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
                break;
            case CLONE:
                src = pieceactuelle.getPosition();
                Piece clone = new Piece(player, src);
                //decremente le nb de pion dans l'inventaire
                boolean sortPion = player.declone();
                switch (plateauAC.getType()) {
                    case PRESENT:
                        player.setProchainPlateau(Plateau.TypePlateau.PAST);
                        if (pastAC.paradoxe(pastAC.getPiece(src.x,src.y),presentAC.getPiece(src.x,src.y),0,0)){
                            // System.out.println("Paradoxe !");
                            pastAC.removePiece(src.x, src.y);
                            if (player.equals(joueur1)) {
                                pastAC.decBlancs();
                            } else if (player.equals(joueur2)) {
                                pastAC.decNoirs();
                            }
                            player.setProchainPlateau(Plateau.TypePlateau.PRESENT);
                        }
                        else {
                            if (pastAC.getPiece(src.x,src.y) != null){
                                if (player.equals(joueur1)) {
                                    presentAC.decNoirs();
                                } else if (player.equals(joueur2)) {
                                    presentAC.decBlancs();
                                }
                            }
                            pastAC.setPiece(pieceactuelle, src.x, src.y);
                            //laisse une copie ou non du pion cloné en fonction de ce qu'il reste dans l'inventaire
                            if (sortPion) {
                                presentAC.setPiece(clone, src.x, src.y);
                            } else {
                                presentAC.removePiece(src.x, src.y);
                                if (player.equals(joueur1)) {
                                    presentAC.decBlancs();
                                } else if (player.equals(joueur2)) {
                                    presentAC.decNoirs();
                                }
                            }
                            if (player.equals(joueur1)) {
                                pastAC.incBlancs();
                            } else if (player.equals(joueur2)) {
                                pastAC.incNoirs();
                            }
                        }

                        break;

                    case FUTURE:
                        player.setProchainPlateau(Plateau.TypePlateau.PRESENT);
                        if (presentAC.paradoxe(presentAC.getPiece(src.x,src.y),futureAC.getPiece(src.x,src.y),0,0)){
                            // System.out.println("Paradoxe !");
                            presentAC.removePiece(src.x, src.y);
                            if (player.equals(joueur1)) {
                                presentAC.decBlancs();
                            } else if (player.equals(joueur2)) {
                                presentAC.decNoirs();
                            }
                            player.setProchainPlateau(Plateau.TypePlateau.FUTURE);
                        }
                        else {
                            if (presentAC.getPiece(src.x,src.y) != null){
                                if (player.equals(joueur1)) {
                                    presentAC.decNoirs();
                                } else if (player.equals(joueur2)) {
                                    presentAC.decBlancs();
                                }
                            }
                            presentAC.setPiece(pieceactuelle, src.x, src.y);
                            //laisse une copie ou non du pion cloné en fonction de ce qu'il reste dans l'inventaire
                            if (sortPion) {
                                futureAC.setPiece(clone, src.x, src.y);
                            } else {
                                futureAC.removePiece(src.x, src.y);
                                if (player.equals(joueur1)) {
                                    futureAC.decBlancs();
                                } else if (player.equals(joueur2)) {
                                    futureAC.decNoirs();
                                }
                            }
                            if (player.equals(joueur1)) {
                                presentAC.incBlancs();
                            } else if (player.equals(joueur2)) {
                                presentAC.incNoirs();
                            }
                        }
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
                        if (presentAC.paradoxe(presentAC.getPiece(src.x,src.y),futureAC.getPiece(src.x,src.y),0,0)){
                            // System.out.println("Paradoxe !");
                            presentAC.removePiece(src.x, src.y);
                            futureAC.removePiece(src.x, src.y);
                            if (player.equals(joueur1)) {
                                presentAC.decBlancs();
                                futureAC.decBlancs();
                            } else if (player.equals(joueur2)) {
                                presentAC.decNoirs();
                                futureAC.decNoirs();
                            }
                        } else {
                            if (futureAC.getPiece(src.x,src.y) != null){
                                if (player.equals(joueur1)) {
                                    presentAC.decNoirs();
                                } else if (player.equals(joueur2)) {
                                    presentAC.decBlancs();
                                }
                            }
                            futureAC.setPiece(pieceactuelle, src.x, src.y);
                            presentAC.removePiece(src.x, src.y);
                            if (player.equals(joueur1)) {
                                presentAC.decBlancs();
                                futureAC.incBlancs();
                            } else if (player.equals(joueur2)) {
                                presentAC.decBlancs();
                                futureAC.incBlancs();
                            }
                            break;
                        }


                    case PAST:
                        // VERIFIER LE PRESENT POUR PARADOX
                        player.setProchainPlateau(Plateau.TypePlateau.PRESENT);
                        if (pastAC.paradoxe(pastAC.getPiece(src.x,src.y),presentAC.getPiece(src.x,src.y),0,0)){
                            // System.out.println("Paradoxe !");
                            pastAC.removePiece(src.x, src.y);
                            presentAC.removePiece(src.x, src.y);
                            if (player.equals(joueur1)) {
                                pastAC.decBlancs();
                                presentAC.decBlancs();
                            } else if (player.equals(joueur2)) {
                                pastAC.decNoirs();
                                presentAC.decNoirs();
                            }
                        }
                        else {
                            if (presentAC.getPiece(src.x,src.y) != null){
                                if (player.equals(joueur1)) {
                                    presentAC.decNoirs();
                                } else if (player.equals(joueur2)) {
                                    presentAC.decBlancs();
                                }
                            }
                            presentAC.setPiece(pieceactuelle, src.x, src.y);
                            pastAC.setPiece(null, src.x, src.y);
                            if (player.equals(joueur1)) {
                                pastAC.decBlancs();
                                presentAC.incBlancs();
                            } else if (player.equals(joueur2)) {
                                pastAC.decNoirs();
                                presentAC.incNoirs();
                            }
                        }
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
    
    public int gameOver() {
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
        // System.out.println("Next Area J1 : " + joueur1.getProchainPlateau().toString());
        // System.out.println("Next Area J2 : " + joueur2.getProchainPlateau().toString());
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE*3 + 2; j++) {
                if (j < TAILLE) {
                    if (past.getPiece(i, j) != null) {
                        if (past.getPiece(i, j).getOwner().equals(joueur1)) {
                            // System.out.print("[B]");
                        } else {
                            // System.out.print("[N]");
                        }
                    } else {
                        // System.out.print("[ ]");
                    }
                }

                if (j == TAILLE || j == TAILLE * 2 + 1) {
                    // System.out.print("  ");
                }

                if (j > TAILLE && j < TAILLE * 2 + 1) {
                    Piece p = present.getPiece(i, j - TAILLE - 1);
                    if (p != null) {
                        if (p.getOwner().equals(joueur1)) {
                            // System.out.print("[B]");
                        } else {
                            // System.out.print("[N]");
                        }
                    } else {
                        // System.out.print("[ ]");
                    }
                }

                if (j > TAILLE * 2 + 1) {
                    Piece p = future.getPiece(i, j - TAILLE * 2 - 2);
                    if (p != null) {
                        if (p.getOwner().equals(joueur1)) {
                            // System.out.print("[B]");
                        } else {
                            // System.out.print("[N]");
                        }
                    } else {
                        // System.out.print("[ ]");
                    }
                }
            }
            // System.out.println();
        }
    }

    public String getGamePlayString() {
        StringBuilder sb = new StringBuilder();
        sb.append(joueur1.getProchainPlateau().toString());
        sb.append(" ");
        for(int i = 0; i < joueur1.getNbClones(); i++) {
            sb.append("*");
        }
        sb.append("\n");
        sb.append(joueur2.getProchainPlateau().toString());
        sb.append(" ");
        for(int i = 0; i < joueur2.getNbClones(); i++) {
            sb.append("*");
        }
        sb.append("\n");
    
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE; j++) {
                if (past.getPiece(i, j) != null) {
                    if (past.getPiece(i, j).getOwner().equals(joueur1)) {
                        sb.append("B");
                    } else {
                        sb.append("N");
                    }
                } else {
                    sb.append(".");
                }
            }
            sb.append("\n");
        }

        sb.append("\n");
    
                
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE; j++) {
                Piece p = present.getPiece(i, j);
                if (p != null) {
                    if (p.getOwner().equals(joueur1)) {
                        sb.append("B");
                    } else {
                        sb.append("N");
                    }
                } else {
                    sb.append(".");
                }
            }
            sb.append("\n");
        }

        sb.append("\n");
    
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE; j++) {
                Piece p = future.getPiece(i, j);
                if (p != null) {
                    if (p.getOwner().equals(joueur1)) {
                        sb.append("B");
                    } else {
                        sb.append("N");
                    }
                } else {
                    sb.append(".");
                }
            }
            sb.append("\n");
        }
        
        sb.append("\n");

        if (joueurCourant != null) {
            if (joueurCourant.equals(joueur1))
                sb.append("1");
            else
                sb.append("2");
            sb.append("\n");
        }

        if (pieceCourante != null) {
            int x = (int) pieceCourante.getPosition().getX();
            int y = (int) pieceCourante.getPosition().getY();

            sb.append(Integer.toString(x));
            sb.append("\n");
            sb.append(Integer.toString(y));
            sb.append("\n");
        }
    
        return sb.toString();
    }
    

}   
